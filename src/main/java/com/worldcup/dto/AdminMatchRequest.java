package com.worldcup.dto;

/**
 * Cialo zadania przy zakladaniu/edycji meczu z panelu admina.
 *
 * roundName != null oznacza mecz fazy pucharowej (wtedy groupName jest ignorowane).
 * Kody druzyn moga byc puste - mecz pucharowy zaklada sie zanim drabinka sie wypelni.
 * date (dzien meczowy) jest opcjonalna; domyslnie wynika z kickoffUtc w strefie Europe/Warsaw.
 */
public record AdminMatchRequest(String groupName, String roundName, String kickoffUtc, String date,
                                String team1Code, String team2Code) {
}
