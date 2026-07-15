package com.worldcup.dto;

/** Miejsce na podium turnieju: place 1 = wygrana (puchar), 2/3 = medal. */
public class WonTournamentView {

    private final String name;
    private final int points;
    private final int place;

    public WonTournamentView(String name, int points, int place) {
        this.name = name;
        this.points = points;
        this.place = place;
    }

    public String getName() {
        return name;
    }

    public int getPoints() {
        return points;
    }

    public int getPlace() {
        return place;
    }
}
