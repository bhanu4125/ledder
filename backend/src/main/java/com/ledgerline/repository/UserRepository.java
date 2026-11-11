package com.ledgerline.repository;

import com.ledgerline.domain.LedgerUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<LedgerUser, Long> {
    Optional<LedgerUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
