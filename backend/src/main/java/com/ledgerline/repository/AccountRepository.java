package com.ledgerline.repository;

import com.ledgerline.domain.Account;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByOwnerIdOrderById(Long ownerId);
    Optional<Account> findByIdAndOwnerId(Long id, Long ownerId);
    Optional<Account> findBySystemTrue();
    @Query("select a.id from Account a where a.system = true")
    Optional<Long> findSystemId();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findLockedById(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id and a.owner.id = :ownerId")
    Optional<Account> findOwnedLocked(@Param("id") Long id, @Param("ownerId") Long ownerId);
}
