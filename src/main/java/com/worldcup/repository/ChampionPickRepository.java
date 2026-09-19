package com.worldcup.repository;

import com.worldcup.model.ChampionPick;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChampionPickRepository extends JpaRepository<ChampionPick, Long> {

    List<ChampionPick> findByTournamentId(Long tournamentId);

    Optional<ChampionPick> findByUsernameIgnoreCaseAndTournamentId(String username, Long tournamentId);

    long countByTournamentId(Long tournamentId);
}
