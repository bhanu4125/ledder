package com.ledgerline.repository;

import com.ledgerline.domain.AuditEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findTop200ByOrderByIdDesc();
    List<AuditEvent> findAllByOrderByIdAsc();
    java.util.Optional<AuditEvent> findFirstByOrderByIdDesc();
    java.util.Optional<AuditEvent> findFirstByEventTypeInOrderByIdDesc(java.util.Collection<String> eventTypes);
}
