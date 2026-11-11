package com.ledgerline.repository;

import com.ledgerline.domain.IdempotencyRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByRequestKeyAndEndpoint(String requestKey, String endpoint);
}
