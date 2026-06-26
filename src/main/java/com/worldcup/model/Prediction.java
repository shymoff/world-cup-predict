package com.worldcup.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Typ (obstawiony wynik) konkretnego uzytkownika na konkretny mecz.
 * Para (username, matchId) jest unikalna - jeden typ na uzytkownika i mecz.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"username", "matchId"}))
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private Long matchId;
    private Integer score1;
    private Integer score2;
    // Faza pucharowa: kod ISO druzyny, ktora wg typujacego awansuje (istotne przy remisie - karne).
    private String advancingCode;

    public Prediction() {
    }

    public Prediction(String username, Long matchId, Integer score1, Integer score2) {
        this.username = username;
        this.matchId = matchId;
        this.score1 = score1;
        this.score2 = score2;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getMatchId() {
        return matchId;
    }

    public void setMatchId(Long matchId) {
        this.matchId = matchId;
    }

    public Integer getScore1() {
        return score1;
    }

    public void setScore1(Integer score1) {
        this.score1 = score1;
    }

    public Integer getScore2() {
        return score2;
    }

    public void setScore2(Integer score2) {
        this.score2 = score2;
    }

    public String getAdvancingCode() {
        return advancingCode;
    }

    public void setAdvancingCode(String advancingCode) {
        this.advancingCode = advancingCode;
    }
}
