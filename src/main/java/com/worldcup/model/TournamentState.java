package com.worldcup.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * Pojedynczy (singleton, id=1) wiersz przechowujacy globalny stan turnieju:
 * kod ISO druzyny, ktora zostala mistrzem MS 2026, oraz informacje o tym,
 * czy punkty za trafione typy mistrza zostaly juz przyznane.
 */
@Entity
public class TournamentState {

    @Id
    private Long id = 1L;

    /** Kod ISO (flagcdn) faktycznego mistrza turnieju. Null dopoki finał sie nie zakonczy. */
    private String championCode;

    /** Czy punkty za trafiony typ mistrza zostaly juz rozdane uzytkownikom. */
    private boolean championPointsAwarded;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getChampionCode() {
        return championCode;
    }

    public void setChampionCode(String championCode) {
        this.championCode = championCode;
    }

    public boolean isChampionPointsAwarded() {
        return championPointsAwarded;
    }

    public void setChampionPointsAwarded(boolean championPointsAwarded) {
        this.championPointsAwarded = championPointsAwarded;
    }
}
