package com.ledgerline.service;

import com.ledgerline.domain.Account;
import com.ledgerline.domain.FinancialTransaction;
import com.ledgerline.domain.LedgerEntry;
import com.ledgerline.repository.LedgerEntryRepository;
import com.ledgerline.web.ApiModels.LedgerView;
import com.ledgerline.web.ApiModels.VerifyResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {
    private static final String GENESIS = "0".repeat(64);
    private final LedgerEntryRepository ledgerEntries;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public LedgerService(LedgerEntryRepository ledgerEntries, JdbcTemplate jdbc, AuditService audit) {
        this.ledgerEntries=ledgerEntries; this.jdbc=jdbc; this.audit=audit;
    }

    @Transactional
    public void postPair(Account debitAccount, Account creditAccount, FinancialTransaction transaction) {
        audit.lock("ledger");
        post(debitAccount, transaction, "DEBIT", transaction.getAmount());
        post(creditAccount, transaction, "CREDIT", transaction.getAmount());
    }

    private void post(Account account, FinancialTransaction transaction, String type, BigDecimal amount) {
        BigDecimal updated = "DEBIT".equals(type)
                ? account.getBalance().subtract(amount) : account.getBalance().add(amount);
        if (updated.signum() < 0) throw ApiException.conflict("Insufficient funds");
        account.setBalance(updated);
        Long id = jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence('ledger_entries', 'id'))", Long.class);
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String previous = ledgerEntries.findFirstByOrderByIdDesc()
                .map(LedgerEntry::getEntryHash).orElse(GENESIS);
        String canonical = canonical(id, account.getId(), transaction.getId(), type, amount, updated, createdAt, previous);
        LedgerEntry entry = new LedgerEntry(account, transaction, type, amount, updated,
                previous, Hashing.sha256(canonical), createdAt);
        entry.assignId(id);
        ledgerEntries.saveAndFlush(entry);
    }

    @Transactional(readOnly = true)
    public VerifyResult verify() {
        List<LedgerEntry> entries = ledgerEntries.findAllByOrderByIdAsc();
        String expectedPrevious = GENESIS;
        String lastHash = GENESIS;
        long checked = 0;
        for (LedgerEntry entry : entries) {
            String expected = Hashing.sha256(canonical(entry.getId(), entry.getAccount().getId(),
                    entry.getTransaction().getId(), entry.getEntryType(), entry.getAmount(),
                    entry.getRunningBalance(), entry.getCreatedAt(), expectedPrevious));
            if (!Hashing.matches(entry.getPrevHash(), expectedPrevious)
                    || !Hashing.matches(entry.getEntryHash(), expected)) {
                return new VerifyResult("TAMPERED", checked, lastHash, entry.getId(), Instant.now());
            }
            expectedPrevious = entry.getEntryHash();
            lastHash = entry.getEntryHash();
            checked++;
        }
        return new VerifyResult("VERIFIED", entries.size(), lastHash, null, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<LedgerView> list(Long ownerId, boolean all) {
        List<LedgerEntry> rows = all ? ledgerEntries.findTop200ByOrderByIdDesc()
                : ownedEntries(ownerId);
        return rows.stream().map(row -> new LedgerView(row.getId(), row.getAccount().getId(),
                row.getTransaction().getId(), row.getEntryType(), row.getAmount(),
                row.getRunningBalance(), row.getEntryHash(), row.getCreatedAt())).toList();
    }

    private List<LedgerEntry> ownedEntries(Long ownerId) {
        return ledgerEntries.findAllByOrderByIdAsc().stream()
                .filter(row -> row.getAccount().getOwner() != null
                        && row.getAccount().getOwner().getId().equals(ownerId))
                .sorted((left, right) -> right.getId().compareTo(left.getId())).limit(200).toList();
    }

    private String canonical(Long id, Long accountId, java.util.UUID transactionId, String type,
                             BigDecimal amount, BigDecimal balance, Instant createdAt, String previous) {
        return String.join("|", id.toString(), accountId.toString(), transactionId.toString(), type,
                amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                balance.setScale(2, RoundingMode.UNNECESSARY).toPlainString(), createdAt.toString(), previous);
    }
}
