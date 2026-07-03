package com.worldcup.dto;

import java.util.List;

public class UserProfileView {

    private final String username;
    private final int points;
    private final int rank;
    private final int totalUsers;
    private final int predictionsMade;
    private final int settledPredictions;
    private final int exactHits;
    private final int hitPredictions;
    private final String championPickCode;
    private final String championPickName;
    private final Boolean championPickCorrect;
    private final List<WonTournamentView> wonTournaments;

    public UserProfileView(String username, int points, int rank, int totalUsers,
                            int predictionsMade, int settledPredictions, int exactHits, int hitPredictions,
                            String championPickCode, String championPickName, Boolean championPickCorrect,
                            List<WonTournamentView> wonTournaments) {
        this.username = username;
        this.points = points;
        this.rank = rank;
        this.totalUsers = totalUsers;
        this.predictionsMade = predictionsMade;
        this.settledPredictions = settledPredictions;
        this.exactHits = exactHits;
        this.hitPredictions = hitPredictions;
        this.championPickCode = championPickCode;
        this.championPickName = championPickName;
        this.championPickCorrect = championPickCorrect;
        this.wonTournaments = wonTournaments;
    }

    public String getUsername() {
        return username;
    }

    public int getPoints() {
        return points;
    }

    public int getRank() {
        return rank;
    }

    public int getTotalUsers() {
        return totalUsers;
    }

    public int getPredictionsMade() {
        return predictionsMade;
    }

    public int getSettledPredictions() {
        return settledPredictions;
    }

    public int getExactHits() {
        return exactHits;
    }

    public int getHitPredictions() {
        return hitPredictions;
    }

    public String getChampionPickCode() {
        return championPickCode;
    }

    public String getChampionPickName() {
        return championPickName;
    }

    public Boolean getChampionPickCorrect() {
        return championPickCorrect;
    }

    public List<WonTournamentView> getWonTournaments() {
        return wonTournaments;
    }
}
