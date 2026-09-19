package com.worldcup.service;

import com.worldcup.model.Match;
import com.worldcup.model.Tournament;
import com.worldcup.model.User;
import com.worldcup.repository.MatchRepository;
import com.worldcup.repository.TournamentRepository;
import com.worldcup.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Utrwala to, co {@link RankingService} wylicza: rozstrzyga zwyciezcow rozgrywek
 * i zapisuje na koncie laczna sume punktow ze wszystkich turniejow.
 *
 * Wyniki meczow wpisuje administrator recznie (panel admina). Punkty NIE sa doliczane
 * przyrostowo - kazde wywolanie {@link #recompute()} liczy stan od zera, wiec poprawienie
 * zle wpisanego wyniku nie wymaga cofania niczego.
 */
@Service
public class ResultService {

    private static final Logger log = LoggerFactory.getLogger(ResultService.class);

    /** Nazwa rundy finalowej - z niej wynika zwyciezca rozgrywek. */
    public static final String FINAL_ROUND = "Finał";

    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final TournamentRepository tournamentRepository;
    private final RankingService rankingService;

    public ResultService(MatchRepository matchRepository,
                         UserRepository userRepository,
                         TournamentRepository tournamentRepository,
                         RankingService rankingService) {
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
        this.tournamentRepository = tournamentRepository;
        this.rankingService = rankingService;
    }

    /**
     * Przelicza punkty wszystkich uzytkownikow od zera. Wolane po kazdej zmianie
     * wprowadzonej przez admina oraz przy starcie aplikacji.
     */
    @Transactional
    public void recompute() {
        resolveChampions();

        Map<String, Integer> total = rankingService.pointsByUser(null);
        for (User user : userRepository.findAll()) {
            int points = total.getOrDefault(user.getUsername().toLowerCase(), 0);
            if (user.getPoints() != points) {
                log.info("Punkty {}: {} -> {}", user.getUsername(), user.getPoints(), points);
                user.setPoints(points);
                userRepository.save(user);
            }
        }
    }

    /**
     * Ustala zwyciezce kazdych rozgrywek na podstawie druzyny awansujacej z finalu.
     * Nie nadpisuje zwyciezcy ustawionego wczesniej recznie w panelu.
     */
    private void resolveChampions() {
        for (Tournament tournament : tournamentRepository.findAll()) {
            if (tournament.getChampionCode() != null) {
                continue;
            }
            String champion = matchRepository.findByTournamentIdOrderByKickoffUtcAscIdAsc(tournament.getId()).stream()
                    .filter(m -> FINAL_ROUND.equals(m.getRoundName()) && m.getAdvancingCode() != null)
                    .map(Match::getAdvancingCode)
                    .findFirst()
                    .orElse(null);
            if (champion == null) {
                continue; // final jeszcze nierozstrzygniety
            }
            tournament.setChampionCode(champion);
            tournament.setFinished(true);
            tournamentRepository.save(tournament);
            log.info("Zwyciezca rozgrywek '{}': {}", tournament.getName(), champion);
        }
    }
}
