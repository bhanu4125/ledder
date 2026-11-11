package com.ledgerline.service;

import com.ledgerline.domain.AuditEvent;
import com.ledgerline.domain.LedgerUser;
import com.ledgerline.repository.AuditEventRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private static final String GENESIS = "0".repeat(64);
    private final AuditEventRepository auditRepository;
    private final JdbcTemplate jdbc;

    public AuditService(AuditEventRepository auditRepository, JdbcTemplate jdbc) {
        this.auditRepository = auditRepository; this.jdbc = jdbc;
    }

    @Transactional
    public AuditEvent record(String type, LedgerUser actor, String description, String ip, String status) {
        lock("audit");
        String previous = auditRepository.findFirstByOrderByIdDesc()
                .map(AuditEvent::getEntryHash).orElse(GENESIS);
        Long id = jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence('audit_log', 'id'))", Long.class);
        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String canonical = String.join("|", id.toString(), type, actor == null ? "" : actor.getId().toString(),
                description, ip == null ? "" : ip, status, createdAt.toString(), previous);
        String hash = Hashing.sha256(canonical);
        jdbc.update("""
                INSERT INTO audit_log
                    (id, event_type, actor_user_id, description, ip_address, status, prev_hash, entry_hash, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, type, actor == null ? null : actor.getId(), description,
                ip, status, previous, hash, Timestamp.from(createdAt));
        return auditRepository.findById(id).orElseThrow(
                () -> new IllegalStateException("Inserted audit event could not be loaded"));
    }

    public void lock(String chain) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?, hashtext(?))")) {
                statement.setInt(1, 20261111);
                statement.setString(2, chain);
                statement.execute();
            }
            return null;
        });
    }
}
