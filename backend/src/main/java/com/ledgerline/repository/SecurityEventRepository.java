package com.ledgerline.repository;

import com.ledgerline.domain.SecurityEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {
    List<SecurityEvent> findTop200ByOrderByCreatedAtDesc();
}
