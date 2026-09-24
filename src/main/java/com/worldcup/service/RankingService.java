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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

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

    /** Pozycje w rankingu ogolnym oraz w wybranych rozgrywkach - z jednego wczytania danych. */
    public record Overview(List<Tournament> tournaments, List<Standing> overall,
                           List<TournamentStanding> byTournament) {
    }

    public record TournamentStanding(Tournament tournament, List<Standing> standings) {
    }

    /** Wszystkie dane potrzebne do rankingu, wczytane raz (piec zapytan, niezaleznie od liczby rozgrywek). */
    private record Data(List<User> users, List<Match> matches, List<Prediction> predictions,
                        List<Tournament> tournaments, List<ChampionPick> picks) {
    }

    /** Punkty, statystyki i lista typujacych w danym zasiegu (klucz: username lowercase). */
    private record Scoreboard(Map<String, Integer> points, Map<String, Stats> stats, Set<String> active) {
    }

    private Data load() {
        return new Data(userRepository.findAll(), matchRepository.findAll(), predictionRepository.findAll(),
                tournamentRepository.findAll(), championPickRepository.findAll());
    }

    /**
     * Ranking ogolny (wszystkie rozgrywki lacznie) oraz osobne rankingi tych rozgrywek,
     * ktore spelniaja {@code withStandings} - wszystko z jednego wczytania danych,
     * zamiast osobnego przeliczenia z bazy dla kazdych rozgrywek.
     */
    public Overview overview(Predicate<Tournament> withStandings) {
        Data data = load();
        List<Standing> overall = rank(data, scoreboard(data, null));
        List<TournamentStanding> byTournament = new ArrayList<>();
        for (Tournament tournament : data.tournaments()) {
            if (withStandings.test(tournament)) {
                byTournament.add(new TournamentStanding(tournament,
                        rank(data, scoreboard(data, tournament.getId()))));
            }
        }
        return new Overview(data.tournaments(), overall, byTournament);
    }

    /** Ranking rozgrywek - tylko uzytkownicy, ktorzy w tych rozgrywkach cokolwiek typowali. */
    public List<Standing> leaderboard(Long tournamentId) {
        Data data = load();
        Scoreboard board = scoreboard(data, tournamentId);
        return rank(data, board).stream()
                .filter(s -> board.active().contains(s.username().toLowerCase()))
                .toList();
    }

    /**
     * Punkty za typy meczowe powiekszone o punkty za trafiony typ zwyciezcy rozgrywek
     * (klucz: username lowercase).
     */
    public Map<String, Integer> pointsByUser(Long tournamentId) {
        return scoreboard(load(), tournamentId).points();
    }

    /**
     * Liczy punkty i statystyki jednym przejsciem po typach.
     * "settled" = typy na mecze z uzupelnionym wynikiem; "hits" = typy punktujace
     * (trafiony kierunek/awans), "exact" = dokladnie trafiony wynik.
     */
    private Scoreboard scoreboard(Data data, Long tournamentId) {
        Map<Long, Match> matches = new HashMap<>();
        for (Match m : data.matches()) {
            if (tournamentId == null || tournamentId.equals(m.getTournamentId())) {
                matches.put(m.getId(), m);
            }
        }

        Map<String, Integer> points = new HashMap<>();
        Map<String, int[]> acc = new HashMap<>(); // [made, settled, hits, exact] per uzytkownik
        Set<String> active = new HashSet<>();

        for (Prediction p : data.predictions()) {
            Match m = matches.get(p.getMatchId());
            if (m == null) {
                continue; // typ z innych rozgrywek
            }
            String key = p.getUsername().toLowerCase();
            active.add(key);
            if (p.getScore1() == null || p.getScore2() == null) {
                continue;
            }
            int[] a = acc.computeIfAbsent(key, k -> new int[4]);
            a[0]++; // made
            if (m.getActualScore1() == null || m.getActualScore2() == null) {
                continue;
            }
            a[1]++; // settled
            int earned = earnedFor(m, p);
            if (earned != 0) {
                points.merge(key, earned, Integer::sum);
            }
            if (earned > 0) {
                a[2]++; // hit (punktujacy typ)
            }
            if (p.getScore1().equals(m.getActualScore1()) && p.getScore2().equals(m.getActualScore2())) {
                a[3]++; // exact
            }
        }

        Map<Long, Tournament> tournaments = new HashMap<>();
        for (Tournament t : data.tournaments()) {
            tournaments.put(t.getId(), t);
        }
        for (ChampionPick pick : data.picks()) {
            Tournament t = tournaments.get(pick.getTournamentId());
            if (t == null || (tournamentId != null && !tournamentId.equals(t.getId()))) {
                continue;
            }
            if (t.getChampionCode() != null && t.getChampionCode().equals(pick.getTeamCode())) {
                points.merge(pick.getUsername().toLowerCase(), ScoringService.CHAMPION_POINTS, Integer::sum);
            }
        }

        Map<String, Stats> stats = new HashMap<>();
        acc.forEach((user, a) -> stats.put(user, new Stats(a[0], a[1], a[2], a[3])));
        return new Scoreboard(points, stats, active);
    }

    /** Ranking z miejscami: punkty, skutecznosc, dokladne wyniki, na koncu nazwa. */
    private List<Standing> rank(Data data, Scoreboard board) {
        record Row(User user, RankKey key) {
        }

        List<Row> rows = data.users().stream()
                .map(u -> new Row(u, keyOf(u, board.points(), board.stats())))
                .sorted(Comparator.comparing(Row::key).reversed()
                        .thenComparing(r -> r.user().getUsername(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<RankKey> distinctKeys = rows.stream().map(Row::key).distinct().toList();

        return rows.stream()
                .map(r -> new Standing(
                        r.user().getUsername(),
                        r.key().points(),
                        statsFor(board.stats(), r.user()),
                        distinctKeys.indexOf(r.key()) + 1))
                .toList();
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
}
