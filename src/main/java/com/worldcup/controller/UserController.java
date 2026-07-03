package com.worldcup.controller;

import com.worldcup.dto.ChangePasswordRequest;
import com.worldcup.dto.UserProfileView;
import com.worldcup.dto.WonTournamentView;
import com.worldcup.model.Match;
import com.worldcup.model.Prediction;
import com.worldcup.model.TournamentState;
import com.worldcup.model.User;
import com.worldcup.repository.MatchRepository;
import com.worldcup.repository.PredictionRepository;
import com.worldcup.repository.TournamentStateRepository;
import com.worldcup.repository.UserRepository;
import com.worldcup.service.JwtService;
import com.worldcup.service.ScoringService;
import com.worldcup.service.Teams;
import com.worldcup.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private static final String TOURNAMENT_NAME = "Mistrzostwa Świata 2026";

    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final PredictionRepository predictionRepository;
    private final TournamentStateRepository tournamentStateRepository;
    private final UserService userService;
    private final JwtService jwtService;

    public UserController(UserRepository userRepository,
                           MatchRepository matchRepository,
                           PredictionRepository predictionRepository,
                           TournamentStateRepository tournamentStateRepository,
                           UserService userService,
                           JwtService jwtService) {
        this.userRepository = userRepository;
        this.matchRepository = matchRepository;
        this.predictionRepository = predictionRepository;
        this.tournamentStateRepository = tournamentStateRepository;
        this.userService = userService;
        this.jwtService = jwtService;
    }

    /** Profil zalogowanego uzytkownika: pozycja w rankingu, statystyki typow, wygrane turnieje. */
    @GetMapping("/profile")
    public UserProfileView getProfile(@RequestHeader(value = "Authorization", required = false) String auth) {
        String username = requireUser(auth);
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token niewazny"));

        List<User> ranking = userRepository.findAllByOrderByPointsDescUsernameAsc();
        int rank = 1;
        for (User u : ranking) {
            if (u.getUsername().equalsIgnoreCase(username)) {
                break;
            }
            rank++;
        }

        Map<Long, Match> matchesById = new HashMap<>();
        for (Match m : matchRepository.findAll()) {
            matchesById.put(m.getId(), m);
        }

        int predictionsMade = 0;
        int settledPredictions = 0;
        int exactHits = 0;
        int hitPredictions = 0;
        for (Prediction p : predictionRepository.findByUsername(username)) {
            if (p.getScore1() == null || p.getScore2() == null) {
                continue;
            }
            predictionsMade++;
            Match match = matchesById.get(p.getMatchId());
            if (match == null || match.getActualScore1() == null || match.getActualScore2() == null) {
                continue;
            }
            settledPredictions++;
            boolean exact = p.getScore1().equals(match.getActualScore1()) && p.getScore2().equals(match.getActualScore2());
            int earned;
            if (match.isKnockout()) {
                String predAdvancing = predictedAdvancing(match, p);
                earned = ScoringService.knockoutPoints(p.getScore1(), p.getScore2(), predAdvancing,
                        match.getActualScore1(), match.getActualScore2(), match.getAdvancingCode());
            } else {
                earned = ScoringService.points(p.getScore1(), p.getScore2(),
                        match.getActualScore1(), match.getActualScore2());
            }
            if (exact) {
                exactHits++;
            }
            if (earned > 0) {
                hitPredictions++;
            }
        }

        TournamentState state = tournamentStateRepository.getOrCreate();
        String championPickCode = user.getChampionPick();
        String championPickName = teamNameByCode(championPickCode);
        Boolean championPickCorrect = state.getChampionCode() == null || championPickCode == null
                ? null
                : championPickCode.equals(state.getChampionCode());

        List<WonTournamentView> wonTournaments = List.of();
        if (state.getChampionCode() != null && !ranking.isEmpty()) {
            int topPoints = ranking.get(0).getPoints();
            if (user.getPoints() == topPoints) {
                wonTournaments = List.of(new WonTournamentView(TOURNAMENT_NAME, user.getPoints()));
            }
        }

        return new UserProfileView(user.getUsername(), user.getPoints(), rank, ranking.size(),
                predictionsMade, settledPredictions, exactHits, hitPredictions,
                championPickCode, championPickName, championPickCorrect, wonTournaments);
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

    /** Druzyna typowana do awansu: przy wygranej wynika z wyniku, przy remisie z osobnego typu (karne). */
    private String predictedAdvancing(Match match, Prediction p) {
        if (!match.isKnockout()) {
            return null;
        }
        if (p.getScore1() > p.getScore2()) return match.getTeam1Code();
        if (p.getScore1() < p.getScore2()) return match.getTeam2Code();
        return p.getAdvancingCode();
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
