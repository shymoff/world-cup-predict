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

import java.text.Normalizer;
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
    // Odstep miedzy zapytaniami do API, by nie przekroczyc limitow darmowego klucza (Cloudflare 429/1015)
    private static final long API_THROTTLE_MS = 1500;
    // Po tylu nieudanych probach (przy cyklu co 5 min => ok. 5h) przestajemy odpytywac API o dany mecz.
    // Tyle czasu z naddatkiem wystarcza, by API opublikowalo wynik; dalsze proby to zwykle problem z
    // nazwa druzyny lub brak meczu w API - wtedy lepiej przestac obciazac API i zglosic to w logu.
    private static final int MAX_FETCH_ATTEMPTS = 60;

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
    // Licznik nieudanych prob pobrania wyniku per mecz (w pamieci - resetuje sie po restarcie aplikacji,
    // wiec redeploy z poprawiona nazwa druzyny ponowi proby). Ogranicza obciazenie API dla meczow,
    // ktorych nie da sie dopasowac (np. rozbieznosc nazw).
    private final java.util.Map<Long, Integer> fetchAttempts = new java.util.concurrent.ConcurrentHashMap<>();

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

    /** Dla zakonczonych meczow bez wyniku probuje pobrac go z darmowego API.
     * Aby nie przekraczac limitow API (Cloudflare 429/1015), grupujemy mecze po dacie i odpytujemy
     * eventsday.php raz na dzien, a wyszukiwanie po nazwach (searchevents.php) odpalamy tylko dla
     * meczow, ktorych nie udalo sie rozstrzygnac po dacie. Miedzy zapytaniami stosujemy odstep. */
    public void fetchMissingResults() {
        Instant now = Instant.now();

        // Mecze zakonczone (po kickoff + czas trwania), bez wyniku i nie po wyczerpaniu limitu prob.
        java.util.Map<String, List<Match>> byDate = new java.util.LinkedHashMap<>();
        for (Match match : matchRepository.findAll()) {
            if (match.getActualScore1() != null) continue;
            if (fetchAttempts.getOrDefault(match.getId(), 0) >= MAX_FETCH_ATTEMPTS) continue; // poddalismy sie
            Instant kickoff = Instant.parse(match.getKickoffUtc());
            if (now.isBefore(kickoff.plus(MATCH_DURATION))) continue; // mecz jeszcze trwa
            String dateUtc = kickoff.atZone(ZoneOffset.UTC).toLocalDate().toString();
            byDate.computeIfAbsent(dateUtc, d -> new java.util.ArrayList<>()).add(match);
        }
        if (byDate.isEmpty()) return;

        // 1) Jedno zapytanie eventsday.php na dzien rozstrzyga zwykle wszystkie mecze tego dnia.
        List<Match> stillMissing = new java.util.ArrayList<>();
        for (var entry : byDate.entrySet()) {
            List<DayEvent> events = fetchEventsForDate(entry.getKey());
            for (Match match : entry.getValue()) {
                int[] result = (events == null) ? null : matchInEvents(match, events);
                if (result != null) {
                    saveResult(match, result);
                } else {
                    stillMissing.add(match);
                }
            }
        }

        // 2) Zapasowo: dla nadal brakujacych szukamy po nazwach druzyn.
        for (Match match : stillMissing) {
            int[] result = fetchResultBySearch(match);
            if (result != null) {
                saveResult(match, result);
            }
        }

        // Mecze, ktorych w tym cyklu nie udalo sie pobrac - zwieksz licznik prob i po limicie odpusc.
        for (Match match : stillMissing) {
            if (match.getActualScore1() != null) continue; // rozstrzygniety przez wyszukiwanie
            int attempts = fetchAttempts.merge(match.getId(), 1, Integer::sum);
            if (attempts == MAX_FETCH_ATTEMPTS) {
                log.warn("Po {} probach nie udalo sie pobrac wyniku {} - {}. Przerywam odpytywanie API "
                        + "(prawdopodobnie rozbieznosc nazw lub brak meczu w API; uzupelnij recznie).",
                        attempts, match.getTeam1En(), match.getTeam2En());
            }
        }
    }

    private void saveResult(Match match, int[] result) {
        match.setActualScore1(result[0]);
        match.setActualScore2(result[1]);
        matchRepository.save(match);
        log.info("Pobrano wynik {} - {}: {}:{}",
                match.getTeam1En(), match.getTeam2En(), result[0], result[1]);
    }

    /** Zwraca wynik meczu, jesli wystepuje wsrod podanych wydarzen dnia (porownanie po nazwach druzyn). */
    private int[] matchInEvents(Match match, List<DayEvent> events) {
        for (DayEvent event : events) {
            if (!isFinished(event.strStatus())) continue;
            if (event.intHomeScore() == null || event.intAwayScore() == null) continue;
            if (!sameTeams(match.getTeam1En(), match.getTeam2En(), event.strHomeTeam(), event.strAwayTeam())) continue;
            return new int[]{Integer.parseInt(event.intHomeScore()), Integer.parseInt(event.intAwayScore())};
        }
        return null;
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

    /** Pobiera wszystkie wydarzenia MS 2026 z danego dnia (jedno zapytanie eventsday.php). */
    private List<DayEvent> fetchEventsForDate(String dateUtc) {
        try {
            throttle();
            EventsDayResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/eventsday.php")
                            .queryParam("d", dateUtc)
                            .queryParam("l", WORLD_CUP_LEAGUE_ID)
                            .build())
                    .retrieve()
                    .body(EventsDayResponse.class);

            return (response == null) ? null : response.events();
        } catch (Exception e) {
            log.warn("Nie udalo sie pobrac wydarzen dnia {}: {}", dateUtc, e.getMessage());
            return null;
        }
    }

    /** Zapasowo: szuka meczu po nazwach druzyn (np. gdy eventsday.php nie zwrocil go w danym dniu).
     * searchevents.php dopasowuje po nazwie wydarzenia, a TheSportsDB nazywa niektore druzyny inaczej
     * niz my (np. "Bosnia-Herzegovina" zamiast "Bosnia and Herzegovina"), dlatego probujemy kilku
     * wariantow zapytania. Ostatecznie i tak weryfikujemy mecz przez sameTeams(). */
    private int[] fetchResultBySearch(Match match) {
        for (String query : searchQueries(match.getTeam1En(), match.getTeam2En())) {
            try {
                throttle();
                SearchEventsResponse response = restClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/searchevents.php")
                                .queryParam("e", query)
                                .build())
                        .retrieve()
                        .body(SearchEventsResponse.class);

                if (response == null || response.event() == null) {
                    continue;
                }
                for (DayEvent event : response.event()) {
                    if (!isFinished(event.strStatus())) continue;
                    if (event.intHomeScore() == null || event.intAwayScore() == null) continue;
                    if (!sameTeams(match.getTeam1En(), match.getTeam2En(), event.strHomeTeam(), event.strAwayTeam())) continue;
                    return new int[]{Integer.parseInt(event.intHomeScore()), Integer.parseInt(event.intAwayScore())};
                }
            } catch (Exception e) {
                log.warn("Nie udalo sie pobrac wyniku (search) dla {} - {}: {}",
                        match.getTeam1En(), match.getTeam2En(), e.getMessage());
            }
        }
        return null;
    }

    /** Buduje kandydujace zapytania "Home_vs_Away" dla searchevents.php, uwzgledniajac roznice
     * w pisowni nazw (TheSportsDB stosuje np. "Bosnia-Herzegovina" zamiast "Bosnia and Herzegovina"). */
    private List<String> searchQueries(String home, String away) {
        List<String> queries = new java.util.ArrayList<>();
        for (String h : nameVariants(home)) {
            for (String a : nameVariants(away)) {
                String q = (h + "_vs_" + a).replace(" ", "_");
                if (!queries.contains(q)) queries.add(q);
            }
        }
        return queries;
    }

    /** Warianty pisowni nazwy druzyny uzywane przez TheSportsDB (np. "X and Y" oraz "X-Y"). */
    private List<String> nameVariants(String name) {
        List<String> variants = new java.util.ArrayList<>();
        variants.add(name);
        if (name.contains(" and ")) {
            variants.add(name.replace(" and ", "-"));
        }
        return variants;
    }

    /** Porownuje pary nazw druzyn ignorujac wielkosc liter oraz roznice typu "and" / "-". */
    private boolean sameTeams(String home1, String away1, String home2, String away2) {
        return namesMatch(home1, home2) && namesMatch(away1, away2);
    }

    private boolean namesMatch(String a, String b) {
        return a != null && b != null && normalizeTeamName(a).equals(normalizeTeamName(b));
    }

    private String normalizeTeamName(String name) {
        String noDiacritics = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noDiacritics.toLowerCase().replace(" and ", " ").replace("-", " ").replaceAll("\\s+", " ").trim();
    }

    /** Statusy oznaczajace, ze mecz jest zakonczony i ma ostateczny wynik. */
    private static final List<String> FINISHED_STATUSES = List.of("ft", "aet", "pen", "ap", "match finished", "finished");

    private boolean isFinished(String status) {
        return status != null && FINISHED_STATUSES.contains(status.toLowerCase());
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
        List<DayEvent> events = fetchEventsForDate(FINAL_DATE);
        if (events == null) {
            return null;
        }
        for (DayEvent event : events) {
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
        return null;
    }

    /** Krotki odstep przed kazdym zapytaniem do API, by uniknac limitow (Cloudflare 429/1015). */
    private void throttle() {
        try {
            Thread.sleep(API_THROTTLE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
    private record SearchEventsResponse(List<DayEvent> event) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DayEvent(String strEvent, String strHomeTeam, String strAwayTeam, String strStatus,
                             String intHomeScore, String intAwayScore) {
    }
}
