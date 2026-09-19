package com.worldcup.controller;

import com.worldcup.dto.AdminMatchRequest;
import com.worldcup.dto.AdminMatchView;
import com.worldcup.dto.AdminResultRequest;
import com.worldcup.dto.CountryOption;
import com.worldcup.dto.TeamRequest;
import com.worldcup.dto.TeamView;
import com.worldcup.dto.TournamentRequest;
import com.worldcup.dto.TournamentView;
import com.worldcup.model.Match;
import com.worldcup.model.Prediction;
import com.worldcup.model.Team;
import com.worldcup.model.Tournament;
import com.worldcup.model.User;
import com.worldcup.repository.ChampionPickRepository;
import com.worldcup.repository.MatchRepository;
import com.worldcup.repository.PredictionRepository;
import com.worldcup.repository.TeamRepository;
import com.worldcup.repository.TournamentRepository;
import com.worldcup.repository.UserRepository;
import com.worldcup.service.CountryCatalog;
import com.worldcup.service.JwtService;
import com.worldcup.service.ResultService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import java.time.ZoneId;
import java.util.List;

/**
 * Panel administratora: zakladanie rozgrywek, druzyn i meczow oraz wpisywanie wynikow.
 * Zastepuje dawna synchronizacje z football-data.org - wszystkie dane wprowadza czlowiek.
 *
 * Kazda zmiana wyniku konczy sie przeliczeniem punktow od zera
 * ({@link ResultService#recompute()}), wiec poprawki nie wymagaja cofania punktow.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    /** Strefa, wg ktorej z godziny rozpoczecia wyliczamy "dzien meczowy" grupujacy mecze na liscie. */
    private static final ZoneId MATCH_DAY_ZONE = ZoneId.of("Europe/Warsaw");

    private final TournamentRepository tournamentRepository;
    private final TeamRepository teamRepository;
    private final ChampionPickRepository championPickRepository;
    private final MatchRepository matchRepository;
    private final PredictionRepository predictionRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final ResultService resultService;

    public AdminController(TournamentRepository tournamentRepository,
                           TeamRepository teamRepository,
                           ChampionPickRepository championPickRepository,
                           MatchRepository matchRepository,
                           PredictionRepository predictionRepository,
                           UserRepository userRepository,
                           JwtService jwtService,
                           ResultService resultService) {
        this.tournamentRepository = tournamentRepository;
        this.teamRepository = teamRepository;
        this.championPickRepository = championPickRepository;
        this.matchRepository = matchRepository;
        this.predictionRepository = predictionRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.resultService = resultService;
    }

    // ---- Turnieje ----

    @GetMapping("/tournaments")
    public List<TournamentView> listTournaments(@RequestHeader(value = "Authorization", required = false) String auth) {
        requireAdmin(auth);
        return tournamentRepository.findAll().stream().map(this::toView).toList();
    }

    @PostMapping("/tournaments")
    public TournamentView createTournament(@RequestHeader(value = "Authorization", required = false) String auth,
                                           @RequestBody TournamentRequest request) {
        requireAdmin(auth);

        String slug = normalizedSlug(request.slug());
        if (tournamentRepository.existsBySlug(slug)) {
            throw badRequest("Rozgrywki o adresie '" + slug + "' już istnieją");
        }
        String name = requireText(request.name(), "Nazwa rozgrywek jest wymagana");
        String teamKind = requireTeamKind(request.teamKind());

        Tournament tournament = new Tournament(slug, name, teamKind,
                Boolean.TRUE.equals(request.championEnabled()));
        tournamentRepository.save(tournament);
        return toView(tournament);
    }

    @PutMapping("/tournaments/{id}")
    public TournamentView updateTournament(@RequestHeader(value = "Authorization", required = false) String auth,
                                           @PathVariable Long id,
                                           @RequestBody TournamentRequest request) {
        requireAdmin(auth);
        Tournament tournament = tournament(id);

        if (request.name() != null) {
            tournament.setName(requireText(request.name(), "Nazwa rozgrywek nie może być pusta"));
        }
        if (request.teamKind() != null) {
            tournament.setTeamKind(requireTeamKind(request.teamKind()));
        }
        if (request.championEnabled() != null) {
            tournament.setChampionEnabled(request.championEnabled());
        }
        if (request.finished() != null) {
            tournament.setFinished(request.finished());
        }
        if (request.championCode() != null) {
            // Pusty ciag = wyczyszczenie zwyciezcy (np. przy pomylce).
            String code = request.championCode().isBlank() ? null : request.championCode().trim();
            if (code != null && teamRepository.findByTournamentIdAndCode(id, code).isEmpty()) {
                throw badRequest("W tych rozgrywkach nie ma drużyny o kodzie '" + code + "'");
            }
            tournament.setChampionCode(code);
        }

        tournamentRepository.save(tournament);
        resultService.recompute(); // zmiana zwyciezcy przesuwa punkty za typ mistrza
        return toView(tournament);
    }

    @DeleteMapping("/tournaments/{id}")
    public ResponseEntity<Void> deleteTournament(@RequestHeader(value = "Authorization", required = false) String auth,
                                                 @PathVariable Long id) {
        requireAdmin(auth);
        Tournament tournament = tournament(id);

        List<Match> matches = matchRepository.findByTournamentIdOrderByKickoffUtcAscIdAsc(id);
        if (!matches.isEmpty()) {
            throw badRequest("Rozgrywki mają " + matches.size()
                    + " meczów - usuń je najpierw, żeby nie skasować typów przez pomyłkę");
        }
        // Bez tego typy na zwyciezce zostalyby osierocone i liczyly sie w sumie punktow.
        championPickRepository.deleteAll(championPickRepository.findByTournamentId(id));
        teamRepository.deleteAll(teamRepository.findByTournamentIdOrderByNameAsc(id));
        tournamentRepository.delete(tournament);
        resultService.recompute();
        return ResponseEntity.noContent().build();
    }

    // ---- Slownik krajow (dodawanie reprezentacji bez recznego wpisywania kodu ISO) ----

    @GetMapping("/countries")
    public List<CountryOption> listCountries(@RequestHeader(value = "Authorization", required = false) String auth) {
        requireAdmin(auth);
        return CountryCatalog.CODES.entrySet().stream()
                .map(e -> new CountryOption(e.getValue(), e.getKey()))
                .sorted(java.util.Comparator.comparing(CountryOption::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // ---- Druzyny ----

    @GetMapping("/tournaments/{id}/teams")
    public List<TeamView> listTeams(@RequestHeader(value = "Authorization", required = false) String auth,
                                    @PathVariable Long id) {
        requireAdmin(auth);
        tournament(id);
        return teamRepository.findByTournamentIdOrderByNameAsc(id).stream().map(TeamView::new).toList();
    }

    @PostMapping("/tournaments/{id}/teams")
    public TeamView createTeam(@RequestHeader(value = "Authorization", required = false) String auth,
                               @PathVariable Long id,
                               @RequestBody TeamRequest request) {
        requireAdmin(auth);
        tournament(id);

        String code = requireText(request.code(), "Kod drużyny jest wymagany").toLowerCase();
        if (teamRepository.findByTournamentIdAndCode(id, code).isPresent()) {
            throw badRequest("Drużyna o kodzie '" + code + "' już jest w tych rozgrywkach");
        }
        String name = requireText(request.name(), "Nazwa drużyny jest wymagana");
        String crest = (request.crestUrl() == null || request.crestUrl().isBlank())
                ? null : request.crestUrl().trim();

        Team team = new Team(id, code, name, crest);
        teamRepository.save(team);
        return new TeamView(team);
    }

    @PutMapping("/teams/{teamId}")
    public TeamView updateTeam(@RequestHeader(value = "Authorization", required = false) String auth,
                               @PathVariable Long teamId,
                               @RequestBody TeamRequest request) {
        requireAdmin(auth);
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> notFound("Nie ma takiej drużyny"));

        // Kodu nie zmieniamy - siedzi w meczach i typach na zwyciezce jako referencja.
        if (request.name() != null) {
            team.setName(requireText(request.name(), "Nazwa drużyny nie może być pusta"));
        }
        if (request.crestUrl() != null) {
            team.setCrestUrl(request.crestUrl().isBlank() ? null : request.crestUrl().trim());
        }
        teamRepository.save(team);
        return new TeamView(team);
    }

    @DeleteMapping("/teams/{teamId}")
    public ResponseEntity<Void> deleteTeam(@RequestHeader(value = "Authorization", required = false) String auth,
                                           @PathVariable Long teamId) {
        requireAdmin(auth);
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> notFound("Nie ma takiej drużyny"));

        boolean used = matchRepository.findByTournamentIdOrderByKickoffUtcAscIdAsc(team.getTournamentId()).stream()
                .anyMatch(m -> team.getCode().equals(m.getTeam1Code()) || team.getCode().equals(m.getTeam2Code()));
        if (used) {
            throw badRequest("Drużyna występuje w meczach - najpierw zmień lub usuń te mecze");
        }
        teamRepository.delete(team);
        return ResponseEntity.noContent().build();
    }

    // ---- Mecze ----

    @GetMapping("/tournaments/{id}/matches")
    public List<AdminMatchView> listMatches(@RequestHeader(value = "Authorization", required = false) String auth,
                                            @PathVariable Long id) {
        requireAdmin(auth);
        tournament(id);
        return matchRepository.findByTournamentIdOrderByKickoffUtcAscIdAsc(id).stream()
                .map(m -> new AdminMatchView(m, predictionRepository.findByMatchId(m.getId()).size()))
                .toList();
    }

    @PostMapping("/tournaments/{id}/matches")
    public AdminMatchView createMatch(@RequestHeader(value = "Authorization", required = false) String auth,
                                      @PathVariable Long id,
                                      @RequestBody AdminMatchRequest request) {
        requireAdmin(auth);
        tournament(id);

        Match match = new Match();
        match.setTournamentId(id);
        applyMatchFields(match, request, id);
        matchRepository.save(match);
        return new AdminMatchView(match, 0);
    }

    @PutMapping("/matches/{matchId}")
    public AdminMatchView updateMatch(@RequestHeader(value = "Authorization", required = false) String auth,
                                      @PathVariable Long matchId,
                                      @RequestBody AdminMatchRequest request) {
        requireAdmin(auth);
        Match match = match(matchId);

        applyMatchFields(match, request, match.getTournamentId());
        matchRepository.save(match);
        resultService.recompute(); // zmiana druzyn zmienia, komu nalezy sie punkt za awans
        return new AdminMatchView(match, predictionRepository.findByMatchId(matchId).size());
    }

    @DeleteMapping("/matches/{matchId}")
    public ResponseEntity<Void> deleteMatch(@RequestHeader(value = "Authorization", required = false) String auth,
                                            @PathVariable Long matchId) {
        requireAdmin(auth);
        Match match = match(matchId);

        List<Prediction> predictions = predictionRepository.findByMatchId(matchId);
        if (!predictions.isEmpty()) {
            predictionRepository.deleteAll(predictions);
        }
        matchRepository.delete(match);
        resultService.recompute();
        return ResponseEntity.noContent().build();
    }

    // ---- Wyniki ----

    @PutMapping("/matches/{matchId}/result")
    public AdminMatchView setResult(@RequestHeader(value = "Authorization", required = false) String auth,
                                    @PathVariable Long matchId,
                                    @RequestBody AdminResultRequest request) {
        requireAdmin(auth);
        Match match = match(matchId);

        if (request.score1() == null || request.score2() == null) {
            throw badRequest("Podaj oba wyniki");
        }
        if (request.score1() < 0 || request.score2() < 0) {
            throw badRequest("Wynik nie może być ujemny");
        }
        if (!match.hasTeams()) {
            throw badRequest("Najpierw uzupełnij obie drużyny w tym meczu");
        }

        String advancing = null;
        if (match.isKnockout()) {
            if (request.score1() > request.score2()) {
                advancing = match.getTeam1Code();
            } else if (request.score1() < request.score2()) {
                advancing = match.getTeam2Code();
            } else {
                // Remis w fazie pucharowej - o awansie decyduja karne, wiec musi wskazac go admin.
                advancing = request.advancingCode();
                boolean valid = match.getTeam1Code().equals(advancing) || match.getTeam2Code().equals(advancing);
                if (!valid) {
                    throw badRequest("Mecz zakończył się remisem - wskaż drużynę, która awansowała po karnych");
                }
            }
        }

        match.setActualScore1(request.score1());
        match.setActualScore2(request.score2());
        match.setAdvancingCode(advancing);
        matchRepository.save(match);

        resultService.recompute();
        return new AdminMatchView(match, predictionRepository.findByMatchId(matchId).size());
    }

    @DeleteMapping("/matches/{matchId}/result")
    public AdminMatchView clearResult(@RequestHeader(value = "Authorization", required = false) String auth,
                                      @PathVariable Long matchId) {
        requireAdmin(auth);
        Match match = match(matchId);

        match.setActualScore1(null);
        match.setActualScore2(null);
        match.setAdvancingCode(null);
        matchRepository.save(match);

        resultService.recompute();
        return new AdminMatchView(match, predictionRepository.findByMatchId(matchId).size());
    }

    /** Awaryjne przeliczenie punktow - przydaje sie po recznej zmianie w bazie. */
    @PostMapping("/recompute")
    public ResponseEntity<Void> recompute(@RequestHeader(value = "Authorization", required = false) String auth) {
        requireAdmin(auth);
        resultService.recompute();
        return ResponseEntity.noContent().build();
    }

    // ---- Pomocnicze ----

    /** Ustawia pola meczu ze zgloszenia, walidujac godzine i przynaleznosc druzyn do turnieju. */
    private void applyMatchFields(Match match, AdminMatchRequest request, Long tournamentId) {
        Instant kickoff;
        try {
            kickoff = Instant.parse(requireText(request.kickoffUtc(), "Godzina rozpoczęcia jest wymagana"));
        } catch (java.time.format.DateTimeParseException e) {
            throw badRequest("Godzina rozpoczęcia musi być w formacie ISO UTC, np. 2026-06-11T19:00:00Z");
        }

        String round = blankToNull(request.roundName());
        String group = blankToNull(request.groupName());
        if (round == null && group == null) {
            throw badRequest("Podaj grupę albo rundę fazy pucharowej");
        }

        match.setKickoffUtc(kickoff.toString());
        match.setDate(blankToNull(request.date()) != null
                ? request.date().trim()
                : kickoff.atZone(MATCH_DAY_ZONE).toLocalDate().toString());
        match.setRoundName(round);
        match.setGroupName(round != null ? null : group);

        applyTeam(match, true, blankToNull(request.team1Code()), tournamentId);
        applyTeam(match, false, blankToNull(request.team2Code()), tournamentId);
    }

    /** Ustawia druzyne po kodzie; null = druzyna jeszcze nieznana (drabinka nierozstrzygnieta). */
    private void applyTeam(Match match, boolean home, String code, Long tournamentId) {
        String name = null;
        if (code != null) {
            Team team = teamRepository.findByTournamentIdAndCode(tournamentId, code)
                    .orElseThrow(() -> badRequest("W tych rozgrywkach nie ma drużyny o kodzie '" + code + "'"));
            name = team.getName();
        }
        if (home) {
            match.setTeam1Code(code);
            match.setTeam1Name(name);
        } else {
            match.setTeam2Code(code);
            match.setTeam2Name(name);
        }
    }

    private TournamentView toView(Tournament t) {
        return new TournamentView(t,
                matchRepository.findByTournamentIdOrderByKickoffUtcAscIdAsc(t.getId()).size(),
                teamRepository.countByTournamentId(t.getId()));
    }

    private Tournament tournament(Long id) {
        return tournamentRepository.findById(id)
                .orElseThrow(() -> notFound("Nie ma takich rozgrywek"));
    }

    private Match match(Long id) {
        return matchRepository.findById(id)
                .orElseThrow(() -> notFound("Nie ma takiego meczu"));
    }

    private String normalizedSlug(String raw) {
        String slug = raw == null ? "" : raw.trim().toLowerCase();
        if (!slug.matches("[a-z0-9-]{2,40}")) {
            throw badRequest("Adres rozgrywek: 2-40 znaków, tylko małe litery, cyfry i myślnik");
        }
        return slug;
    }

    private String requireTeamKind(String raw) {
        String kind = raw == null ? "" : raw.trim().toUpperCase();
        if (!Tournament.TEAMS_NATIONAL.equals(kind) && !Tournament.TEAMS_CLUB.equals(kind)) {
            throw badRequest("Rodzaj drużyn musi być NATIONAL albo CLUB");
        }
        return kind;
    }

    private String requireText(String raw, String message) {
        if (raw == null || raw.isBlank()) {
            throw badRequest(message);
        }
        return raw.trim();
    }

    private static String blankToNull(String raw) {
        return (raw == null || raw.isBlank()) ? null : raw.trim();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    /** Wpuszcza wylacznie konta z flaga admina; kazde inne dostaje 403. */
    private User requireAdmin(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Brak tokenu");
        }
        String username;
        try {
            username = jwtService.validateAndGetUsername(authHeader.substring(7));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token nieważny");
        }
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token nieważny"));
        if (!user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Brak uprawnień administratora");
        }
        return user;
    }
}
