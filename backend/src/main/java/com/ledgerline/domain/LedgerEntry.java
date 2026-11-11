package com.ledgerline.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ledger_entries")
@SequenceGenerator(name = "ledger_entry_sequence", sequenceName = "ledger_entries_id_seq", allocationSize = 1)
public class LedgerEntry {
    @Id @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ledger_entry_sequence")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "account_id")
    private Account account;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "transaction_id")
    private FinancialTransaction transaction;
    @Column(name = "entry_type", nullable = false, length = 8)
    private String entryType;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(name = "running_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal runningBalance;
    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;
    @Column(name = "entry_hash", nullable = false, unique = true, length = 64)
    private String entryHash;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {}
    public LedgerEntry(Account account, FinancialTransaction transaction, String entryType,
                       BigDecimal amount, BigDecimal runningBalance, String prevHash, String entryHash, Instant createdAt) {
        this.account = account;
        this.transaction = transaction;
        this.entryType = entryType;
        this.amount = amount;
        this.runningBalance = runningBalance;
        this.prevHash = prevHash;
        this.entryHash = entryHash;
        this.createdAt = createdAt;
    }
    public void assignId(Long id) { this.id = id; }
    public Long getId() { return id; }
    public Account getAccount() { return account; }
    public FinancialTransaction getTransaction() { return transaction; }
    public String getEntryType() { return entryType; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getRunningBalance() { return runningBalance; }
    public String getPrevHash() { return prevHash; }
    public String getEntryHash() { return entryHash; }
    public Instant getCreatedAt() { return createdAt; }
}
