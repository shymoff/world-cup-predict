package com.worldcup.controller;

import com.worldcup.dto.ChangePasswordRequest;
import com.worldcup.dto.UserProfileView;
import com.worldcup.dto.WonTournamentView;
import com.worldcup.model.TournamentState;
import com.worldcup.model.User;
import com.worldcup.repository.TournamentStateRepository;
import com.worldcup.repository.UserRepository;
import com.worldcup.service.JwtService;
import com.worldcup.service.RankingService;
import com.worldcup.service.Teams;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private static final String TOURNAMENT_NAME = "Mistrzostwa Świata 2026";

    private final UserRepository userRepository;
    private final TournamentStateRepository tournamentStateRepository;
    private final UserService userService;
    private final JwtService jwtService;
    private final RankingService rankingService;

    public UserController(UserRepository userRepository,
                           TournamentStateRepository tournamentStateRepository,
                           UserService userService,
                           JwtService jwtService,
                           RankingService rankingService) {
        this.userRepository = userRepository;
        this.tournamentStateRepository = tournamentStateRepository;
        this.userService = userService;
        this.jwtService = jwtService;
        this.rankingService = rankingService;
    }

    /** Profil zalogowanego uzytkownika: pozycja w rankingu, statystyki typow, wygrane turnieje. */
    @GetMapping("/profile")
    public UserProfileView getProfile(@RequestHeader(value = "Authorization", required = false) String auth) {
        String username = requireUser(auth);
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token niewazny"));

        Map<String, RankingService.Stats> statsMap = rankingService.statsByUser();
        List<User> ranking = rankingService.rankedUsers(statsMap);
        int rank = 1;
        for (User u : ranking) {
            if (u.getUsername().equalsIgnoreCase(username)) {
                break;
            }
            rank++;
        }

        RankingService.Stats stats = RankingService.statsFor(statsMap, user);

        TournamentState state = tournamentStateRepository.getOrCreate();
        String championPickCode = user.getChampionPick();
        String championPickName = teamNameByCode(championPickCode);
        Boolean championPickCorrect = state.getChampionCode() == null || championPickCode == null
                ? null
                : championPickCode.equals(state.getChampionCode());

        List<WonTournamentView> wonTournaments = podiumOf(user, ranking, statsMap, state);

        return new UserProfileView(user.getUsername(), user.getPoints(), rank, ranking.size(),
                stats.predictionsMade(), stats.settled(), stats.exact(), stats.hits(),
                championPickCode, championPickName, championPickCorrect, wonTournaments);
    }

    /** Podium (gablota) dowolnego uzytkownika - do podgladu z rankingu. */
    @GetMapping("/{username}/podium")
    public List<WonTournamentView> getPodium(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable String username) {
        requireUser(auth);
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie ma takiego użytkownika"));
        Map<String, RankingService.Stats> statsMap = rankingService.statsByUser();
        List<User> ranking = rankingService.rankedUsers(statsMap);
        TournamentState state = tournamentStateRepository.getOrCreate();
        return podiumOf(user, ranking, statsMap, state);
    }

    /**
     * Miejsce na podium (1-3) po zakonczeniu turnieju, wg punktow, a przy remisie
     * wg skutecznosci, a dalej dokladnych wynikow. Uzytkownicy rowni we wszystkich
     * kryteriach dziela miejsce (ranking gesty po unikalnych kluczach pozycji).
     */
    private List<WonTournamentView> podiumOf(User user, List<User> ranking,
                                             Map<String, RankingService.Stats> statsMap, TournamentState state) {
        if (state.getChampionCode() == null || ranking.isEmpty()) {
            return List.of();
        }
        RankingService.RankKey userKey = rankingService.rankKey(user, statsMap);
        int place = (int) ranking.stream()
                .map(u -> rankingService.rankKey(u, statsMap))
                .distinct()
                .takeWhile(k -> k.compareTo(userKey) > 0)
                .count() + 1;
        if (place > 3) {
            return List.of();
        }
        return List.of(new WonTournamentView(TOURNAMENT_NAME, user.getPoints(), place));
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

    private String teamNameByCode(String code) {
        if (code == null) {
            return null;
        }
        return Teams.CODES.entrySet().stream()
                .filter(e -> e.getValue().equals(code))
                .map(Map.Entry::getKey)
                .findFirst()
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
