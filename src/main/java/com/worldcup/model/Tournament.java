package com.worldcup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Pojedyncza rozgrywka (MS, Euro, Liga Narodow, Klubowe MS...). Mecze, typy i ranking
 * sa liczone w obrebie turnieju. Turnieje zaklada administrator z panelu.
 */
@Entity
public class Tournament {

    /** Rodzaj druzyn: reprezentacje (flaga z kodu ISO) albo kluby (herb z URL). */
    public static final String TEAMS_NATIONAL = "NATIONAL";
    public static final String TEAMS_CLUB = "CLUB";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Czlon adresu i klucz techniczny, np. "worldcup". Niezmienny po utworzeniu. */
    @Column(unique = true)
    private String slug;

    /** Nazwa wyswietlana, np. "Mistrzostwa Świata 2026". Po niej hub dobiera trofeum. */
    private String name;

    /** TEAMS_NATIONAL albo TEAMS_CLUB. */
    private String teamKind;

    /** Czy w tym turnieju typuje sie zwyciezce calych rozgrywek. */
    private Boolean championEnabled;

    /** Kod druzyny, ktora wygrala turniej. Null dopoki nierozstrzygniete. */
    private String championCode;

    /** Czy turniej jest zakonczony - dopiero wtedy hub przyznaje miejsca na podium. */
    private Boolean finished;

    public Tournament() {
    }

    public Tournament(String slug, String name, String teamKind, boolean championEnabled) {
        this.slug = slug;
        this.name = name;
        this.teamKind = teamKind;
        this.championEnabled = championEnabled;
        this.finished = false;
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTeamKind() {
        return teamKind;
    }

    public void setTeamKind(String teamKind) {
        this.teamKind = teamKind;
    }

    /** Czy druzyny to kluby (herb z URL) zamiast reprezentacji (flaga z kodu ISO). */
    public boolean isClubTeams() {
        return TEAMS_CLUB.equals(teamKind);
    }

    public boolean isChampionEnabled() {
        return Boolean.TRUE.equals(championEnabled);
    }

    public void setChampionEnabled(Boolean championEnabled) {
        this.championEnabled = championEnabled;
    }

    public String getChampionCode() {
        return championCode;
    }

    public void setChampionCode(String championCode) {
        this.championCode = championCode;
    }

    public boolean isFinished() {
        return Boolean.TRUE.equals(finished);
    }

    public void setFinished(Boolean finished) {
        this.finished = finished;
    }
}
