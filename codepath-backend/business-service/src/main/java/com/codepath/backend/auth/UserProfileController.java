package com.codepath.backend.auth;

import com.codepath.backend.common.ApiResponse;
import com.codepath.backend.common.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.ResultSet;
import java.sql.SQLException;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserProfileController {
    private static final int MAX_AVATAR_LENGTH = 700_000;
    private final JdbcClient jdbc;

    public UserProfileController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 100) String displayName,
            @Pattern(regexp = "^$|MALE|FEMALE|NON_BINARY|PREFER_NOT_TO_SAY") String gender,
            @Size(max = 500) String personality,
            @Size(max = 120) String country,
            @Size(max = 200) String school,
            @Size(max = 300) String familyAddress,
            @Size(max = 200) String major,
            @Size(max = 500) String habit,
            @Size(max = MAX_AVATAR_LENGTH) String avatarDataUrl
    ) {}

    public record UserProfileView(
            Long id, String email, String username, String displayName, UserRole role,
            String gender, String personality, String country, String school,
            String familyAddress, String major, String habit, String avatarDataUrl
    ) {}

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<UserProfileView> get(@AuthenticationPrincipal AppPrincipal principal) {
        return ApiResponse.ok(find(principal.userId()));
    }

    @PutMapping
    @Transactional
    public ApiResponse<UserProfileView> update(@AuthenticationPrincipal AppPrincipal principal,
                                                @Valid @RequestBody UpdateProfileRequest request) {
        String avatar = clean(request.avatarDataUrl());
        if (!avatar.isEmpty() && !avatar.matches("^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$")) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "头像格式不支持，请使用 PNG、JPEG 或 WebP 图片");
        }
        int changed = jdbc.sql("UPDATE app_user SET display_name=:displayName WHERE id=:userId AND status='ACTIVE'")
                .param("displayName", request.displayName().trim())
                .param("userId", principal.userId()).update();
        if (changed == 0) throw new BusinessException(HttpStatus.NOT_FOUND, "用户不存在");

        jdbc.sql("""
                INSERT INTO user_profile(user_id,gender,personality,country,school,family_address,major,habit,avatar_data_url)
                VALUES (:userId,:gender,:personality,:country,:school,:address,:major,:habit,:avatar)
                ON DUPLICATE KEY UPDATE gender=VALUES(gender),personality=VALUES(personality),
                  country=VALUES(country),school=VALUES(school),family_address=VALUES(family_address),
                  major=VALUES(major),habit=VALUES(habit),avatar_data_url=VALUES(avatar_data_url)
                """)
                .param("userId", principal.userId())
                .param("gender", nullable(request.gender()))
                .param("personality", nullable(request.personality()))
                .param("country", nullable(request.country()))
                .param("school", nullable(request.school()))
                .param("address", nullable(request.familyAddress()))
                .param("major", nullable(request.major()))
                .param("habit", nullable(request.habit()))
                .param("avatar", avatar.isEmpty() ? null : avatar)
                .update();
        return ApiResponse.ok(find(principal.userId()));
    }

    private UserProfileView find(Long userId) {
        return jdbc.sql("""
                SELECT u.id,u.email,u.username,u.display_name,u.role,
                       p.gender,p.personality,p.country,p.school,p.family_address,p.major,p.habit,p.avatar_data_url
                FROM app_user u LEFT JOIN user_profile p ON p.user_id=u.id
                WHERE u.id=:userId AND u.status='ACTIVE'
                """).param("userId", userId).query(UserProfileController::map).optional()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "用户不存在"));
    }

    private static UserProfileView map(ResultSet rs, int row) throws SQLException {
        return new UserProfileView(
                rs.getLong("id"), rs.getString("email"), rs.getString("username"),
                rs.getString("display_name"), UserRole.valueOf(rs.getString("role")),
                value(rs, "gender"), value(rs, "personality"), value(rs, "country"),
                value(rs, "school"), value(rs, "family_address"), value(rs, "major"),
                value(rs, "habit"), value(rs, "avatar_data_url"));
    }

    private static String value(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? "" : value;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullable(String value) {
        String cleaned = clean(value);
        return cleaned.isEmpty() ? null : cleaned;
    }
}
