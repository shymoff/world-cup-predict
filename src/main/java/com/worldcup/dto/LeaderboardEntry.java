package com.worldcup.dto;

import com.worldcup.service.RankingService;

public class LeaderboardEntry {

    private final String username;
    private final int points;

    public LeaderboardEntry(RankingService.Standing standing) {
        this.username = standing.username();
        this.points = standing.points();
    }

    public String getUsername() {
        return username;
    }

    public int getPoints() {
        return points;
    }
}
