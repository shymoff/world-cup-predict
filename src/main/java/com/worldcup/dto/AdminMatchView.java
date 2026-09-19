package com.worldcup.dto;

import com.worldcup.model.Match;

/**
 * Mecz widziany z panelu admina - bez typu uzytkownika, za to z rzeczywistym wynikiem
 * i liczba oddanych typow (przydaje sie przed usunieciem meczu).
 */
public record AdminMatchView(Long id, Long tournamentId, String groupName, String roundName,
                             String date, String kickoffUtc,
                             String team1Code, String team1Name,
                             String team2Code, String team2Name,
                             Integer actualScore1, Integer actualScore2,
                             String advancingCode, long predictionCount) {

    public AdminMatchView(Match m, long predictionCount) {
        this(m.getId(), m.getTournamentId(), m.getGroupName(), m.getRoundName(),
                m.getDate(), m.getKickoffUtc(),
                m.getTeam1Code(), m.getTeam1Name(),
                m.getTeam2Code(), m.getTeam2Name(),
                m.getActualScore1(), m.getActualScore2(),
                m.getAdvancingCode(), predictionCount);
    }
}
