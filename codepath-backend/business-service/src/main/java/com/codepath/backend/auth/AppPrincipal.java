package com.codepath.backend.auth;

public record AppPrincipal(Long userId, String username, UserRole role) {
}
