package com.ledgerline.repository;

import com.ledgerline.domain.LedgerEntry;
import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findTop200ByOrderByIdDesc();
    List<LedgerEntry> findAllByOrderByIdAsc();
    java.util.Optional<LedgerEntry> findFirstByOrderByIdDesc();
    long countByTransactionId(java.util.UUID transactionId);
    List<LedgerEntry> findByAccount_IdOrderById(Long accountId);
    List<LedgerEntry> findByAccount_IdAndCreatedAtGreaterThanEqualOrderById(Long accountId, Instant fromInclusive);
    List<LedgerEntry> findByAccount_IdAndCreatedAtLessThanOrderById(Long accountId, Instant toExclusive);
    List<LedgerEntry> findByAccount_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderById(
            Long accountId, Instant fromInclusive, Instant toExclusive);
}
