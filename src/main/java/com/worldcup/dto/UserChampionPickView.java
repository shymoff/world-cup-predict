package com.worldcup.dto;

import com.worldcup.model.User;
import com.worldcup.service.ScoringService;

/**
 * Typ JEDNEGO uzytkownika na mistrza turnieju - widoczny dla innych po zablokowaniu typowania.
 */
public class UserChampionPickView {

    private final String username;
    private final String code;
    private final Integer pointsEarned;

    public UserChampionPickView(User u, String actualChampion) {
        this.username = u.getUsername();
        this.code = u.getChampionPick();
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
