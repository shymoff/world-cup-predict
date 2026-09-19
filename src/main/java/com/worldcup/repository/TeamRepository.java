package com.worldcup.repository;

import com.worldcup.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByTournamentIdOrderByNameAsc(Long tournamentId);

    Optional<Team> findByTournamentIdAndCode(Long tournamentId, String code);

    long countByTournamentId(Long tournamentId);
}
