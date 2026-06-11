package com.worldcup.dto;

import com.worldcup.model.Match;
import com.worldcup.model.Prediction;
import com.worldcup.service.ScoringService;

/**
 * Mecz wraz z typem ZALOGOWANEGO uzytkownika.
 * Dzieki temu kazdy uzytkownik dostaje z API wylacznie wlasne wyniki.
 */
public class MatchView {

    private final Long id;
    private final String groupName;
    private final String date;
    private final String kickoffUtc;
    private final String team1Name;
    private final String team1Code;
    private final String team2Name;
    private final String team2Code;
    private final Integer score1;
    private final Integer score2;
    private final boolean played;
    private final Integer actualScore1;
    private final Integer actualScore2;
    private final Integer pointsEarned;

    public MatchView(Match m, Prediction p) {
        this.id = m.getId();
        this.groupName = m.getGroupName();
        this.date = m.getDate();
        this.kickoffUtc = m.getKickoffUtc();
        this.team1Name = m.getTeam1Name();
        this.team1Code = m.getTeam1Code();
        this.team2Name = m.getTeam2Name();
        this.team2Code = m.getTeam2Code();
        this.actualScore1 = m.getActualScore1();
        this.actualScore2 = m.getActualScore2();
        if (p != null && p.getScore1() != null && p.getScore2() != null) {
            this.score1 = p.getScore1();
            this.score2 = p.getScore2();
            this.played = true;
        } else {
            this.score1 = null;
            this.score2 = null;
            this.played = false;
        }
        if (actualScore1 != null && actualScore2 != null) {
            this.pointsEarned = this.played
                    ? ScoringService.points(score1, score2, actualScore1, actualScore2)
                    : 0;
        } else {
            this.pointsEarned = null;
        }
    }

    public Long getId() {
        return id;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getDate() {
        return date;
    }

    public String getKickoffUtc() {
        return kickoffUtc;
    }

    public String getTeam1Name() {
        return team1Name;
    }

    public String getTeam1Code() {
        return team1Code;
    }

    public String getTeam2Name() {
        return team2Name;
    }

    public String getTeam2Code() {
        return team2Code;
    }

    public Integer getScore1() {
        return score1;
    }

    public Integer getScore2() {
        return score2;
    }

    public boolean isPlayed() {
        return played;
    }

    public Integer getActualScore1() {
        return actualScore1;
    }

    public Integer getActualScore2() {
        return actualScore2;
    }

    public Integer getPointsEarned() {
        return pointsEarned;
    }
}
