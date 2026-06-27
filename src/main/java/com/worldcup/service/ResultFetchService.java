package com.worldcup.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.worldcup.model.Match;
import com.worldcup.model.Prediction;
import com.worldcup.model.TournamentState;
import com.worldcup.model.User;
import com.worldcup.repository.MatchRepository;
import com.worldcup.repository.PredictionRepository;
import com.worldcup.repository.TournamentStateRepository;
import com.worldcup.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pobiera dane MS 2026 z football-data.org (jedno zapytanie zwraca wszystkie 104 mecze).
 * Na ich podstawie:
 *  - uzupelnia wyniki meczow fazy grupowej (dopasowanie po parze druzyn),
 *  - synchronizuje mecze fazy pucharowej (1/16 - Final) wraz z druzynami, ktore wpadaja w drabinke
 *    po zakonczeniu fazy grupowej, oraz z druzyna awansujaca (rowniez po karnych - pole score.winner),
 *  - przyznaje punkty za typy i ustala mistrza turnieju.
 *
 * Dziala tylko, gdy ustawiony jest token (FOOTBALL_DATA_TOKEN). Bez tokenu metoda refresh() jest pomijana.
 */
@Service
public class ResultFetchService {

    private static final Logger log = LoggerFactory.getLogger(ResultFetchService.class);

    private static final String FINAL_ROUND = "Finał";

    // Mapowanie etapow football-data.org na nasze etykiety rund.
    private static final Map<String, String> STAGE_TO_ROUND = Map.of(
            "LAST_32", "1/16",
            "LAST_16", "1/8",
            "QUARTER_FINALS", "Ćwierćfinał",
            "SEMI_FINALS", "Półfinał",
            "THIRD_PLACE", "Mecz o 3. miejsce",
            "FINAL", FINAL_ROUND);

    private final MatchRepository matchRepository;
    private final PredictionRepository predictionRepository;
    private final UserRepository userRepository;
    private final TournamentStateRepository tournamentStateRepository;
    private final RestClient restClient;
    private final String token;
    private boolean missingTokenLogged = false;

    public ResultFetchService(MatchRepository matchRepository,
                              PredictionRepository predictionRepository,
                              UserRepository userRepository,
                              TournamentStateRepository tournamentStateRepository,
                              @Value("${app.footballdata.token:}") String token,
                              @Value("${app.footballdata.base-url:https://api.football-data.org/v4}") String baseUrl) {
        this.matchRepository = matchRepository;
        this.predictionRepository = predictionRepository;
        this.userRepository = userRepository;
        this.tournamentStateRepository = tournamentStateRepository;
        this.token = token;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /** Co 5 minut: synchronizacja meczow i wynikow, przyznanie punktow, ustalenie mistrza. */
    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 15 * 1000)
    public void refresh() {
        if (token == null || token.isBlank()) {
            if (!missingTokenLogged) {
                log.warn("Brak FOOTBALL_DATA_TOKEN - pobieranie wynikow z football-data.org wylaczone.");
                missingTokenLogged = true;
            }
            return;
        }

        List<FdMatch> matches = fetchAllMatches();
        if (matches == null) {
            return; // blad sieci/API - sprobujemy w nastepnym cyklu
        }
        syncAndUpdate(matches);
        awardPendingPoints();
        awardChampion();
    }

    /** Jedno zapytanie po wszystkie mecze MS 2026 (faza grupowa + pucharowa). */
    private List<FdMatch> fetchAllMatches() {
        try {
            MatchesResponse response = restClient.get()
                    .uri("/competitions/WC/matches")
                    .header("X-Auth-Token", token)
                    .retrieve()
                    .body(MatchesResponse.class);
            return (response == null) ? null : response.matches();
        } catch (Exception e) {
            log.warn("Nie udalo sie pobrac meczow z football-data.org: {}", e.getMessage());
            return null;
        }
    }

