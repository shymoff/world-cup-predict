package com.worldcup.service;

/**
 * Wspolna logika punktacji typow.
 *
 * Faza grupowa: 3 pkt za dokladny wynik, 1 pkt za trafiony wynik meczu
 * (zwyciestwo gospodarzy / gosci / remis), 0 pkt w pozostalych przypadkach.
 *
 * Faza pucharowa (liczy sie tez druzyna, ktora awansuje - po ew. karnych):
 *   4 pkt - dokladny wynik (co oznacza rowniez prawidlowy awans),
 *   3 pkt - dokladny remis, ale zly typ druzyny awansujacej,
 *   2 pkt - niedokladny wynik, ale dobry typ druzyny awansujacej,
 *   1 pkt - niedokladny remis i zly typ druzyny awansujacej,
 *   0 pkt - pozostale przypadki.
 */
public final class ScoringService {

    private ScoringService() {
    }

    /** Punkty za trafiony typ mistrza turnieju. */
    public static final int CHAMPION_POINTS = 15;

    public static int points(int predScore1, int predScore2, int actualScore1, int actualScore2) {
        if (predScore1 == actualScore1 && predScore2 == actualScore2) {
            return 3;
        }
        int predDiff = Integer.signum(predScore1 - predScore2);
        int actualDiff = Integer.signum(actualScore1 - actualScore2);
        return predDiff == actualDiff ? 1 : 0;
    }

    /**
     * Punktacja meczu fazy pucharowej. predAdvancing / actualAdvancing to kody ISO druzyn,
     * ktore wg typujacego / faktycznie awansowaly do nastepnej fazy (przy remisie - po karnych).
     */
    public static int knockoutPoints(int predScore1, int predScore2, String predAdvancing,
                                     int actualScore1, int actualScore2, String actualAdvancing) {
        boolean exact = predScore1 == actualScore1 && predScore2 == actualScore2;
        boolean advancingCorrect = predAdvancing != null && predAdvancing.equals(actualAdvancing);

        if (exact && advancingCorrect) {
            return 4;
        }
        if (exact) {
            return 3; // dokladny (remisowy) wynik, ale zly typ awansu
        }
        if (advancingCorrect) {
            return 2; // niedokladny wynik, ale dobry typ awansu
        }
        if (predScore1 == predScore2 && actualScore1 == actualScore2) {
            return 1; // niedokladny remis i zly typ awansu
        }
        return 0;
    }
}
