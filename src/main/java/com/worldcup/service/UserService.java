package com.worldcup.service;

import com.worldcup.model.User;
import com.worldcup.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * Rejestracja i logowanie uzytkownikow (konta przechowywane w bazie).
 * Nazwy uzytkownikow sa unikalne i porownywane bez wzgledu na wielkosc liter.
 */
@Service
public class UserService {

    /** Bez znakow latwych do pomylenia (0/O, 1/l/I), bo haslo admin przekazuje dalej recznie. */
    private static final String TEMP_PASSWORD_ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int TEMP_PASSWORD_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository repository;

    public UserService(UserRepository repository) {
        this.repository = repository;
    }

    /** Walidacja przy nieprawidlowych danych rejestracji/logowania. */
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    /** Rejestruje nowego uzytkownika i zwraca jego (kanoniczna) nazwe. */
    public String register(String rawUsername, String password) {
        String username = rawUsername == null ? "" : rawUsername.trim();

        if (username.length() < 2 || username.length() > 20) {
            throw new ValidationException("Nazwa użytkownika musi mieć od 2 do 20 znaków");
        }
        if (!username.matches("[\\p{L}\\p{N}_ .\\-]+")) {
            throw new ValidationException("Nazwa może zawierać tylko litery, cyfry, spację i . _ -");
        }
        if (password == null || password.length() < 4) {
            throw new ValidationException("Hasło musi mieć co najmniej 4 znaki");
        }
        if (repository.existsByUsernameIgnoreCase(username)) {
            throw new ValidationException("Użytkownik o tej nazwie już istnieje");
        }

        User user = repository.save(new User(username, PasswordHasher.hash(password)));
        return user.getUsername();
    }

    /** Czy konto ma dostep do panelu admina (flage nadaje TournamentBootstrap wg APP_ADMIN_USERNAME). */
    public boolean isAdmin(String username) {
        return repository.findByUsernameIgnoreCase(username).map(User::isAdmin).orElse(false);
    }

    /** Zwraca kanoniczna nazwe uzytkownika przy poprawnym logowaniu; inaczej null. */
    public String authenticate(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        return repository.findByUsernameIgnoreCase(username.trim())
                .filter(u -> PasswordHasher.matches(password, u.getPasswordHash()))
                .map(User::getUsername)
                .orElse(null);
    }

    /** Zmienia haslo zalogowanego uzytkownika po zweryfikowaniu aktualnego hasla. */
    public void changePassword(String username, String oldPassword, String newPassword) {
        User user = repository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ValidationException("Użytkownik nie istnieje"));

        if (oldPassword == null || !PasswordHasher.matches(oldPassword, user.getPasswordHash())) {
            throw new ValidationException("Aktualne hasło jest nieprawidłowe");
        }
        if (newPassword == null || newPassword.length() < 4) {
            throw new ValidationException("Nowe hasło musi mieć co najmniej 4 znaki");
        }

        user.setPasswordHash(PasswordHasher.hash(newPassword));
        repository.save(user);
    }

    /**
     * Reset hasla przez admina: ustawia losowe haslo tymczasowe i zwraca je w jawnej postaci
     * (jedyny moment, w ktorym da sie je odczytac - w bazie ladu tylko hash).
     */
    public String resetPassword(Long userId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new ValidationException("Użytkownik nie istnieje"));

        String temporary = generateTemporaryPassword();
        user.setPasswordHash(PasswordHasher.hash(temporary));
        repository.save(user);
        return temporary;
    }

    private static String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
        for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
            sb.append(TEMP_PASSWORD_ALPHABET.charAt(RANDOM.nextInt(TEMP_PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }
}
