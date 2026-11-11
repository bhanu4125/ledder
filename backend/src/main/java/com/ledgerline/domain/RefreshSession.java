package com.ledgerline.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private LedgerUser user;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "revoked_at")
    private Instant revokedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    protected RefreshSession() {}
    public RefreshSession(UUID id, LedgerUser user, String tokenHash, Instant expiresAt) {
        this.id=id; this.user=user; this.tokenHash=tokenHash; this.expiresAt=expiresAt; this.createdAt=Instant.now();
    }
    public UUID getId() { return id; }
    public LedgerUser getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void revoke() { revokedAt = Instant.now(); }
}
