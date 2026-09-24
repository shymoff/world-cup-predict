package com.worldcup.controller;

import com.worldcup.dto.ChampionRequest;
import com.worldcup.dto.ChampionView;
import com.worldcup.dto.LeaderboardEntry;
import com.worldcup.dto.MatchView;
import com.worldcup.dto.ResultRequest;
import com.worldcup.dto.TeamOption;
import com.worldcup.dto.TournamentSummary;
import com.worldcup.dto.UserChampionPickView;
import com.worldcup.dto.UserPredictionView;
import com.worldcup.model.ChampionPick;
import com.worldcup.model.Match;
import com.worldcup.model.Prediction;
import com.worldcup.model.Team;
import com.worldcup.model.Tournament;
import com.worldcup.repository.ChampionPickRepository;
import com.worldcup.repository.MatchRepository;
import com.worldcup.repository.PredictionRepository;
import com.worldcup.repository.TeamRepository;
import com.worldcup.repository.TournamentRepository;
import com.worldcup.service.JwtService;
import com.worldcup.service.RankingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * Rozgrywki uzywane, gdy zadanie nie poda parametru "tournament".
     * Dzieki temu starsze linki i zakladki nadal trafiaja na mundial.
     */
    private static final String DEFAULT_SLUG = "worldcup";

    private final MatchRepository matchRepository;
    private final PredictionRepository predictionRepository;
    private final TournamentRepository tournamentRepository;
    private final TeamRepository teamRepository;
    private final ChampionPickRepository championPickRepository;
    private final JwtService jwtService;
    private final RankingService rankingService;

    public MatchController(MatchRepository matchRepository,
                          PredictionRepository predictionRepository,
                          TournamentRepository tournamentRepository,
                          TeamRepository teamRepository,
                          ChampionPickRepository championPickRepository,
                          JwtService jwtService,
                          RankingService rankingService) {
        this.matchRepository = matchRepository;
        this.predictionRepository = predictionRepository;
        this.tournamentRepository = tournamentRepository;
        this.teamRepository = teamRepository;
        this.championPickRepository = championPickRepository;
        this.jwtService = jwtService;
        this.rankingService = rankingService;
    }

    /** Lista rozgrywek dla huba - widoczna dla kazdego zalogowanego. */
    @GetMapping("/tournaments")
    public List<TournamentSummary> getTournaments(
            @RequestHeader(value = "Authorization", required = false) String auth) {
        requireUser(auth);
        Map<Long, MatchRepository.TournamentMatchCount> counts = new HashMap<>();
        for (MatchRepository.TournamentMatchCount c : matchRepository.countByTournament()) {
            counts.put(c.getTournamentId(), c);
        }
        return tournamentRepository.findAll().stream()
                .map(t -> {
                    MatchRepository.TournamentMatchCount c = counts.get(t.getId());
                    return new TournamentSummary(t, c == null ? 0 : c.getTotal(), c == null ? 0 : c.getPlayed());
                })
                .sorted(Comparator.comparing(TournamentSummary::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Mecze rozgrywek wraz z typami ZALOGOWANEGO uzytkownika (nigdy cudzymi). */
    @GetMapping("/matches")
    public List<MatchView> getMatches(@RequestHeader(value = "Authorization", required = false) String auth,
                                      @RequestParam(value = "tournament", required = false) String slug) {
        String username = requireUser(auth);
        Tournament tournament = tournament(slug);

        Map<Long, Prediction> mine = new HashMap<>();
        for (Prediction p : predictionRepository.findByUsername(username)) {
            mine.put(p.getMatchId(), p);
        }

        Map<String, String> crests = crestsByCode(tournament.getId());
        return matchRepository.findByTournamentIdOrderByKickoffUtcAscIdAsc(tournament.getId()).stream()
                .map(m -> new MatchView(m, mine.get(m.getId()), crests))
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
                .map(p -> new UserPredictionView(match, p))
                .sorted(Comparator.comparing(UserPredictionView::getUsername, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return ResponseEntity.ok(predictions);
    }

    /**
     * Ranking rozgrywek - tylko uzytkownicy, ktorzy w tych rozgrywkach cokolwiek typowali.
     * Przy rownej liczbie punktow rozstrzyga skutecznosc, a dalej dokladne wyniki.
     */
    @GetMapping("/leaderboard")
    public List<LeaderboardEntry> getLeaderboard(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(value = "tournament", required = false) String slug) {
        requireUser(auth);
        Tournament tournament = tournament(slug);
        return rankingService.leaderboard(tournament.getId()).stream()
                .map(LeaderboardEntry::new)
                .toList();
    }

    /** Stan typu na zwyciezce rozgrywek (lista druzyn, wlasny typ, blokada, faktyczny zwyciezca). */
    @GetMapping("/champion")
    public ChampionView getChampion(@RequestHeader(value = "Authorization", required = false) String auth,
                                    @RequestParam(value = "tournament", required = false) String slug) {
        String username = requireUser(auth);
        Tournament tournament = tournament(slug);

        List<TeamOption> teams = teamRepository.findByTournamentIdOrderByNameAsc(tournament.getId()).stream()
                .map(TeamOption::new)
                .toList();

        String pick = championPickRepository
                .findByUsernameIgnoreCaseAndTournamentId(username, tournament.getId())
                .map(ChampionPick::getTeamCode)
                .orElse(null);

        return new ChampionView(teams, pick, isChampionPickLocked(tournament), tournament.getChampionCode());
    }

    /** Zapis lub wyczyszczenie WLASNEGO typu na zwyciezce rozgrywek (przed ich startem). */
    @PutMapping("/champion")
    public ResponseEntity<ChampionView> updateChampion(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(value = "tournament", required = false) String slug,
            @RequestBody ChampionRequest request) {

        String username = requireUser(auth);
        Tournament tournament = tournament(slug);

        if (!tournament.isChampionEnabled() || isChampionPickLocked(tournament)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String code = request.getCode();
        if (code != null && teamRepository.findByTournamentIdAndCode(tournament.getId(), code).isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        ChampionPick pick = championPickRepository
                .findByUsernameIgnoreCaseAndTournamentId(username, tournament.getId())
                .orElseGet(() -> new ChampionPick(username, tournament.getId(), null));

        if (code == null) {
            if (pick.getId() != null) {
                championPickRepository.delete(pick);
            }
        } else {
            pick.setTeamCode(code);
            championPickRepository.save(pick);
        }

        return ResponseEntity.ok(getChampion(auth, slug));
    }

    /** Typy wszystkich uzytkownikow na zwyciezce - widoczne dopiero po zablokowaniu typowania. */
    @GetMapping("/champion/all")
    public ResponseEntity<List<UserChampionPickView>> getAllChampionPicks(
            @RequestHeader(value = "Authorization", required = false) String auth,
            @RequestParam(value = "tournament", required = false) String slug) {

        requireUser(auth);
        Tournament tournament = tournament(slug);

        if (!isChampionPickLocked(tournament)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String actualChampion = tournament.getChampionCode();

        List<UserChampionPickView> picks = championPickRepository.findByTournamentId(tournament.getId()).stream()
                .filter(p -> p.getTeamCode() != null)
                .map(p -> new UserChampionPickView(p.getUsername(), p.getTeamCode(), actualChampion))
                .sorted(Comparator.comparing(UserChampionPickView::getUsername, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return ResponseEntity.ok(picks);
    }

    /** Kod druzyny -> adres herbu; puste dla reprezentacji (flaga wynika z kodu ISO). */
    private Map<String, String> crestsByCode(Long tournamentId) {
        Map<String, String> crests = new HashMap<>();
        for (Team team : teamRepository.findByTournamentIdOrderByNameAsc(tournamentId)) {
            if (team.getCrestUrl() != null) {
                crests.put(team.getCode(), team.getCrestUrl());
            }
        }
        return crests;
    }

    /** Typ na zwyciezce blokuje sie wraz z pierwszym meczem rozgrywek (pomijajac mecze testowe). */
    private boolean isChampionPickLocked(Tournament tournament) {
        return matchRepository.findByTournamentIdOrderByKickoffUtcAscIdAsc(tournament.getId()).stream()
                .filter(m -> !"TEST".equals(m.getGroupName()))
                .findFirst()
                .map(m -> !Instant.now().isBefore(Instant.parse(m.getKickoffUtc())))
                .orElse(false);
    }

    /** Rozgrywki po slugu; bez parametru - domyslne, zeby stare linki dalej dzialaly. */
    private Tournament tournament(String slug) {
        String wanted = (slug == null || slug.isBlank()) ? DEFAULT_SLUG : slug.trim();
        return tournamentRepository.findBySlug(wanted)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Nie ma rozgrywek o adresie '" + wanted + "'"));
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
