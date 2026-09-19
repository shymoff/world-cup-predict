package com.worldcup.service;

import com.worldcup.model.ChampionPick;
import com.worldcup.model.Match;
import com.worldcup.model.Team;
import com.worldcup.model.Tournament;
import com.worldcup.model.TournamentState;
import com.worldcup.model.User;
import com.worldcup.repository.ChampionPickRepository;
import com.worldcup.repository.MatchRepository;
import com.worldcup.repository.TeamRepository;
import com.worldcup.repository.TournamentRepository;
import com.worldcup.repository.TournamentStateRepository;
import com.worldcup.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Przenosi istniejace dane (mecze, typy na mistrza, stan turnieju) pod nowy model
 * wielu rozgrywek, nie ruszajac ani jednego wiersza meczu ani typu.
 *
 * Wszystkie kroki sa idempotentne - przy kolejnych startach nie robia nic.
 * Dzieki temu migracja nie wymaga recznego SQL na produkcji.
 */
@Component
@Order(2) // po DataSeeder, ktory moze dopiero zalozyc mecze na pustej bazie
public class TournamentBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(TournamentBootstrap.class);

    /** Turniej, do ktorego trafiaja wszystkie mecze sprzed podzialu na rozgrywki. */
    private static final String LEGACY_SLUG = "worldcup";
    private static final String LEGACY_NAME = "Mistrzostwa Świata 2026";

    private final TournamentRepository tournamentRepository;
    private final TeamRepository teamRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final ChampionPickRepository championPickRepository;
    private final TournamentStateRepository tournamentStateRepository;
    private final ResultService resultService;
    private final String adminUsername;

    public TournamentBootstrap(TournamentRepository tournamentRepository,
                               TeamRepository teamRepository,
                               MatchRepository matchRepository,
                               UserRepository userRepository,
                               ChampionPickRepository championPickRepository,
                               TournamentStateRepository tournamentStateRepository,
                               ResultService resultService,
                               @Value("${app.admin.username:}") String adminUsername) {
        this.tournamentRepository = tournamentRepository;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
        this.championPickRepository = championPickRepository;
        this.tournamentStateRepository = tournamentStateRepository;
        this.resultService = resultService;
        this.adminUsername = adminUsername;
    }

    @Override
    public void run(String... args) {
        Tournament legacy = ensureLegacyTournament();
        seedNationalTeams(legacy);
        assignOrphanMatches(legacy);
        migrateChampionPicks(legacy);
        promoteAdmin();

        // Punkty licza sie od zera - baza mogla zostac zmieniona recznie miedzy startami.
        resultService.recompute();
    }

    /** Zaklada turniej dla istniejacego mundialu, przejmujac mistrza z dotychczasowego TournamentState. */
    private Tournament ensureLegacyTournament() {
        return tournamentRepository.findBySlug(LEGACY_SLUG).orElseGet(() -> {
            Tournament t = new Tournament(LEGACY_SLUG, LEGACY_NAME, Tournament.TEAMS_NATIONAL, true);
            TournamentState state = tournamentStateRepository.getOrCreate();
            t.setChampionCode(state.getChampionCode());
            t.setFinished(state.getChampionCode() != null);
            tournamentRepository.save(t);
            log.info("Utworzono turniej '{}' (slug: {})", LEGACY_NAME, LEGACY_SLUG);
            return t;
        });
    }

    /** Wypelnia liste druzyn mundialu ze slownika Teams - tylko gdy turniej nie ma jeszcze zadnej. */
    private void seedNationalTeams(Tournament tournament) {
        if (teamRepository.countByTournamentId(tournament.getId()) > 0) {
            return;
        }
        List<Team> teams = new ArrayList<>();
        Teams.CODES.forEach((name, code) -> teams.add(new Team(tournament.getId(), code, name, null)));
        teamRepository.saveAll(teams);
        log.info("Dodano {} druzyn do turnieju '{}'", teams.size(), tournament.getName());
    }

    /** Przypisuje mecze bez turnieju do mundialu. Wiersze zostaja te same - zmienia sie jedna kolumna. */
    private void assignOrphanMatches(Tournament tournament) {
        List<Match> orphans = matchRepository.findByTournamentIdIsNull();
        if (orphans.isEmpty()) {
            return;
        }
        orphans.forEach(m -> m.setTournamentId(tournament.getId()));
        matchRepository.saveAll(orphans);
        log.info("Przypisano {} meczow do turnieju '{}'", orphans.size(), tournament.getName());
    }

    /** Przenosi typy na mistrza z User.championPick do tabeli champion_pick (raz, przy pierwszym starcie). */
    @SuppressWarnings("deprecation")
    private void migrateChampionPicks(Tournament tournament) {
        if (championPickRepository.countByTournamentId(tournament.getId()) > 0) {
            return;
        }
        List<ChampionPick> picks = new ArrayList<>();
        for (User user : userRepository.findAll()) {
            if (user.getChampionPick() != null) {
                picks.add(new ChampionPick(user.getUsername(), tournament.getId(), user.getChampionPick()));
            }
        }
        if (picks.isEmpty()) {
            return;
        }
        championPickRepository.saveAll(picks);
        log.info("Przeniesiono {} typow na mistrza do turnieju '{}'", picks.size(), tournament.getName());
    }

    /**
     * Nadaje uprawnienia administratora koncie wskazanemu przez APP_ADMIN_USERNAME.
     * Bez tego nie da sie zrobic pierwszego admina - rejestracja tworzy zwyklych uzytkownikow.
     */
    private void promoteAdmin() {
        if (adminUsername == null || adminUsername.isBlank()) {
            return;
        }
        userRepository.findByUsernameIgnoreCase(adminUsername.trim()).ifPresentOrElse(user -> {
            if (!user.isAdmin()) {
                user.setAdmin(true);
                userRepository.save(user);
                log.info("Nadano uprawnienia administratora: {}", user.getUsername());
            }
        }, () -> log.warn("APP_ADMIN_USERNAME wskazuje na nieistniejace konto '{}' - zarejestruj je i zrestartuj.",
                adminUsername));
    }
}
