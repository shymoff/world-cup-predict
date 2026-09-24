package com.worldcup.repository;

import com.worldcup.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByTournamentIdOrderByNameAsc(Long tournamentId);

    Optional<Team> findByTournamentIdAndCode(Long tournamentId, String code);

    long countByTournamentId(Long tournamentId);

    /** Liczba druzyn we wszystkich rozgrywkach naraz (jedno zapytanie). */
    @Query("select t.tournamentId as tournamentId, count(t) as total from Team t group by t.tournamentId")
    List<TournamentTeamCount> countByTournament();

    interface TournamentTeamCount {
        Long getTournamentId();

        long getTotal();
    }
}
