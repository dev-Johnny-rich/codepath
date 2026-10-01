package com.codepath.backend.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    private String status;

    public static AppUser create(String email, String username, String passwordHash,
                                 String displayName, UserRole role) {
        AppUser user = new AppUser();
        user.email = email.toLowerCase();
        user.username = username;
        user.passwordHash = passwordHash;
        user.displayName = displayName;
        user.role = role;
        user.status = "ACTIVE";
        return user;
    }

    public void changeRole(UserRole role) {
        this.role = role;
    }

    public void changeStatus(String status) {
        this.status = status;
    }
}
