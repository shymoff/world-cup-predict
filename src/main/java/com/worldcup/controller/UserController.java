package com.worldcup.controller;

import com.worldcup.dto.ChangePasswordRequest;
import com.worldcup.dto.UserProfileView;
import com.worldcup.dto.WonTournamentView;
import com.worldcup.model.ChampionPick;
import com.worldcup.model.Team;
import com.worldcup.model.Tournament;
import com.worldcup.model.User;
import com.worldcup.repository.ChampionPickRepository;
import com.worldcup.repository.TeamRepository;
import com.worldcup.repository.UserRepository;
import com.worldcup.service.JwtService;
import com.worldcup.service.RankingService;
import com.worldcup.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    /** Rozgrywki, z ktorych konto pokazuje typ na zwyciezce (hub nie ma jeszcze przelacznika). */
    private static final String DEFAULT_SLUG = "worldcup";

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final ChampionPickRepository championPickRepository;
    private final UserService userService;
    private final JwtService jwtService;
    private final RankingService rankingService;

    public UserController(UserRepository userRepository,
                           TeamRepository teamRepository,
                           ChampionPickRepository championPickRepository,
                           UserService userService,
                           JwtService jwtService,
                           RankingService rankingService) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.championPickRepository = championPickRepository;
        this.userService = userService;
        this.jwtService = jwtService;
        this.rankingService = rankingService;
    }

    /**
     * Profil zalogowanego uzytkownika. Punkty, pozycja i statystyki obejmuja
     * WSZYSTKIE rozgrywki lacznie - to widok konta na hubie, nie ranking jednej gry.
     */
    @GetMapping("/profile")
    public UserProfileView getProfile(@RequestHeader(value = "Authorization", required = false) String auth) {
        String username = requireUser(auth);
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token niewazny"));

        RankingService.Overview overview = rankingService.overview(UserController::hasPodium);
        List<RankingService.Standing> overall = overview.overall();
        RankingService.Standing mine = overall.stream()
                .filter(s -> s.username().equalsIgnoreCase(username))
                .findFirst()
                .orElse(new RankingService.Standing(user.getUsername(), 0, RankingService.Stats.EMPTY, overall.size()));

        Tournament defaultTournament = overview.tournaments().stream()
                .filter(t -> DEFAULT_SLUG.equals(t.getSlug()))
                .findFirst()
                .orElse(null);
        String championPickCode = null;
        String championPickName = null;
        Boolean championPickCorrect = null;
        if (defaultTournament != null) {
            championPickCode = championPickRepository
                    .findByUsernameIgnoreCaseAndTournamentId(username, defaultTournament.getId())
                    .map(ChampionPick::getTeamCode)
                    .orElse(null);
            championPickName = teamName(defaultTournament.getId(), championPickCode);
            String actual = defaultTournament.getChampionCode();
            championPickCorrect = (actual == null || championPickCode == null)
                    ? null
                    : championPickCode.equals(actual);
        }

        RankingService.Stats stats = mine.stats();
        return new UserProfileView(user.getUsername(), mine.points(), mine.place(), overall.size(),
                stats.predictionsMade(), stats.settled(), stats.exact(), stats.hits(),
                championPickCode, championPickName, championPickCorrect, podiumOf(username, overview));
    }

    /** Podium (gablota) dowolnego uzytkownika - do podgladu z rankingu. */
    @GetMapping("/{username}/podium")
    public List<WonTournamentView> getPodium(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable String username) {
        requireUser(auth);
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie ma takiego użytkownika"));
        return podiumOf(user.getUsername(), rankingService.overview(UserController::hasPodium));
    }

    /**
     * Miejsca na podium (1-3) w kazdych ZAKONCZONYCH rozgrywkach - po jednym wpisie na turniej.
     * Uzytkownicy rowni we wszystkich kryteriach dziela miejsce (ranking gesty).
     */
    private List<WonTournamentView> podiumOf(String username, RankingService.Overview overview) {
        List<WonTournamentView> won = new ArrayList<>();
        for (RankingService.TournamentStanding entry : overview.byTournament()) {
            Tournament tournament = entry.tournament();
            RankingService.Standing standing = entry.standings().stream()
                    .filter(s -> s.username().equalsIgnoreCase(username))
                    .findFirst()
                    .orElse(null);
            if (standing == null || standing.place() > 3) {
                continue;
            }
            // Bez ani jednego typu nie ma mowy o miejscu na podium (konto zalozone po turnieju).
            if (standing.stats().predictionsMade() == 0) {
                continue;
            }
            won.add(new WonTournamentView(tournament.getName(), standing.points(), standing.place()));
        }
        return won;
    }

    /** Rozgrywki wciaz trwaja - podium jeszcze nie istnieje, wiec nie liczymy dla nich rankingu. */
    private static boolean hasPodium(Tournament tournament) {
        return tournament.isFinished() || tournament.getChampionCode() != null;
    }

    /** Zmiana wlasnego hasla - wymaga podania aktualnego hasla. */
    @PutMapping("/password")
    public ResponseEntity<?> changePassword(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody ChangePasswordRequest request) {

        String username = requireUser(auth);
        try {
            userService.changePassword(username, request.getOldPassword(), request.getNewPassword());
            return ResponseEntity.noContent().build();
        } catch (UserService.ValidationException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private String teamName(Long tournamentId, String code) {
        if (code == null) {
            return null;
        }
        return teamRepository.findByTournamentIdAndCode(tournamentId, code)
                .map(Team::getName)
                .orElse(code);
    }

    /** Wyciaga nazwe uzytkownika z naglowka Authorization: Bearer <token> lub zwraca 401. */
    private String requireUser(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Brak tokenu");
        }
        try {
            return jwtService.validateAndGetUsername(authHeader.substring(7));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token niewazny");
        }
    }
}
