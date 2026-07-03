package com.worldcup.dto;

public class WonTournamentView {

    private final String name;
    private final int points;

    public WonTournamentView(String name, int points) {
        this.name = name;
        this.points = points;
    }

    public String getName() {
        return name;
    }

    public int getPoints() {
        return points;
    }
}
