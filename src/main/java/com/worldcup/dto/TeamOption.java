package com.worldcup.dto;

import com.worldcup.model.Team;

/**
 * Pojedyncza opcja na liscie druzyn do wyboru (typ na zwyciezce rozgrywek).
 * crestUrl wypelnia sie tylko dla klubow - reprezentacje maja flage z kodu ISO.
 */
public class TeamOption {

    private final String code;
    private final String name;
    private final String crestUrl;

    public TeamOption(String code, String name, String crestUrl) {
        this.code = code;
        this.name = name;
        this.crestUrl = crestUrl;
    }

    public TeamOption(Team team) {
        this(team.getCode(), team.getName(), team.getCrestUrl());
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getCrestUrl() {
        return crestUrl;
    }
}
