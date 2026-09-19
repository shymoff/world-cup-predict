package com.worldcup.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Typ uzytkownika na zwyciezce calego turnieju - jeden na turniej.
 * Zastepuje pole User.championPick, ktore miescilo tylko jeden turniej;
 * stara kolumna zostaje w bazie jako nieuzywana.
 */
@Entity
public class ChampionPick {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    private Long tournamentId;

    /** Kod druzyny (Team.code) typowanej na zwyciezce. */
    private String teamCode;

    public ChampionPick() {
    }

    public ChampionPick(String username, Long tournamentId, String teamCode) {
        this.username = username;
        this.tournamentId = tournamentId;
        this.teamCode = teamCode;
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

    public Long getTournamentId() {
        return tournamentId;
    }

    public void setTournamentId(Long tournamentId) {
        this.tournamentId = tournamentId;
    }

    public String getTeamCode() {
        return teamCode;
    }

    public void setTeamCode(String teamCode) {
        this.teamCode = teamCode;
    }
}
