package com.worldcup.dto;

/**
 * Cialo zadania przy zakladaniu/edycji turnieju.
 * Przy edycji slug jest ignorowany - stanowi trwaly klucz rozgrywek.
 */
public record TournamentRequest(String slug, String name, String teamKind,
                                Boolean championEnabled, String championCode, Boolean finished) {
}
