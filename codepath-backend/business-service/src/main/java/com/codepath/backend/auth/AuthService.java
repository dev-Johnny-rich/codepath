package com.codepath.backend.auth;

import com.codepath.backend.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class AuthService {
    private final AppUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long refreshDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(AppUserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       @Value("${codepath.jwt.refresh-days}") long refreshDays) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshDays = refreshDays;
    }

    @Transactional
    public AuthDtos.TokenResponse register(AuthDtos.RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCaseOrUsername(request.email(), request.username())) {
            throw new BusinessException(HttpStatus.CONFLICT, "邮箱或用户名已被使用");
        }
        AppUser user = userRepository.save(AppUser.create(
                request.email(), request.username(), passwordEncoder.encode(request.password()),
                request.displayName(), UserRole.STUDENT));
        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        AppUser user = request.account().contains("@")
                ? userRepository.findByEmailIgnoreCase(request.account()).orElse(null)
                : userRepository.findByUsername(request.account()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "账号或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "账号已被停用");
        }
        return issueTokens(user);
    }

    @Transactional
    public AuthDtos.TokenResponse refresh(AuthDtos.RefreshRequest request) {
        RefreshToken stored = refreshTokenRepository
                .findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
                        hash(request.refreshToken()), LocalDateTime.now())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "刷新令牌无效或已过期"));
        stored.revoke();
        AppUser user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "用户不存在"));
        return issueTokens(user);
    }

    @Transactional
    public void logout(AuthDtos.LogoutRequest request) {
        refreshTokenRepository.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(
                hash(request.refreshToken()), LocalDateTime.now()).ifPresent(RefreshToken::revoke);
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserView me(Long userId) {
        return view(userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "用户不存在")));
    }

    private AuthDtos.TokenResponse issueTokens(AppUser user) {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        String rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepository.save(RefreshToken.create(
                user.getId(), hash(rawRefresh), LocalDateTime.now().plusDays(refreshDays)));
        return new AuthDtos.TokenResponse(
                jwtService.createAccessToken(user), rawRefresh,
                jwtService.accessExpiresInSeconds(), view(user));
    }

    private static AuthDtos.UserView view(AppUser user) {
        return new AuthDtos.UserView(user.getId(), user.getEmail(), user.getUsername(),
                user.getDisplayName(), user.getRole());
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
