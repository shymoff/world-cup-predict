package com.worldcup.dto;

import com.worldcup.model.Tournament;

/**
 * Rozgrywki widziane przez zwyklego uzytkownika - tyle, ile potrzebuje hub,
 * zeby wyswietlic kafelek i link do gry.
 */
public record TournamentSummary(Long id, String slug, String name, String teamKind,
                                boolean championEnabled, boolean finished,
                                long matchCount, long playedCount) {

    public TournamentSummary(Tournament t, long matchCount, long playedCount) {
        this(t.getId(), t.getSlug(), t.getName(), t.getTeamKind(),
                t.isChampionEnabled(), t.isFinished(), matchCount, playedCount);
    }
}
