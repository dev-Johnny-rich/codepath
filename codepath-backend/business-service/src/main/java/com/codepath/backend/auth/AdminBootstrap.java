package com.codepath.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements CommandLineRunner {
    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String username;
    private final String password;

    public AdminBootstrap(AppUserRepository repository,
                          PasswordEncoder passwordEncoder,
                          @Value("${codepath.bootstrap-admin.email}") String email,
                          @Value("${codepath.bootstrap-admin.username}") String username,
                          @Value("${codepath.bootstrap-admin.password}") String password) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.email = email;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (!repository.existsByEmailIgnoreCaseOrUsername(email, username)) {
            repository.save(AppUser.create(email, username, passwordEncoder.encode(password),
                    "系统管理员", UserRole.ADMIN));
        }
    }
}
