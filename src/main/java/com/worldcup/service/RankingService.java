package com.worldcup.service;

import com.worldcup.model.Match;
import com.worldcup.model.Prediction;
import com.worldcup.model.User;
import com.worldcup.repository.MatchRepository;
import com.worldcup.repository.PredictionRepository;
import com.worldcup.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wspolne statystyki typow i kolejnosc rankingu. Kolejnosc:
 * punkty malejaco, przy remisie skutecznosc malejaco, dalej liczba dokladnie
 * trafionych wynikow malejaco, na koncu nazwa uzytkownika alfabetycznie.
 * Uzywane przez tabele rankingu, statystyki i pozycje w profilu oraz podium (gablote),
 * dzieki czemu wszedzie licza sie te same wartosci.
 */
@Service
public class RankingService {

    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final PredictionRepository predictionRepository;

    public RankingService(UserRepository userRepository,
                          MatchRepository matchRepository,
                          PredictionRepository predictionRepository) {
        this.userRepository = userRepository;
        this.matchRepository = matchRepository;
        this.predictionRepository = predictionRepository;
    }

    /** Statystyki typow jednego uzytkownika. */
    public record Stats(int predictionsMade, int settled, int hits, int exact) {

        public static final Stats EMPTY = new Stats(0, 0, 0, 0);

        /** Skutecznosc w procentach (zaokraglona) - tak samo jak pokazuje profil. */
        public int hitRatePercent() {
            return settled == 0 ? 0 : Math.round(100f * hits / settled);
        }
    }

    /**
     * Klucz porownawczy pozycji rankingowej: punkty, potem skutecznosc, potem dokladne wyniki.
     * Uzytkownicy o rownym kluczu dziela miejsce (naturalna kolejnosc rosnaca - wiekszy = lepszy).
     */
    public record RankKey(int points, int hitRatePercent, int exactHits) implements Comparable<RankKey> {

        private static final Comparator<RankKey> ORDER = Comparator
                .comparingInt(RankKey::points)
                .thenComparingInt(RankKey::hitRatePercent)
                .thenComparingInt(RankKey::exactHits);

        @Override
        public int compareTo(RankKey other) {
            return ORDER.compare(this, other);
        }
    }

    /**
     * Statystyki wszystkich uzytkownikow (klucz: username lowercase).
     * "settled" = typy na mecze z uzupelnionym wynikiem; "hits" = typy punktujace
     * (trafiony kierunek/awans), "exact" = dokladnie trafiony wynik.
     */
    public Map<String, Stats> statsByUser() {
        Map<Long, Match> matchesById = new HashMap<>();
        for (Match m : matchRepository.findAll()) {
            matchesById.put(m.getId(), m);
        }

        // [made, settled, hits, exact] per uzytkownik
        Map<String, int[]> acc = new HashMap<>();
        for (Prediction p : predictionRepository.findAll()) {
            if (p.getScore1() == null || p.getScore2() == null) {
                continue;
            }
            int[] a = acc.computeIfAbsent(p.getUsername().toLowerCase(), k -> new int[4]);
            a[0]++; // made
            Match m = matchesById.get(p.getMatchId());
            if (m == null || m.getActualScore1() == null || m.getActualScore2() == null) {
                continue;
            }
            a[1]++; // settled
            boolean exact = p.getScore1().equals(m.getActualScore1())
                    && p.getScore2().equals(m.getActualScore2());
            int earned = earnedFor(m, p);
            if (earned > 0) {
                a[2]++; // hit (punktujacy typ)
            }
            if (exact) {
                a[3]++;
            }
        }

        Map<String, Stats> stats = new HashMap<>();
        acc.forEach((user, a) -> stats.put(user, new Stats(a[0], a[1], a[2], a[3])));
        return stats;
    }

    /** Wszyscy uzytkownicy w kolejnosci rankingowej. */
    public List<User> rankedUsers(Map<String, Stats> stats) {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing((User u) -> rankKey(u, stats)).reversed()
                        .thenComparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Klucz pozycji rankingowej danego uzytkownika. */
    public RankKey rankKey(User user, Map<String, Stats> stats) {
        Stats s = statsFor(stats, user);
        return new RankKey(user.getPoints(), s.hitRatePercent(), s.exact());
    }

    public static Stats statsFor(Map<String, Stats> stats, User user) {
        return stats.getOrDefault(user.getUsername().toLowerCase(), Stats.EMPTY);
    }

    /** Punkty za pojedynczy typ - wg zasad fazy pucharowej (z awansem) lub grupowej. */
    private static int earnedFor(Match m, Prediction p) {
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
}
