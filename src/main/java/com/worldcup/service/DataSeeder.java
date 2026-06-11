package com.worldcup.service;

import com.worldcup.model.Match;
import com.worldcup.repository.MatchRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Wypelnia baze prawdziwymi meczami fazy grupowej MS 2026.
 * Terminarz wg oficjalnego losowania z 5 grudnia 2025 (zrodlo: ESPN / FIFA).
 * 12 grup (A-L) po 4 zespoly = 72 mecze, ulozone wg dni rozgrywania.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final MatchRepository repository;

    public DataSeeder(MatchRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return; // baza juz wypelniona - nie duplikujemy
        }

        List<Match> m = new ArrayList<>();
        // Godziny podane w czasie wschodnim USA (ET); metoda 'add' przelicza je na UTC.
        // 'addLate' = mecz o 00:00 ET, ktory nalezy jeszcze do poprzedniego dnia meczowego.

        // ---- Kolejka 1 ----
        add(m, "2026-06-11T15:00", "A", "Meksyk", "RPA");
        add(m, "2026-06-11T22:00", "A", "Korea Pld.", "Czechy");

        add(m, "2026-06-12T15:00", "B", "Kanada", "Bosnia i Hercegowina");
        add(m, "2026-06-12T21:00", "D", "USA", "Paragwaj");

        add(m, "2026-06-13T15:00", "B", "Katar", "Szwajcaria");
        add(m, "2026-06-13T18:00", "C", "Brazylia", "Maroko");
        add(m, "2026-06-13T21:00", "C", "Haiti", "Szkocja");
        addLate(m, "2026-06-14T00:00", "D", "Australia", "Turcja");

        add(m, "2026-06-14T13:00", "E", "Niemcy", "Curacao");
        add(m, "2026-06-14T16:00", "F", "Holandia", "Japonia");
        add(m, "2026-06-14T19:00", "E", "Wybrzeze Kosci Sloniowej", "Ekwador");
        add(m, "2026-06-14T22:00", "F", "Szwecja", "Tunezja");

        add(m, "2026-06-15T13:00", "H", "Hiszpania", "Republika Zielonego Przyladka");
        add(m, "2026-06-15T18:00", "G", "Belgia", "Egipt");
        add(m, "2026-06-15T18:00", "H", "Arabia Saudyjska", "Urugwaj");
        addLate(m, "2026-06-16T00:00", "G", "Iran", "Nowa Zelandia");

        add(m, "2026-06-16T15:00", "I", "Francja", "Senegal");
        add(m, "2026-06-16T18:00", "I", "Irak", "Norwegia");
        add(m, "2026-06-16T21:00", "J", "Argentyna", "Algieria");
        addLate(m, "2026-06-17T00:00", "J", "Austria", "Jordania");

        add(m, "2026-06-17T13:00", "K", "Portugalia", "DR Konga");
        add(m, "2026-06-17T16:00", "L", "Anglia", "Chorwacja");
        add(m, "2026-06-17T19:00", "L", "Ghana", "Panama");
        add(m, "2026-06-17T22:00", "K", "Uzbekistan", "Kolumbia");

        // ---- Kolejka 2 ----
        add(m, "2026-06-18T12:00", "A", "Czechy", "RPA");
        add(m, "2026-06-18T15:00", "B", "Szwajcaria", "Bosnia i Hercegowina");
        add(m, "2026-06-18T18:00", "B", "Kanada", "Katar");
        add(m, "2026-06-18T23:00", "A", "Meksyk", "Korea Pld.");

        add(m, "2026-06-19T15:00", "D", "USA", "Australia");
        add(m, "2026-06-19T18:00", "C", "Szkocja", "Maroko");
        add(m, "2026-06-19T21:00", "C", "Brazylia", "Haiti");
        addLate(m, "2026-06-20T00:00", "D", "Turcja", "Paragwaj");

        add(m, "2026-06-20T13:00", "F", "Holandia", "Szwecja");
        add(m, "2026-06-20T16:00", "E", "Niemcy", "Wybrzeze Kosci Sloniowej");
        add(m, "2026-06-20T20:00", "E", "Ekwador", "Curacao");
        addLate(m, "2026-06-21T00:00", "F", "Tunezja", "Japonia");

        add(m, "2026-06-21T12:00", "H", "Hiszpania", "Arabia Saudyjska");
        add(m, "2026-06-21T15:00", "G", "Belgia", "Iran");
        add(m, "2026-06-21T18:00", "H", "Urugwaj", "Republika Zielonego Przyladka");
        add(m, "2026-06-21T21:00", "G", "Nowa Zelandia", "Egipt");

        add(m, "2026-06-22T13:00", "J", "Argentyna", "Austria");
        add(m, "2026-06-22T17:00", "I", "Francja", "Irak");
        add(m, "2026-06-22T20:00", "I", "Norwegia", "Senegal");
        add(m, "2026-06-22T23:00", "J", "Jordania", "Algieria");

        add(m, "2026-06-23T13:00", "K", "Portugalia", "Uzbekistan");
        add(m, "2026-06-23T16:00", "L", "Anglia", "Ghana");
        add(m, "2026-06-23T19:00", "L", "Panama", "Chorwacja");
        add(m, "2026-06-23T22:00", "K", "Kolumbia", "DR Konga");

        // ---- Kolejka 3 (po dwa mecze grupy o tej samej godzinie) ----
        add(m, "2026-06-24T15:00", "B", "Szwajcaria", "Kanada");
        add(m, "2026-06-24T15:00", "B", "Bosnia i Hercegowina", "Katar");
        add(m, "2026-06-24T18:00", "C", "Szkocja", "Brazylia");
        add(m, "2026-06-24T18:00", "C", "Maroko", "Haiti");
        add(m, "2026-06-24T21:00", "A", "Czechy", "Meksyk");
        add(m, "2026-06-24T21:00", "A", "RPA", "Korea Pld.");

        add(m, "2026-06-25T16:00", "E", "Ekwador", "Niemcy");
        add(m, "2026-06-25T16:00", "E", "Curacao", "Wybrzeze Kosci Sloniowej");
        add(m, "2026-06-25T19:00", "F", "Japonia", "Szwecja");
        add(m, "2026-06-25T19:00", "F", "Tunezja", "Holandia");
        add(m, "2026-06-25T22:00", "D", "Turcja", "USA");
        add(m, "2026-06-25T22:00", "D", "Paragwaj", "Australia");

        add(m, "2026-06-26T15:00", "I", "Norwegia", "Francja");
        add(m, "2026-06-26T15:00", "I", "Senegal", "Irak");
        add(m, "2026-06-26T20:00", "H", "Republika Zielonego Przyladka", "Arabia Saudyjska");
        add(m, "2026-06-26T20:00", "H", "Urugwaj", "Hiszpania");
        add(m, "2026-06-26T23:00", "G", "Egipt", "Iran");
        add(m, "2026-06-26T23:00", "G", "Nowa Zelandia", "Belgia");

        add(m, "2026-06-27T17:00", "L", "Panama", "Anglia");
        add(m, "2026-06-27T17:00", "L", "Chorwacja", "Ghana");
        add(m, "2026-06-27T19:30", "K", "Kolumbia", "Portugalia");
        add(m, "2026-06-27T19:30", "K", "DR Konga", "Uzbekistan");
        add(m, "2026-06-27T22:00", "J", "Algieria", "Austria");
        add(m, "2026-06-27T22:00", "J", "Jordania", "Argentyna");

        // ---- Mecz testowy: sprawdzenie automatycznego pobierania wynikow z API ----
        // Prawdziwy mecz Portugalia - Nigeria, 10.06.2026 21:45 czasu polskiego (CEST, UTC+2) = 19:45 UTC.
        String testKickoff = "2026-06-10T19:45:00Z";
        String testDate = "2026-06-10";
        m.add(new Match("TEST", testDate, testKickoff,
                "Portugalia", code("Portugalia"), enName("Portugalia"),
                "Nigeria", code("Nigeria"), enName("Nigeria")));

        repository.saveAll(m);
    }

    /** Mecz, ktorego dzien meczowy = data czesci ET. */
    private void add(List<Match> list, String etDateTime, String group, String home, String away) {
        register(list, etDateTime, group, home, away, false);
    }

    /** Mecz o 00:00 ET liczony do poprzedniego dnia meczowego. */
    private void addLate(List<Match> list, String etDateTime, String group, String home, String away) {
        register(list, etDateTime, group, home, away, true);
    }

    private void register(List<Match> list, String etDateTime, String group,
                          String home, String away, boolean prevDaySlate) {
        LocalDateTime ldt = LocalDateTime.parse(etDateTime);
        String kickoffUtc = ldt.atZone(ZoneId.of("America/New_York")).toInstant().toString();
        LocalDate slate = prevDaySlate ? ldt.toLocalDate().minusDays(1) : ldt.toLocalDate();
        list.add(new Match(group, slate.toString(), kickoffUtc,
                home, code(home), enName(home),
                away, code(away), enName(away)));
    }

    private String code(String team) {
        String c = Teams.CODES.get(team);
        if (c == null) {
            throw new IllegalStateException("Brak kodu flagi dla druzyny: " + team);
        }
        return c;
    }

    private String enName(String team) {
        String en = Teams.EN_NAMES.get(team);
        if (en == null) {
            throw new IllegalStateException("Brak angielskiej nazwy dla druzyny: " + team);
        }
        return en;
    }
}
