package com.codepath.backend.admin;

import com.codepath.backend.auth.*;
import com.codepath.backend.common.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AppUserRepository repository;

    public AdminUserController(AppUserRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<Page<AuthDtos.UserView>> list(Pageable pageable) {
        return ApiResponse.ok(repository.findAll(pageable).map(user -> new AuthDtos.UserView(
                user.getId(), user.getEmail(), user.getUsername(), user.getDisplayName(), user.getRole())));
    }

    @PutMapping("/{id}/role")
    @Transactional
    public ApiResponse<Void> role(@PathVariable Long id, @RequestBody RoleRequest request) {
        AppUser user = repository.findById(id).orElseThrow();
        user.changeRole(request.role());
        return ApiResponse.ok(null);
    }

    @PutMapping("/{id}/status")
    @Transactional
    public ApiResponse<Void> status(@PathVariable Long id, @RequestBody StatusRequest request) {
        AppUser user = repository.findById(id).orElseThrow();
        user.changeStatus(request.status().toUpperCase());
        return ApiResponse.ok(null);
    }

    public record RoleRequest(@NotNull UserRole role) {}
    public record StatusRequest(@NotBlank String status) {}
}
