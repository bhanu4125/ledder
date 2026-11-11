package com.ledgerline.security;

import com.ledgerline.domain.LedgerUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final String issuer;
    private final long accessMinutes;

    public JwtService(@Value("${ledgerline.jwt.secret}") String secret,
                      @Value("${ledgerline.jwt.issuer}") String issuer,
                      @Value("${ledgerline.jwt.access-minutes}") long accessMinutes) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessMinutes = accessMinutes;
    }

    public String issueAccessToken(LedgerUser user) {
        Instant now = Instant.now();
        return Jwts.builder().issuer(issuer).subject(user.getId().toString())
                .claim("email", user.getEmail()).claim("role", user.getRole().name())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(accessMinutes * 60)))
                .signWith(key).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                .parseSignedClaims(token).getPayload();
    }
}
