package com.worldcup.dto;

import com.worldcup.model.User;

/** Konto na liscie w panelu admina (bez hasha hasla). */
public record AdminUserView(Long id, String username, boolean admin) {

    public AdminUserView(User user) {
        this(user.getId(), user.getUsername(), user.isAdmin());
    }
}
