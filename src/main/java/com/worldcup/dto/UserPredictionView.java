package com.worldcup.dto;

import com.worldcup.model.Prediction;

/**
 * Typ JEDNEGO uzytkownika na konkretny mecz - widoczny dla innych po zablokowaniu meczu.
 */
public class UserPredictionView {

    private final String username;
    private final Integer score1;
    private final Integer score2;
    private final String advancingCode;

    public UserPredictionView(Prediction p) {
        this.username = p.getUsername();
        this.score1 = p.getScore1();
        this.score2 = p.getScore2();
        this.advancingCode = p.getAdvancingCode();
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
}
