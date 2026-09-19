package com.worldcup.repository;

import com.worldcup.model.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    Optional<Tournament> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