    /** Aktualizuje wyniki grupowe i synchronizuje mecze pucharowe na podstawie danych z API. */
    private void syncAndUpdate(List<FdMatch> matches) {
        // Indeks meczow fazy grupowej po nieuporzadkowanej parze kodow druzyn (kazda para gra raz).
        Map<String, Match> groupByPair = new HashMap<>();
        for (Match m : matchRepository.findAll()) {
            if (m.getRoundName() == null && m.getTeam1Code() != null && m.getTeam2Code() != null) {
                groupByPair.put(pairKey(m.getTeam1Code(), m.getTeam2Code()), m);
            }
        }

        for (FdMatch fd : matches) {
            String round = STAGE_TO_ROUND.get(fd.stage());
            if ("GROUP_STAGE".equals(fd.stage())) {
                updateGroupResult(fd, groupByPair);
            } else if (round != null) {
                upsertKnockout(fd, round);
            }
            // pozostale, nieobslugiwane etapy pomijamy
        }
    }

    /** Uzupelnia wynik meczu grupowego (jesli jeszcze go nie mamy), zachowujac nasza kolejnosc druzyn. */
    private void updateGroupResult(FdMatch fd, Map<String, Match> groupByPair) {
        if (!isFinished(fd) || fd.score() == null || fd.score().fullTime() == null) {
            return;
        }
        Integer home = fd.score().fullTime().home();
        Integer away = fd.score().fullTime().away();
        if (home == null || away == null) {
            return;
        }
        String homeCode = Teams.codeForFootballDataName(fd.homeTeam() == null ? null : fd.homeTeam().name());
        String awayCode = Teams.codeForFootballDataName(fd.awayTeam() == null ? null : fd.awayTeam().name());
        if (homeCode == null || awayCode == null) {
            return; // nieznana druzyna - nie ryzykujemy blednego dopasowania
        }
        Match match = groupByPair.get(pairKey(homeCode, awayCode));
        if (match == null || match.getActualScore1() != null) {
            return; // brak takiego meczu u nas lub wynik juz zapisany
        }
        // Dopasuj wynik do naszej kolejnosci druzyn (team1/team2 moze byc odwrocone wzgledem API).
        if (match.getTeam1Code().equals(homeCode)) {
            match.setActualScore1(home);
            match.setActualScore2(away);
        } else {
            match.setActualScore1(away);
            match.setActualScore2(home);
        }
        matchRepository.save(match);
        log.info("Wynik grupowy {} - {}: {}:{}", match.getTeam1Name(), match.getTeam2Name(),
                match.getActualScore1(), match.getActualScore2());
    }

    /** Tworzy lub aktualizuje mecz pucharowy (klucz: externalId). Druzyny i wynik uzupelniaja sie z czasem. */
    private void upsertKnockout(FdMatch fd, String round) {
        Match match = matchRepository.findByExternalId(fd.id()).orElseGet(Match::new);
        match.setExternalId(fd.id());
        match.setRoundName(round);
        match.setGroupName(null);

        Instant kickoff = Instant.parse(fd.utcDate());
        match.setKickoffUtc(kickoff.toString());
        match.setDate(kickoff.atZone(ZoneId.of("Europe/Warsaw")).toLocalDate().toString());

        applyTeam(match, true, fd.homeTeam());
        applyTeam(match, false, fd.awayTeam());

        if (isFinished(fd) && fd.score() != null && fd.score().fullTime() != null) {
            Integer home = fd.score().fullTime().home();
            Integer away = fd.score().fullTime().away();
            if (home != null && away != null && match.getActualScore1() == null) {
                match.setActualScore1(home);
                match.setActualScore2(away);
                String winner = fd.score().winner();
                if ("HOME_TEAM".equals(winner)) {
                    match.setAdvancingCode(match.getTeam1Code());
                } else if ("AWAY_TEAM".equals(winner)) {
                    match.setAdvancingCode(match.getTeam2Code());
                }
                log.info("Wynik pucharowy [{}] {} - {}: {}:{} (awans: {})", round,
                        match.getTeam1Name(), match.getTeam2Name(), home, away, match.getAdvancingCode());
            }
        }
        matchRepository.save(match);
    }

    /** Ustawia druzyne (gospodarz/gosc) na meczu pucharowym - dopoki nieznana, pola pozostaja puste. */
    private void applyTeam(Match match, boolean home, FdTeam team) {
        String enName = (team == null) ? null : team.name();
        String code = Teams.codeForFootballDataName(enName);
        // Polska nazwa z kodu; jesli kod nieznany, a nazwa jest - pokaz nazwe angielska awaryjnie.
        String plName = (code != null) ? Teams.CODE_TO_PL.get(code) : enName;
        if (home) {
            match.setTeam1Name(plName);
            match.setTeam1Code(code);
            match.setTeam1En(enName);
        } else {
            match.setTeam2Name(plName);
            match.setTeam2Code(code);
            match.setTeam2En(enName);
        }
    }

