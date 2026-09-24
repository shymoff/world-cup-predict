package com.worldcup.dto;

/** Wynik resetu hasla: tymczasowe haslo w jawnej postaci widac tylko w tej jednej odpowiedzi. */
public record PasswordResetView(String username, String temporaryPassword) {
}
