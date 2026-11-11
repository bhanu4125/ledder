package com.ledgerline.repository;

import com.ledgerline.domain.FinancialTransaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;

public interface TransactionRepository extends JpaRepository<FinancialTransaction, UUID> {
    List<FinancialTransaction> findTop100ByOrderByCreatedAtDesc();
    List<FinancialTransaction> findTop100ByRequestedByIdOrderByCreatedAtDesc(Long userId);
    long countByStatus(String status);
    @Query("select coalesce(sum(t.amount), 0) from FinancialTransaction t where t.status = 'COMPLETED'")
    BigDecimal completedVolume();
    @Query("select distinct t from FinancialTransaction t left join t.sender s left join t.receiver r " +
            "where t.requestedBy.id = :userId or s.owner.id = :userId or r.owner.id = :userId " +
            "order by t.createdAt desc")
    List<FinancialTransaction> findVisibleToUser(@Param("userId") Long userId, Pageable pageable);
    @Query("select count(distinct t) from FinancialTransaction t left join t.sender s left join t.receiver r " +
            "where (t.requestedBy.id = :userId or s.owner.id = :userId or r.owner.id = :userId) " +
            "and (:status is null or t.status = :status)")
    long countVisibleToUserByStatus(@Param("userId") Long userId, @Param("status") String status);
    @Query("select coalesce(sum(t.amount), 0) from FinancialTransaction t left join t.sender s left join t.receiver r " +
            "where (t.requestedBy.id = :userId or s.owner.id = :userId or r.owner.id = :userId) and t.status = 'COMPLETED'")
    BigDecimal completedVolumeVisibleToUser(@Param("userId") Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from FinancialTransaction t where t.id = :id")
    Optional<FinancialTransaction> findLockedById(UUID id);
}
