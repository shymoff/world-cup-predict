package com.worldcup.service;

import com.worldcup.model.ChampionPick;
import com.worldcup.model.Match;
import com.worldcup.model.Prediction;
import com.worldcup.model.Tournament;
import com.worldcup.model.User;
import com.worldcup.repository.ChampionPickRepository;
import com.worldcup.repository.MatchRepository;
import com.worldcup.repository.PredictionRepository;
import com.worldcup.repository.TournamentRepository;
import com.worldcup.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Punkty, statystyki i kolejnosc rankingu - liczone od zera z meczow i typow.
 * Nigdzie nie sa przechowywane przyrostowo, wiec poprawka wyniku przez admina
 * od razu daje spojny ranking.
 *
 * Kazda metoda przyjmuje identyfikator rozgrywek; {@code null} oznacza
 * "wszystkie rozgrywki lacznie" (tak liczy sie podsumowanie na koncie).
 *
 * Kolejnosc: punkty malejaco, przy remisie skutecznosc, dalej dokladnie trafione
 * wyniki, na koncu nazwa alfabetycznie. Uzytkownicy rowni we wszystkich kryteriach
 * dziela miejsce.
 */
@Service
public class RankingService {

    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final PredictionRepository predictionRepository;
    private final ChampionPickRepository championPickRepository;
    private final TournamentRepository tournamentRepository;

    public RankingService(UserRepository userRepository,
                          MatchRepository matchRepository,
                          PredictionRepository predictionRepository,
                          ChampionPickRepository championPickRepository,
                          TournamentRepository tournamentRepository) {
        this.userRepository = userRepository;
        this.matchRepository = matchRepository;
        this.predictionRepository = predictionRepository;
        this.championPickRepository = championPickRepository;
        this.tournamentRepository = tournamentRepository;
    }

    /** Statystyki typow jednego uzytkownika. */
    public record Stats(int predictionsMade, int settled, int hits, int exact) {

        public static final Stats EMPTY = new Stats(0, 0, 0, 0);

        /** Skutecznosc w procentach (zaokraglona) - tak samo jak pokazuje profil. */
        public int hitRatePercent() {
            return settled == 0 ? 0 : Math.round(100f * hits / settled);
        }
    }

    /** Pozycja uzytkownika w rankingu danych rozgrywek. */
    public record Standing(String username, int points, Stats stats, int place) {
    }

    /**
     * Klucz porownawczy pozycji: punkty, potem skutecznosc, potem dokladne wyniki.
     * Uzytkownicy o rownym kluczu dziela miejsce (naturalna kolejnosc rosnaca - wiekszy = lepszy).
     */
    private record RankKey(int points, int hitRatePercent, int exactHits) implements Comparable<RankKey> {

        private static final Comparator<RankKey> ORDER = Comparator
                .comparingInt(RankKey::points)
                .thenComparingInt(RankKey::hitRatePercent)
                .thenComparingInt(RankKey::exactHits);

        @Override
        public int compareTo(RankKey other) {
            return ORDER.compare(this, other);
        }
    }

