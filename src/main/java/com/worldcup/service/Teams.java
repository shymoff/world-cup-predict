package com.worldcup.service;

import java.text.Normalizer;
import java.util.Map;

/**
 * Wspolny slownik 48 druzyn MS 2026: kod ISO flagi (flagcdn.com) i nazwa angielska
 * (do wyszukiwania wynikow w TheSportsDB). Uzywany przez DataSeeder oraz przy
 * typowaniu i rozstrzyganiu mistrza turnieju.
 */
public final class Teams {

    private Teams() {
    }

    // Kod ISO flagi dla kazdej druzyny (flagcdn.com)
    public static final Map<String, String> CODES = Map.ofEntries(
            Map.entry("Meksyk", "mx"),
            Map.entry("RPA", "za"),
            Map.entry("Korea Pld.", "kr"),
            Map.entry("Czechy", "cz"),
            Map.entry("Kanada", "ca"),
            Map.entry("Bosnia i Hercegowina", "ba"),
            Map.entry("Katar", "qa"),
            Map.entry("Szwajcaria", "ch"),
            Map.entry("Brazylia", "br"),
            Map.entry("Maroko", "ma"),
            Map.entry("Haiti", "ht"),
            Map.entry("Szkocja", "gb-sct"),
            Map.entry("USA", "us"),
            Map.entry("Paragwaj", "py"),
            Map.entry("Australia", "au"),
            Map.entry("Turcja", "tr"),
            Map.entry("Niemcy", "de"),
            Map.entry("Curacao", "cw"),
            Map.entry("Wybrzeze Kosci Sloniowej", "ci"),
            Map.entry("Ekwador", "ec"),
            Map.entry("Holandia", "nl"),
            Map.entry("Japonia", "jp"),
            Map.entry("Szwecja", "se"),
            Map.entry("Tunezja", "tn"),
            Map.entry("Belgia", "be"),
            Map.entry("Egipt", "eg"),
            Map.entry("Iran", "ir"),
            Map.entry("Nowa Zelandia", "nz"),
            Map.entry("Hiszpania", "es"),
            Map.entry("Republika Zielonego Przyladka", "cv"),
            Map.entry("Arabia Saudyjska", "sa"),
            Map.entry("Urugwaj", "uy"),
            Map.entry("Francja", "fr"),
            Map.entry("Senegal", "sn"),
            Map.entry("Irak", "iq"),
            Map.entry("Norwegia", "no"),
            Map.entry("Argentyna", "ar"),
            Map.entry("Algieria", "dz"),
            Map.entry("Austria", "at"),
            Map.entry("Jordania", "jo"),
            Map.entry("Portugalia", "pt"),
            Map.entry("DR Konga", "cd"),
            Map.entry("Uzbekistan", "uz"),
            Map.entry("Kolumbia", "co"),
            Map.entry("Anglia", "gb-eng"),
            Map.entry("Chorwacja", "hr"),
            Map.entry("Ghana", "gh"),
            Map.entry("Panama", "pa"),
            Map.entry("Nigeria", "ng")
    );

    // Angielska nazwa druzyny - uzywana do wyszukiwania wynikow w darmowym API (TheSportsDB)
    public static final Map<String, String> EN_NAMES = Map.ofEntries(
            Map.entry("Meksyk", "Mexico"),
            Map.entry("RPA", "South Africa"),
            Map.entry("Korea Pld.", "South Korea"),
            Map.entry("Czechy", "Czech Republic"),
            Map.entry("Kanada", "Canada"),
            Map.entry("Bosnia i Hercegowina", "Bosnia and Herzegovina"),
            Map.entry("Katar", "Qatar"),
            Map.entry("Szwajcaria", "Switzerland"),
            Map.entry("Brazylia", "Brazil"),
            Map.entry("Maroko", "Morocco"),
            Map.entry("Haiti", "Haiti"),
            Map.entry("Szkocja", "Scotland"),
            Map.entry("USA", "USA"),
            Map.entry("Paragwaj", "Paraguay"),
            Map.entry("Australia", "Australia"),
            Map.entry("Turcja", "Turkey"),
            Map.entry("Niemcy", "Germany"),
            Map.entry("Curacao", "Curacao"),
            Map.entry("Wybrzeze Kosci Sloniowej", "Ivory Coast"),
            Map.entry("Ekwador", "Ecuador"),
            Map.entry("Holandia", "Netherlands"),
            Map.entry("Japonia", "Japan"),
            Map.entry("Szwecja", "Sweden"),
            Map.entry("Tunezja", "Tunisia"),
            Map.entry("Belgia", "Belgium"),
            Map.entry("Egipt", "Egypt"),
            Map.entry("Iran", "Iran"),
            Map.entry("Nowa Zelandia", "New Zealand"),
            Map.entry("Hiszpania", "Spain"),
            Map.entry("Republika Zielonego Przyladka", "Cape Verde"),
            Map.entry("Arabia Saudyjska", "Saudi Arabia"),
            Map.entry("Urugwaj", "Uruguay"),
            Map.entry("Francja", "France"),
            Map.entry("Senegal", "Senegal"),
            Map.entry("Irak", "Iraq"),
            Map.entry("Norwegia", "Norway"),
            Map.entry("Argentyna", "Argentina"),
            Map.entry("Algieria", "Algeria"),
            Map.entry("Austria", "Austria"),
            Map.entry("Jordania", "Jordan"),
            Map.entry("Portugalia", "Portugal"),
            Map.entry("DR Konga", "DR Congo"),
            Map.entry("Uzbekistan", "Uzbekistan"),
            Map.entry("Kolumbia", "Colombia"),
            Map.entry("Anglia", "England"),
            Map.entry("Chorwacja", "Croatia"),
            Map.entry("Ghana", "Ghana"),
            Map.entry("Panama", "Panama"),
            Map.entry("Nigeria", "Nigeria")
    );

    /** Mapowanie angielskiej nazwy druzyny (nasza pisownia) na kod ISO flagi. */
    public static final Map<String, String> ENGLISH_TO_CODE = EN_NAMES.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getValue, e -> CODES.get(e.getKey())));

    /** Mapowanie kodu ISO flagi na polska nazwe druzyny (odwrotnosc CODES). */
    public static final Map<String, String> CODE_TO_PL = CODES.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    // Znormalizowana angielska nazwa -> kod ISO (do odpornego dopasowania nazw z football-data.org).
    private static final Map<String, String> NORMALIZED_EN_TO_CODE = ENGLISH_TO_CODE.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(e -> normalize(e.getKey()), Map.Entry::getValue));

    // Roznice w pisowni football-data.org wzgledem naszych nazw, ktorych nie wyrownuje normalizacja.
    private static final Map<String, String> FOOTBALL_DATA_ALIASES = Map.of(
            normalize("Cape Verde Islands"), "cv",
            normalize("Congo DR"), "cd",
            normalize("Czechia"), "cz",
            normalize("United States"), "us");

    /** Zwraca kod ISO flagi dla angielskiej nazwy druzyny z football-data.org (lub null gdy nieznana). */
    public static String codeForFootballDataName(String name) {
        if (name == null) {
            return null;
        }
        String norm = normalize(name);
        String alias = FOOTBALL_DATA_ALIASES.get(norm);
        return alias != null ? alias : NORMALIZED_EN_TO_CODE.get(norm);
    }

    /** Normalizacja nazwy: bez diakrytykow, malymi literami, bez "and"/"-", pojedyncze spacje. */
    static String normalize(String name) {
        String noDiacritics = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noDiacritics.toLowerCase().replace(" and ", " ").replace("-", " ").replaceAll("\\s+", " ").trim();
    }
}
