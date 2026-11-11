package com.ledgerline.repository;

import com.ledgerline.domain.LedgerEntry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findTop200ByOrderByIdDesc();
    List<LedgerEntry> findAllByOrderByIdAsc();
    java.util.Optional<LedgerEntry> findFirstByOrderByIdDesc();
    long countByTransactionId(java.util.UUID transactionId);
}
