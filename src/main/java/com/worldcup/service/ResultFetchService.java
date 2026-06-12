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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Pobiera rzeczywiste wyniki zakonczonych meczow z darmowego API TheSportsDB
 * (publiczny testowy klucz "3", bez rejestracji) i na ich podstawie przyznaje
 * punkty za typy: 3 pkt za dokladny wynik, 1 pkt za trafiony wynik meczu (1x2).
 */
@Service
public class ResultFetchService {

    private static final Logger log = LoggerFactory.getLogger(ResultFetchService.class);

    private static final String API_BASE = "https://www.thesportsdb.com/api/v1/json/3";
    // Mecz uznajemy za potencjalnie zakonczony ok. 2h po starcie
    private static final Duration MATCH_DURATION = Duration.ofHours(2);

    // Finał MS 2026: 19.07.2026, MetLife Stadium. Sprawdzanie wyniku rozpoczynamy
    // kilka godzin po starcie, by uwzglednic dogrywke i ewentualne karne.
    private static final String FINAL_DATE = "2026-07-19";
    private static final Instant FINAL_CHECK_FROM = Instant.parse("2026-07-19T22:00:00Z");
    private static final String WORLD_CUP_LEAGUE_ID = "4429"; // FIFA World Cup w TheSportsDB

    private final MatchRepository matchRepository;
    private final PredictionRepository predictionRepository;
    private final UserRepository userRepository;
    private final TournamentStateRepository tournamentStateRepository;
    private final RestClient restClient;

    public ResultFetchService(MatchRepository matchRepository,
                               PredictionRepository predictionRepository,
                               UserRepository userRepository,
                               TournamentStateRepository tournamentStateRepository) {
        this.matchRepository = matchRepository;
        this.predictionRepository = predictionRepository;
        this.userRepository = userRepository;
        this.tournamentStateRepository = tournamentStateRepository;
        this.restClient = RestClient.create(API_BASE);
    }

