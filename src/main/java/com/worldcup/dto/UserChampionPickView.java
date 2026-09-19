package com.worldcup.dto;

import com.worldcup.service.ScoringService;

/**
 * Typ JEDNEGO uzytkownika na zwyciezce rozgrywek - widoczny dla innych po zablokowaniu typowania.
 */
public class UserChampionPickView {

    private final String username;
    private final String code;
    private final Integer pointsEarned;

    public UserChampionPickView(String username, String code, String actualChampion) {
        this.username = username;
        this.code = code;
        if (actualChampion == null) {
            this.pointsEarned = null;
        } else {
            this.pointsEarned = actualChampion.equals(code) ? ScoringService.CHAMPION_POINTS : 0;
        }
    }

    public String getUsername() {
        return username;
    }

    public String getCode() {
        return code;
    }

    public Integer getPointsEarned() {
        return pointsEarned;
    }
}
