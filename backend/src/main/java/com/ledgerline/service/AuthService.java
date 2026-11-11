package com.ledgerline.service;

import com.ledgerline.domain.LedgerUser;
import com.ledgerline.domain.RefreshSession;
import com.ledgerline.repository.RefreshSessionRepository;
import com.ledgerline.repository.SecurityEventRepository;
import com.ledgerline.repository.UserRepository;
import com.ledgerline.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    public record LoginResult(String accessToken, LedgerUser user, String refreshToken) {}
    private final UserRepository users;
    private final RefreshSessionRepository refreshSessions;
    private final SecurityEventRepository securityEvents;
    private final AuditService audit;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RequestRateLimiter limiter;
    private final int refreshDays;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, RefreshSessionRepository refreshSessions,
                       SecurityEventRepository securityEvents, AuditService audit, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RequestRateLimiter limiter,
                       @Value("${ledgerline.jwt.refresh-days}") int refreshDays) {
        this.users=users; this.refreshSessions=refreshSessions; this.securityEvents=securityEvents;
        this.audit=audit; this.passwordEncoder=passwordEncoder; this.jwtService=jwtService;
        this.limiter=limiter; this.refreshDays=refreshDays;
    }

    @Transactional
    public LoginResult login(String email, String password, String ip) {
        String normalized = email.trim().toLowerCase();
        if (!limiter.allow("ip:" + ip, 20, 60) || !limiter.allow("email:" + normalized, 8, 60)) {
            securityEvents.save(new com.ledgerline.domain.SecurityEvent("LOGIN_RATE_LIMIT", "Login rate limit exceeded", ip, "BLOCKED"));
            return null;
        }
        LedgerUser user = users.findByEmailIgnoreCase(normalized).orElse(null);
        if (user == null || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now()))
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            if (user != null) user.recordFailure(Instant.now());
            securityEvents.save(new com.ledgerline.domain.SecurityEvent("LOGIN_FAILED", "Invalid credentials or locked account", ip, "DENIED"));
            audit.record("LOGIN_FAILED", user, "Authentication attempt rejected", ip, "DENIED");
            return null;
        }
        user.resetFailures();
        audit.record("LOGIN_SUCCEEDED", user, "User authenticated", ip, "SUCCESS");
        String refreshToken = issueRefreshToken(user);
        return new LoginResult(jwtService.issueAccessToken(user), user, refreshToken);
    }

    @Transactional
    public LoginResult register(String email, String password, String ip) {
        if (!limiter.allow("register:" + ip, 5, 60)) {
            securityEvents.save(new com.ledgerline.domain.SecurityEvent("REGISTER_RATE_LIMIT",
                    "Registration rate limit exceeded", ip, "BLOCKED"));
            throw ApiException.unauthorized();
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.badRequest("Password must be at most 72 UTF-8 bytes");
        }
        String normalized = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(normalized)) throw ApiException.conflict("Email is already registered");
        LedgerUser user = users.save(new LedgerUser(normalized, passwordEncoder.encode(password),
                com.ledgerline.domain.Role.ACCOUNT_HOLDER));
        audit.record("USER_REGISTERED", user, "Account holder registered", ip, "SUCCESS");
        return new LoginResult(jwtService.issueAccessToken(user), user, issueRefreshToken(user));
    }

    @Transactional
    public LoginResult rotate(String rawToken, String ip) {
        if (rawToken == null || !limiter.allow("refresh:" + ip, 30, 60)) return null;
        String hash = Hashing.sha256(rawToken);
        RefreshSession session = refreshSessions.findLockedByTokenHash(hash).orElse(null);
        if (session == null || session.getRevokedAt() != null || session.getExpiresAt().isBefore(Instant.now())) {
            securityEvents.save(new com.ledgerline.domain.SecurityEvent("REFRESH_REJECTED", "Refresh credential rejected", ip, "DENIED"));
            return null;
        }
        session.revoke();
        LedgerUser user = session.getUser();
        String newRefresh = issueRefreshToken(user);
        audit.record("TOKEN_REFRESHED", user, "Refresh credential rotated", ip, "SUCCESS");
        return new LoginResult(jwtService.issueAccessToken(user), user, newRefresh);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null) return;
        refreshSessions.findLockedByTokenHash(Hashing.sha256(rawToken)).ifPresent(session -> {
            session.revoke();
            audit.record("LOGOUT", session.getUser(), "User signed out", null, "SUCCESS");
        });
    }

    private String issueRefreshToken(LedgerUser user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshSessions.save(new RefreshSession(UUID.randomUUID(), user, Hashing.sha256(token),
                Instant.now().plusSeconds(refreshDays * 86400L)));
        return token;
    }
}
