package com.worldcup.dto;

import com.worldcup.model.Team;

/** Druzyna turnieju. Dla reprezentacji crestUrl jest puste - flaga wynika z kodu ISO. */
public record TeamView(Long id, String code, String name, String crestUrl) {

    public TeamView(Team t) {
        this(t.getId(), t.getCode(), t.getName(), t.getCrestUrl());
    }
}
