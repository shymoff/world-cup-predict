package com.worldcup.service;

/**
 * Wspolna logika punktacji typow: 3 pkt za dokladny wynik, 1 pkt za trafiony
 * wynik meczu (zwyciestwo gospodarzy / gosci / remis), 0 pkt w pozostalych przypadkach.
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
}
