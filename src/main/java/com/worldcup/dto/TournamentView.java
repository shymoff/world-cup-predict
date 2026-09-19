package com.worldcup.dto;

import com.worldcup.model.Tournament;

/** Turniej widziany z panelu admina i huba. */
public record TournamentView(Long id, String slug, String name, String teamKind,
                             boolean championEnabled, String championCode,
                             boolean finished, long matchCount, long teamCount) {

    public TournamentView(Tournament t, long matchCount, long teamCount) {
        this(t.getId(), t.getSlug(), t.getName(), t.getTeamKind(),
                t.isChampionEnabled(), t.getChampionCode(), t.isFinished(), matchCount, teamCount);
    }
}
