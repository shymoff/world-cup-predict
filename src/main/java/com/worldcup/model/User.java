package com.worldcup.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Konto uzytkownika utworzone przez rejestracje.
 * Tabela "app_user" - "user" jest slowem zarezerwowanym w H2.
 */
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private int points = 0;

    /**
     * Kod ISO (flagcdn) typowanego mistrza turnieju, np. "br". Null = brak typu.
     *
     * @deprecated typy na zwyciezce trzyma teraz {@link ChampionPick} (jeden na turniej).
     *             Kolumna zostaje wylacznie dla zgodnosci z istniejaca baza.
     */
    @Deprecated
    private String championPick;

    /** Czy uzytkownik ma dostep do panelu admina. Nullowalne - null traktujemy jak false. */
    private Boolean admin;

    public User() {
    }

    public User(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    @Deprecated
    public String getChampionPick() {
        return championPick;
    }

    @Deprecated
    public void setChampionPick(String championPick) {
        this.championPick = championPick;
    }

    public boolean isAdmin() {
        return Boolean.TRUE.equals(admin);
    }

    public void setAdmin(Boolean admin) {
        this.admin = admin;
    }
}
