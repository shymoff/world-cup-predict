package com.worldcup.dto;

import com.worldcup.model.Match;
import com.worldcup.model.Prediction;
import com.worldcup.service.ScoringService;

/**
 * Typ JEDNEGO uzytkownika na konkretny mecz - widoczny dla innych po zablokowaniu meczu.
 */
public class UserPredictionView {

    private final String username;
    private final Integer score1;
    private final Integer score2;
    private final String advancingCode;
    private final Integer pointsEarned;

    public UserPredictionView(Match m, Prediction p) {
        this.username = p.getUsername();
        this.score1 = p.getScore1();
        this.score2 = p.getScore2();
        this.advancingCode = p.getAdvancingCode();
        this.pointsEarned = computePoints(m, p);
    }

    private static Integer computePoints(Match m, Prediction p) {
        Integer actualScore1 = m.getActualScore1();
        Integer actualScore2 = m.getActualScore2();
        if (actualScore1 == null || actualScore2 == null || p.getScore1() == null || p.getScore2() == null) {
            return null;
        }
        if (m.isKnockout()) {
            String predAdvancing = predictedAdvancing(m, p);
            return ScoringService.knockoutPoints(p.getScore1(), p.getScore2(), predAdvancing,
                    actualScore1, actualScore2, m.getAdvancingCode());
        }
        return ScoringService.points(p.getScore1(), p.getScore2(), actualScore1, actualScore2);
    }

    /** Druzyna typowana do awansu: przy wygranej wynika z wyniku, przy remisie z osobnego typu (karne). */
    private static String predictedAdvancing(Match m, Prediction p) {
        if (p.getScore1() > p.getScore2()) return m.getTeam1Code();
        if (p.getScore1() < p.getScore2()) return m.getTeam2Code();
        return p.getAdvancingCode();
    }

    public String getUsername() {
        return username;
    }

    public Integer getScore1() {
        return score1;
    }

    public Integer getScore2() {
        return score2;
    }

    public String getAdvancingCode() {
        return advancingCode;
    }

    public Integer getPointsEarned() {
        return pointsEarned;
    }
}