    /** Co 5 minut: pobiera wyniki zakonczonych meczow, przyznaje punkty i sprawdza mistrza turnieju. */
    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 15 * 1000)
    public void refresh() {
        fetchMissingResults();
        awardPendingPoints();
        fetchAndAwardChampion();
    }

    /** Dla zakonczonych meczow bez wyniku probuje pobrac go z darmowego API. */
    public void fetchMissingResults() {
        Instant now = Instant.now();
        for (Match match : matchRepository.findAll()) {
            if (match.getActualScore1() != null) continue;
            Instant kickoff = Instant.parse(match.getKickoffUtc());
            if (now.isBefore(kickoff.plus(MATCH_DURATION))) continue; // mecz jeszcze trwa

            int[] result = fetchResult(match);
            if (result != null) {
                match.setActualScore1(result[0]);
                match.setActualScore2(result[1]);
                matchRepository.save(match);
                log.info("Pobrano wynik {} - {}: {}:{}",
                        match.getTeam1En(), match.getTeam2En(), result[0], result[1]);
            }
        }
    }

    /** Dla meczow z wynikiem, ale bez przyznanych punktow - liczy i dopisuje punkty uzytkownikom. */
    public void awardPendingPoints() {
        for (Match match : matchRepository.findAll()) {
            if (match.isPointsAwarded() || match.getActualScore1() == null || match.getActualScore2() == null) {
                continue;
            }
            for (Prediction p : predictionRepository.findByMatchId(match.getId())) {
                if (p.getScore1() == null || p.getScore2() == null) continue;
                int pts = ScoringService.points(p.getScore1(), p.getScore2(),
                        match.getActualScore1(), match.getActualScore2());
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

    /** Szuka wyniku meczu wsrod wszystkich wydarzen MS 2026 w danym dniu (wg daty kickoffu UTC). */
    private int[] fetchResult(Match match) {
        try {
            String dateUtc = Instant.parse(match.getKickoffUtc()).atZone(ZoneOffset.UTC).toLocalDate().toString();
            EventsDayResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/eventsday.php")
                            .queryParam("d", dateUtc)
                            .queryParam("l", WORLD_CUP_LEAGUE_ID)
                            .build())
                    .retrieve()
                    .body(EventsDayResponse.class);

            if (response == null || response.events() == null) {
                return null;
            }
            for (DayEvent event : response.events()) {
                if (!hasStarted(event.strStatus())) continue;
                if (event.intHomeScore() == null || event.intAwayScore() == null) continue;
                if (!sameTeams(match.getTeam1En(), match.getTeam2En(), event.strHomeTeam(), event.strAwayTeam())) continue;
                return new int[]{Integer.parseInt(event.intHomeScore()), Integer.parseInt(event.intAwayScore())};
            }
        } catch (Exception e) {
            log.warn("Nie udalo sie pobrac wyniku dla {} - {}: {}",
                    match.getTeam1En(), match.getTeam2En(), e.getMessage());
        }
        return null;
    }

    /** Porownuje pary nazw druzyn ignorujac wielkosc liter oraz roznice typu "and" / "-". */
    private boolean sameTeams(String home1, String away1, String home2, String away2) {
        return namesMatch(home1, home2) && namesMatch(away1, away2);
    }

    private boolean namesMatch(String a, String b) {
        return a != null && b != null && normalizeTeamName(a).equals(normalizeTeamName(b));
    }

    private String normalizeTeamName(String name) {
        return name.toLowerCase().replace(" and ", " ").replace("-", " ").replaceAll("\\s+", " ").trim();
    }

    /** Status oznaczajacy, ze mecz sie rozpoczal i ma wiarygodny wynik (w toku lub zakonczony). */
    private static final List<String> NOT_STARTED_STATUSES = List.of("", "ns", "not started", "postponed", "cancelled", "tbd");

    private boolean hasStarted(String status) {
        return status != null && !NOT_STARTED_STATUSES.contains(status.toLowerCase());
    }

    /**
     * Po zakonczeniu finalu MS 2026 ustala zwyciezce z TheSportsDB i jednorazowo
     * dolicza {@value ScoringService#CHAMPION_POINTS} pkt uzytkownikom, ktorzy go trafili.
     */
    public void fetchAndAwardChampion() {
        TournamentState state = tournamentStateRepository.getOrCreate();
        if (state.isChampionPointsAwarded()) {
            return;
        }

        if (state.getChampionCode() == null) {
            if (Instant.now().isBefore(FINAL_CHECK_FROM)) {
                return; // final jeszcze sie nie zakonczyl
            }
            String championCode = fetchChampionCode();
            if (championCode == null) {
                return; // wynik finalu jeszcze niedostepny w API - sprobuj ponownie za 5 min
            }
            state.setChampionCode(championCode);
            tournamentStateRepository.save(state);
            log.info("Mistrz MS 2026 ustalony: {}", championCode);
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

    /** Szuka meczu finalowego MS 2026 w TheSportsDB i zwraca kod ISO druzyny zwycieskiej. */
    private String fetchChampionCode() {
        try {
            EventsDayResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/eventsday.php")
                            .queryParam("d", FINAL_DATE)
                            .queryParam("l", WORLD_CUP_LEAGUE_ID)
                            .build())
                    .retrieve()
                    .body(EventsDayResponse.class);

            if (response == null || response.events() == null) {
                return null;
            }
            for (DayEvent event : response.events()) {
                if (!isFinal(event.strEvent())) continue;
                if (event.intHomeScore() == null || event.intAwayScore() == null) continue;

                int home = Integer.parseInt(event.intHomeScore());
                int away = Integer.parseInt(event.intAwayScore());
                String winnerEn;
                if (home > away) {
                    winnerEn = event.strHomeTeam();
                } else if (away > home) {
                    winnerEn = event.strAwayTeam();
                } else {
                    continue; // remis bez wyniku karnych - sprobuj ponownie, gdy API zaktualizuje dane
                }
                return Teams.ENGLISH_TO_CODE.get(winnerEn);
            }
        } catch (Exception e) {
            log.warn("Nie udalo sie pobrac wyniku finalu MS 2026: {}", e.getMessage());
        }
        return null;
    }

    /** Rozpoznaje mecz finalowy (a nie polfinal / mecz o 3. miejsce) po nazwie wydarzenia. */
    private boolean isFinal(String eventName) {
        if (eventName == null) return false;
        String name = eventName.toLowerCase();
        if (!name.contains("final")) return false;
        return !name.contains("semi") && !name.contains("3rd") && !name.contains("third place");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EventsDayResponse(List<DayEvent> events) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DayEvent(String strEvent, String strHomeTeam, String strAwayTeam, String strStatus,
                             String intHomeScore, String intAwayScore) {
    }
}
