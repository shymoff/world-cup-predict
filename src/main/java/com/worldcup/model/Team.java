package com.worldcup.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Druzyna biorąca udzial w danym turnieju. Zasila listy wyboru w panelu admina
 * i typowanie zwyciezcy rozgrywek.
 *
 * Reprezentacje: {@code code} to kod ISO flagcdn (np. "pl"), {@code crestUrl} puste.
 * Kluby: {@code code} to dowolny unikalny identyfikator w obrebie turnieju
 * (np. "real-madryt"), a herb bierze sie z {@code crestUrl}.
 */
@Entity
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long tournamentId;

    /** Identyfikator druzyny w obrebie turnieju - to on ląduje w Match.team1Code/team2Code. */
    private String code;

    private String name;

    /** Adres herbu - tylko dla klubow; dla reprezentacji flaga wynika z kodu ISO. */
    private String crestUrl;

    public Team() {
    }

    public Team(Long tournamentId, String code, String name, String crestUrl) {
        this.tournamentId = tournamentId;
        this.code = code;
        this.name = name;
        this.crestUrl = crestUrl;
    }

    public Long getId() {
        return id;
    }

    public Long getTournamentId() {
        return tournamentId;
    }

    public void setTournamentId(Long tournamentId) {
        this.tournamentId = tournamentId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCrestUrl() {
        return crestUrl;
    }

    public void setCrestUrl(String crestUrl) {
        this.crestUrl = crestUrl;
    }
}
