package com.worldcup.dto;

/**
 * Rzeczywisty wynik meczu wpisywany przez admina.
 * advancingCode jest wymagane, gdy mecz pucharowy konczy sie remisem (rozstrzygniecie po karnych).
 */
public record AdminResultRequest(Integer score1, Integer score2, String advancingCode) {
}