    /** Ranking rozgrywek (albo wszystkich lacznie, gdy tournamentId == null), z miejscami. */
    public List<Standing> standings(Long tournamentId) {
        Map<String, Integer> points = pointsByUser(tournamentId);
        Map<String, Stats> stats = statsByUser(tournamentId);

        record Row(User user, RankKey key) {
        }

        List<Row> rows = userRepository.findAll().stream()
                .map(u -> new Row(u, keyOf(u, points, stats)))
                .sorted(Comparator.comparing(Row::key).reversed()
                        .thenComparing(r -> r.user().getUsername(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<RankKey> distinctKeys = rows.stream().map(Row::key).distinct().toList();

        return rows.stream()
                .map(r -> new Standing(
                        r.user().getUsername(),
                        r.key().points(),
                        statsFor(stats, r.user()),
                        distinctKeys.indexOf(r.key()) + 1))
                .toList();
    }

    /** Pozycja jednego uzytkownika; null, gdy konta nie ma w rankingu. */
    public Standing standingOf(String username, Long tournamentId) {
        return standings(tournamentId).stream()
                .filter(s -> s.username().equalsIgnoreCase(username))
                .findFirst()
                .orElse(null);
    }

    /**
     * Punkty za typy meczowe powiekszone o punkty za trafiony typ zwyciezcy rozgrywek
     * (klucz: username lowercase).
     */
    public Map<String, Integer> pointsByUser(Long tournamentId) {
        Map<Long, Match> matches = matchesById(tournamentId);

        Map<String, Integer> points = new HashMap<>();
        for (Prediction p : predictionRepository.findAll()) {
            if (p.getScore1() == null || p.getScore2() == null) {
                continue;
            }
            Match match = matches.get(p.getMatchId());
            if (match == null || match.getActualScore1() == null || match.getActualScore2() == null) {
                continue;
            }
            int earned = earnedFor(match, p);
            if (earned != 0) {
                points.merge(p.getUsername().toLowerCase(), earned, Integer::sum);
            }
        }

        for (Tournament tournament : tournamentsInScope(tournamentId)) {
            String champion = tournament.getChampionCode();
            if (champion == null) {
                continue;
            }
            for (ChampionPick pick : championPickRepository.findByTournamentId(tournament.getId())) {
                if (champion.equals(pick.getTeamCode())) {
                    points.merge(pick.getUsername().toLowerCase(), ScoringService.CHAMPION_POINTS, Integer::sum);
                }
            }
        }
        return points;
    }

    /**
     * Statystyki wszystkich uzytkownikow (klucz: username lowercase).
     * "settled" = typy na mecze z uzupelnionym wynikiem; "hits" = typy punktujace
     * (trafiony kierunek/awans), "exact" = dokladnie trafiony wynik.
     */
    public Map<String, Stats> statsByUser(Long tournamentId) {
        Map<Long, Match> matches = matchesById(tournamentId);

        // [made, settled, hits, exact] per uzytkownik
        Map<String, int[]> acc = new HashMap<>();
        for (Prediction p : predictionRepository.findAll()) {
            if (p.getScore1() == null || p.getScore2() == null) {
                continue;
            }
            Match m = matches.get(p.getMatchId());
            if (m == null) {
                continue; // typ z innych rozgrywek
            }
            int[] a = acc.computeIfAbsent(p.getUsername().toLowerCase(), k -> new int[4]);
            a[0]++; // made
            if (m.getActualScore1() == null || m.getActualScore2() == null) {
                continue;
            }
            a[1]++; // settled
            if (earnedFor(m, p) > 0) {
                a[2]++; // hit (punktujacy typ)
            }
            if (p.getScore1().equals(m.getActualScore1()) && p.getScore2().equals(m.getActualScore2())) {
                a[3]++; // exact
            }
        }

        Map<String, Stats> stats = new HashMap<>();
        acc.forEach((user, a) -> stats.put(user, new Stats(a[0], a[1], a[2], a[3])));
        return stats;
    }

    public static Stats statsFor(Map<String, Stats> stats, User user) {
        return stats.getOrDefault(user.getUsername().toLowerCase(), Stats.EMPTY);
    }

    /** Punkty za pojedynczy typ - wg zasad fazy pucharowej (z awansem) lub grupowej. */
    public static int earnedFor(Match m, Prediction p) {
        if (m.isKnockout()) {
            return ScoringService.knockoutPoints(p.getScore1(), p.getScore2(), predictedAdvancing(m, p),
                    m.getActualScore1(), m.getActualScore2(), m.getAdvancingCode());
        }
        return ScoringService.points(p.getScore1(), p.getScore2(),
                m.getActualScore1(), m.getActualScore2());
    }

    /** Druzyna typowana do awansu: przy wygranej z wyniku, przy remisie z osobnego typu (karne). */
    private static String predictedAdvancing(Match m, Prediction p) {
        if (!m.isKnockout()) {
            return null;
        }
        if (p.getScore1() > p.getScore2()) return m.getTeam1Code();
        if (p.getScore1() < p.getScore2()) return m.getTeam2Code();
        return p.getAdvancingCode();
    }

    private RankKey keyOf(User user, Map<String, Integer> points, Map<String, Stats> stats) {
        Stats s = statsFor(stats, user);
        return new RankKey(points.getOrDefault(user.getUsername().toLowerCase(), 0),
                s.hitRatePercent(), s.exact());
    }

    /** Mecze w zasiegu: jednych rozgrywek albo wszystkich (tournamentId == null). */
    private Map<Long, Match> matchesById(Long tournamentId) {
        List<Match> list = (tournamentId == null)
                ? matchRepository.findAll()
                : matchRepository.findByTournamentIdOrderByKickoffUtcAscIdAsc(tournamentId);
        Map<Long, Match> byId = new HashMap<>();
        for (Match m : list) {
            byId.put(m.getId(), m);
        }
        return byId;
    }

    private List<Tournament> tournamentsInScope(Long tournamentId) {
        if (tournamentId == null) {
            return tournamentRepository.findAll();
        }
        return tournamentRepository.findById(tournamentId).map(List::of).orElseGet(List::of);
    }

    /** Nazwy uzytkownikow, ktorzy w danych rozgrywkach cokolwiek typowali. */
    public Set<String> activeUsernames(Long tournamentId) {
        Map<Long, Match> matches = matchesById(tournamentId);
        Set<String> names = new HashSet<>();
        for (Prediction p : predictionRepository.findAll()) {
            if (matches.containsKey(p.getMatchId())) {
                names.add(p.getUsername().toLowerCase());
            }
        }
        return names;
    }
}
