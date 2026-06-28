package com.worldcup.controller;

import com.worldcup.dto.ChampionRequest;
import com.worldcup.dto.ChampionView;
import com.worldcup.dto.LeaderboardEntry;
import com.worldcup.dto.MatchView;
import com.worldcup.dto.ResultRequest;
import com.worldcup.dto.TeamOption;
import com.worldcup.dto.UserPredictionView;
import com.worldcup.model.Match;
import com.worldcup.model.Prediction;
import com.worldcup.model.TournamentState;
import com.worldcup.model.User;
import com.worldcup.repository.MatchRepository;
import com.worldcup.repository.PredictionRepository;
import com.worldcup.repository.TournamentStateRepository;
import com.worldcup.repository.UserRepository;
import com.worldcup.service.JwtService;
import com.worldcup.service.ResultFetchService;
import com.worldcup.service.Teams;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MatchController {

    private final MatchRepository matchRepository;
    private final PredictionRepository predictionRepository;
    private final UserRepository userRepository;
    private final TournamentStateRepository tournamentStateRepository;
    private final JwtService jwtService;
    private final ResultFetchService resultFetchService;

    public MatchController(MatchRepository matchRepository,
                          PredictionRepository predictionRepository,
                          UserRepository userRepository,
                          TournamentStateRepository tournamentStateRepository,
                          JwtService jwtService,
                          ResultFetchService resultFetchService) {
        this.matchRepository = matchRepository;
        this.predictionRepository = predictionRepository;
        this.userRepository = userRepository;
        this.tournamentStateRepository = tournamentStateRepository;
        this.jwtService = jwtService;
        this.resultFetchService = resultFetchService;
    }

    /** Mecze wraz z typami ZALOGOWANEGO uzytkownika (nigdy cudzymi). */
    @GetMapping("/matches")
    public List<MatchView> getMatches(@RequestHeader(value = "Authorization", required = false) String auth) {
        String username = requireUser(auth);

        Map<Long, Prediction> mine = new HashMap<>();
        for (Prediction p : predictionRepository.findByUsername(username)) {
            mine.put(p.getMatchId(), p);
        }

        return matchRepository.findAllByOrderByKickoffUtcAscIdAsc().stream()
                .map(m -> new MatchView(m, mine.get(m.getId())))
                .toList();
    }

    /** Zapis lub wyczyszczenie WLASNEGO typu na mecz. */
    @PutMapping("/matches/{id}")
    public ResponseEntity<MatchView> updateResult(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id,
            @RequestBody ResultRequest request) {

        String username = requireUser(auth);

        Match match = matchRepository.findById(id).orElse(null);
        if (match == null) {
            return ResponseEntity.notFound().build();
        }

        // Blokada: po rozpoczeciu meczu nie mozna juz zmieniac typu
        if (Instant.now().isAfter(Instant.parse(match.getKickoffUtc()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Prediction prediction = predictionRepository
                .findByUsernameAndMatchId(username, id)
                .orElseGet(() -> new Prediction(username, id, null, null));

        boolean hasResult = request.getScore1() != null && request.getScore2() != null;
        if (hasResult) {
            int s1 = Math.max(0, request.getScore1());
            int s2 = Math.max(0, request.getScore2());
            prediction.setScore1(s1);
            prediction.setScore2(s2);
            // Faza pucharowa: przy remisie zapamietujemy typ druzyny awansujacej (karne).
            // Przy rozstrzygnieciu awans wynika z wyniku, wiec dodatkowy typ nie jest potrzebny.
            if (match.isKnockout() && s1 == s2) {
                String adv = request.getAdvancingCode();
                boolean valid = match.getTeam1Code().equals(adv) || match.getTeam2Code().equals(adv);
                prediction.setAdvancingCode(valid ? adv : null);
            } else {
                prediction.setAdvancingCode(null);
            }
            predictionRepository.save(prediction);
        } else if (prediction.getId() != null) {
            predictionRepository.delete(prediction); // czyszczenie typu
            prediction = null;
        }

        return ResponseEntity.ok(new MatchView(match, prediction));
    }

    /** Typy wszystkich uzytkownikow na dany mecz - widoczne dopiero po jego zablokowaniu (start meczu). */
    @GetMapping("/matches/{id}/predictions")
    public ResponseEntity<List<UserPredictionView>> getMatchPredictions(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @PathVariable Long id) {

        requireUser(auth);

        Match match = matchRepository.findById(id).orElse(null);
        if (match == null) {
            return ResponseEntity.notFound().build();
        }

        if (Instant.now().isBefore(Instant.parse(match.getKickoffUtc()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<UserPredictionView> predictions = predictionRepository.findByMatchId(id).stream()
                .filter(p -> p.getScore1() != null && p.getScore2() != null)
                .map(UserPredictionView::new)
                .sorted(Comparator.comparing(UserPredictionView::getUsername, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return ResponseEntity.ok(predictions);
    }

    /** Ranking wszystkich zarejestrowanych uzytkownikow (na poczatek kazdy ma 0 punktow). */
    @GetMapping("/leaderboard")
    public List<LeaderboardEntry> getLeaderboard(@RequestHeader(value = "Authorization", required = false) String auth) {
        requireUser(auth);
        return userRepository.findAllByOrderByPointsDescUsernameAsc().stream()
                .map(LeaderboardEntry::new)
                .toList();
    }

    /** Stan typu na mistrza turnieju (lista druzyn, wlasny typ, blokada, rzeczywisty mistrz). */
    @GetMapping("/champion")
    public ChampionView getChampion(@RequestHeader(value = "Authorization", required = false) String auth) {
        String username = requireUser(auth);
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token niewazny"));

        List<TeamOption> teams = Teams.CODES.entrySet().stream()
                .map(e -> new TeamOption(e.getValue(), e.getKey()))
                .sorted(Comparator.comparing(TeamOption::getName))
                .toList();

        TournamentState state = tournamentStateRepository.getOrCreate();
        return new ChampionView(teams, user.getChampionPick(), isChampionPickLocked(), state.getChampionCode());
    }

    /** Zapis lub wyczyszczenie WLASNEGO typu na mistrza turnieju (przed startem turnieju). */
    @PutMapping("/champion")
    public ResponseEntity<ChampionView> updateChampion(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestBody ChampionRequest request) {

        String username = requireUser(auth);
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token niewazny"));

        if (isChampionPickLocked()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String code = request.getCode();
        if (code != null && !Teams.CODES.containsValue(code)) {
            return ResponseEntity.badRequest().build();
        }

        user.setChampionPick(code);
        userRepository.save(user);

        return ResponseEntity.ok(getChampion(auth));
    }

    /** Typ na mistrza blokuje sie wraz z poczatkiem turnieju (pierwszy mecz fazy grupowej). */
    private boolean isChampionPickLocked() {
        return matchRepository.findFirstByGroupNameNotOrderByKickoffUtcAsc("TEST")
                .map(m -> !Instant.now().isBefore(Instant.parse(m.getKickoffUtc())))
                .orElse(false);
    }

    /** Recznie wymusza sprawdzenie wynikow zakonczonych meczow i przyznanie punktow. */
    @PostMapping("/results/refresh")
    public ResponseEntity<Void> refreshResults(@RequestHeader(value = "Authorization", required = false) String auth) {
        requireUser(auth);
        resultFetchService.refresh();
        return ResponseEntity.noContent().build();
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
