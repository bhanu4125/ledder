package com.ledgerline.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class FinancialTransaction {
    @Id
    private UUID id;
    @Column(nullable = false, length = 16)
    private String type;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "sender_account_id")
    private Account sender;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "receiver_account_id")
    private Account receiver;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 16)
    private String status;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "requested_by_user_id")
    private LedgerUser requestedBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "decided_by_user_id")
    private LedgerUser decidedBy;
    @Column(name = "decided_at")
    private Instant decidedAt;
    @Column(length = 140)
    private String memo;

    protected FinancialTransaction() {}
    public FinancialTransaction(String type, Account sender, Account receiver, BigDecimal amount,
                                String status, String idempotencyKey, LedgerUser requestedBy, String memo) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.sender = sender;
        this.receiver = receiver;
        this.amount = amount;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.requestedBy = requestedBy;
        this.createdAt = Instant.now();
        this.memo = memo;
    }
    public UUID getId() { return id; }
    public String getType() { return type; }
    public Account getSender() { return sender; }
    public Account getReceiver() { return receiver; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public LedgerUser getRequestedBy() { return requestedBy; }
    public LedgerUser getDecidedBy() { return decidedBy; }
    public String getMemo() { return memo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public void decide(LedgerUser actor, boolean approved) {
        this.decidedBy = actor;
        this.decidedAt = Instant.now();
        this.status = approved ? "COMPLETED" : "REJECTED";
    }
}
