package com.ledgerline.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class LedgerUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private Role role;
    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;
    @Column(name = "last_failed_login_at")
    private Instant lastFailedLoginAt;
    @Column(name = "locked_until")
    private Instant lockedUntil;
    @Column(name = "lock_level", nullable = false)
    private int lockLevel;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerUser() {}
    public LedgerUser(String email, String passwordHash, Role role) {
        this.email = email.toLowerCase();
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public int getFailedLoginCount() { return failedLoginCount; }
    public Instant getLockedUntil() { return lockedUntil; }
    public int getLockLevel() { return lockLevel; }
    public void resetFailures() { failedLoginCount = 0; lockedUntil = null; lastFailedLoginAt = null; }
    public void recordFailure(Instant now) {
        if (lastFailedLoginAt == null || lastFailedLoginAt.plusSeconds(900).isBefore(now)) failedLoginCount = 0;
        lastFailedLoginAt = now;
        failedLoginCount++;
        if (failedLoginCount >= 5) {
            lockLevel = Math.min(lockLevel + 1, 6);
            lockedUntil = now.plusSeconds(Math.min(900L * (1L << (lockLevel - 1)), 86400L));
            failedLoginCount = 0;
        }
    }
    public void setRole(Role role) { this.role = role; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
