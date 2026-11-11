package com.ledgerline.web;

import com.ledgerline.domain.LedgerUser;
import com.ledgerline.service.ApiException;
import com.ledgerline.service.AuthService;
import com.ledgerline.web.ApiModels.LoginRequest;
import com.ledgerline.web.ApiModels.LoginResponse;
import com.ledgerline.web.ApiModels.RegisterRequest;
import com.ledgerline.web.ApiModels.UserView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String COOKIE_NAME = "ledgerline_refresh";
    private final AuthService auth;
    private final String origin;
    private final boolean secureCookies;
    private final int refreshDays;

    public AuthController(AuthService auth, @Value("${ledgerline.frontend-origin}") String rawOrigin,
                          @Value("${ledgerline.secure-cookies}") boolean secureCookies,
                          @Value("${ledgerline.jwt.refresh-days}") int refreshDays) {
        this.auth=auth;
        this.origin = (rawOrigin != null && !rawOrigin.startsWith("http://") && !rawOrigin.startsWith("https://"))
                ? "https://" + rawOrigin : rawOrigin;
        this.secureCookies=secureCookies;
        this.refreshDays=refreshDays;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        AuthService.LoginResult result = auth.login(request.email(), request.password(), ip(servletRequest));
        if (result == null) throw ApiException.unauthorized();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(response(result));
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        AuthService.LoginResult result = auth.register(request.email(), request.password(), ip(servletRequest));
        return ResponseEntity.status(201).header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(response(result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@CookieValue(name = COOKIE_NAME, required = false) String token,
                                                 @RequestHeader(value = "Origin", required = false) String requestOrigin,
                                                 HttpServletRequest request) {
        requireSameOrigin(requestOrigin);
        AuthService.LoginResult result = auth.rotate(token, ip(request));
        if (result == null) throw ApiException.unauthorized();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString())
                .body(response(result));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = COOKIE_NAME, required = false) String token,
                                       @RequestHeader(value = "Origin", required = false) String requestOrigin) {
        requireSameOrigin(requestOrigin);
        auth.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearCookie().toString()).build();
    }

    private void requireSameOrigin(String requestOrigin) {
        if (requestOrigin == null || requestOrigin.isBlank()) {
            return;
        }
        if (Objects.equals(origin, requestOrigin)
                || requestOrigin.equals(origin + ".onrender.com")
                || requestOrigin.endsWith(".onrender.com")
                || requestOrigin.startsWith("http://localhost:")
                || requestOrigin.startsWith("http://127.0.0.1:")) {
            return;
        }
        throw ApiException.forbidden();
    }
    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value).httpOnly(true).secure(secureCookies)
                .sameSite("Strict").path("/api/auth").maxAge(Duration.ofDays(refreshDays)).build();
    }
    private ResponseCookie clearCookie() {
        return ResponseCookie.from(COOKIE_NAME, "").httpOnly(true).secure(secureCookies)
                .sameSite("Strict").path("/api/auth").maxAge(Duration.ZERO).build();
    }
    private LoginResponse response(AuthService.LoginResult result) {
        LedgerUser user = result.user();
        return new LoginResponse(result.accessToken(), new UserView(user.getId(), user.getEmail(), user.getRole().name()));
    }
    private String ip(HttpServletRequest request) { return request.getRemoteAddr(); }
}
