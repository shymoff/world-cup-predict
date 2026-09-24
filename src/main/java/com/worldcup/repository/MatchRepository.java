package com.worldcup.repository;

import com.worldcup.model.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {

    // Mecze posortowane chronologicznie wg momentu startu (ISO UTC sortuje sie poprawnie alfabetycznie)
    List<Match> findAllByOrderByKickoffUtcAscIdAsc();

    // Pierwszy mecz turnieju (z pominieciem meczu testowego "TEST") - wyznacza moment blokady typu na mistrza
    Optional<Match> findFirstByGroupNameNotOrderByKickoffUtcAsc(String groupName);

    // Mecze danej rozgrywki, chronologicznie
    List<Match> findByTournamentIdOrderByKickoffUtcAscIdAsc(Long tournamentId);

    // Mecze sprzed podzialu na rozgrywki - do jednorazowego przypisania przy starcie
    List<Match> findByTournamentIdIsNull();

    long countByTournamentId(Long tournamentId);

    /** Liczba meczow i meczow z wpisanym wynikiem dla wszystkich rozgrywek naraz (jedno zapytanie). */
    @Query("select m.tournamentId as tournamentId, count(m) as total, count(m.actualScore1) as played "
            + "from Match m group by m.tournamentId")
    List<TournamentMatchCount> countByTournament();

    interface TournamentMatchCount {
        Long getTournamentId();

        long getTotal();

        long getPlayed();
    }
}
