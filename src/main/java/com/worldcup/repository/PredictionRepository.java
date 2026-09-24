package com.worldcup.repository;

import com.worldcup.model.Prediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {

    List<Prediction> findByUsername(String username);

    Optional<Prediction> findByUsernameAndMatchId(String username, Long matchId);

    List<Prediction> findByMatchId(Long matchId);

    long countByMatchId(Long matchId);

    /** Liczba typow na kazdy mecz rozgrywek (jedno zapytanie zamiast jednego na mecz). */
    @Query("select p.matchId as matchId, count(p) as total from Prediction p "
            + "where p.matchId in (select m.id from Match m where m.tournamentId = :tournamentId) "
            + "group by p.matchId")
    List<MatchPredictionCount> countByMatchInTournament(@Param("tournamentId") Long tournamentId);

    interface MatchPredictionCount {
        Long getMatchId();

        long getTotal();
    }
}
