package com.worldcup.dto;

/** Cialo zadania przy dodawaniu/edycji druzyny w turnieju. */
public record TeamRequest(String code, String name, String crestUrl) {
}
