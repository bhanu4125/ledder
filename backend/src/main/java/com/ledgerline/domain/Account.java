package com.ledgerline.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "owner_user_id")
    private LedgerUser owner;
    @Column(name = "account_number", nullable = false, unique = true)
    private String accountNumber;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;
    @Version @Column(nullable = false)
    private long version;
    @Column(nullable = false)
    private String status;
    @Column(name = "frozen_reason", length = 240)
    private String frozenReason;
    @Column(name = "is_system", nullable = false)
    private boolean system;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {}
    public Account(LedgerUser owner, String accountNumber, BigDecimal balance, boolean system) {
        this.owner = owner;
        this.accountNumber = accountNumber;
        this.balance = balance;
        this.system = system;
        this.status = "ACTIVE";
        this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public LedgerUser getOwner() { return owner; }
    public String getAccountNumber() { return accountNumber; }
    public BigDecimal getBalance() { return balance; }
    public String getStatus() { return status; }
    public String getFrozenReason() { return frozenReason; }
    public boolean isSystem() { return system; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public void freeze(String reason) { this.status = "FROZEN"; this.frozenReason = reason; }
    public void unfreeze() { this.status = "ACTIVE"; this.frozenReason = null; }
}