    /** Dla meczow z wynikiem, ale bez przyznanych punktow - liczy i dopisuje punkty uzytkownikom. */
    public void awardPendingPoints() {
        for (Match match : matchRepository.findAll()) {
            if (match.isPointsAwarded() || match.getActualScore1() == null || match.getActualScore2() == null) {
                continue;
            }
            // Mecz pucharowy zakonczony remisem: bez znanej druzyny awansujacej nie da sie poprawnie
            // przyznac punktow - czekamy, az kod awansu zostanie uzupelniony.
            boolean knockoutDraw = match.isKnockout()
                    && match.getActualScore1().equals(match.getActualScore2());
            if (knockoutDraw && match.getAdvancingCode() == null) {
                continue;
            }
            for (Prediction p : predictionRepository.findByMatchId(match.getId())) {
                if (p.getScore1() == null || p.getScore2() == null) continue;
                int pts = pointsFor(match, p);
                if (pts == 0) continue;
                userRepository.findByUsernameIgnoreCase(p.getUsername()).ifPresent(user -> {
                    user.setPoints(user.getPoints() + pts);
                    userRepository.save(user);
                });
            }
            match.setPointsAwarded(true);
            matchRepository.save(match);
        }
    }

    /** Punkty za typ - wg zasad fazy pucharowej (z uwzglednieniem awansu) lub fazy grupowej. */
    private int pointsFor(Match match, Prediction p) {
        if (match.isKnockout()) {
            String predAdvancing = predictedAdvancing(match, p);
            return ScoringService.knockoutPoints(p.getScore1(), p.getScore2(), predAdvancing,
                    match.getActualScore1(), match.getActualScore2(), match.getAdvancingCode());
        }
        return ScoringService.points(p.getScore1(), p.getScore2(),
                match.getActualScore1(), match.getActualScore2());
    }

    /** Druzyna typowana do awansu: przy wygranej wynika z wyniku, przy remisie z osobnego typu. */
    private String predictedAdvancing(Match match, Prediction p) {
        if (p.getScore1() > p.getScore2()) return match.getTeam1Code();
        if (p.getScore1() < p.getScore2()) return match.getTeam2Code();
        return p.getAdvancingCode();
    }

    /**
     * Po zakonczeniu finalu ustala mistrza (z meczu finalowego) i jednorazowo dolicza
     * {@value ScoringService#CHAMPION_POINTS} pkt uzytkownikom, ktorzy go trafili.
     */
    public void awardChampion() {
        TournamentState state = tournamentStateRepository.getOrCreate();
        if (state.isChampionPointsAwarded()) {
            return;
        }
        if (state.getChampionCode() == null) {
            String champion = matchRepository.findAll().stream()
                    .filter(m -> FINAL_ROUND.equals(m.getRoundName()) && m.getAdvancingCode() != null)
                    .map(Match::getAdvancingCode)
                    .findFirst()
                    .orElse(null);
            if (champion == null) {
                return; // final jeszcze nierozstrzygniety
            }
            state.setChampionCode(champion);
            tournamentStateRepository.save(state);
            log.info("Mistrz MS 2026 ustalony: {}", champion);
        }

        for (User user : userRepository.findAll()) {
            if (state.getChampionCode().equals(user.getChampionPick())) {
                user.setPoints(user.getPoints() + ScoringService.CHAMPION_POINTS);
                userRepository.save(user);
            }
        }
        state.setChampionPointsAwarded(true);
        tournamentStateRepository.save(state);
    }

    private boolean isFinished(FdMatch fd) {
        return "FINISHED".equals(fd.status());
    }

    /** Klucz nieuporzadkowanej pary kodow druzyn (np. "ar|au"). */
    private String pairKey(String code1, String code2) {
        return (code1.compareTo(code2) <= 0) ? code1 + "|" + code2 : code2 + "|" + code1;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MatchesResponse(List<FdMatch> matches) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FdMatch(long id, String stage, String status, String utcDate,
                           FdTeam homeTeam, FdTeam awayTeam, FdScore score) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FdTeam(Long id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FdScore(String winner, String duration, FdScoreLine fullTime) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record FdScoreLine(Integer home, Integer away) {
    }
}
