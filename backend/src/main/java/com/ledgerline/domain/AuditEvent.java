package com.ledgerline.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_log")
@SequenceGenerator(name = "audit_sequence", sequenceName = "audit_log_id_seq", allocationSize = 1)
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_sequence")
    private Long id;
    @Column(name = "event_type", nullable = false, length = 48)
    private String eventType;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_user_id")
    private LedgerUser actor;
    @Column(nullable = false, length = 500)
    private String description;
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;
    @Column(name = "entry_hash", nullable = false, unique = true, length = 64)
    private String entryHash;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    protected AuditEvent() {}
    public AuditEvent(String eventType, LedgerUser actor, String description, String ipAddress,
                      String status, String prevHash, String entryHash, Instant createdAt) {
        this.eventType = eventType; this.actor = actor; this.description = description;
        this.ipAddress = ipAddress; this.status = status; this.prevHash = prevHash;
        this.entryHash = entryHash; this.createdAt = createdAt;
    }
    public void assignId(Long id) { this.id = id; }
    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public String getDescription() { return description; }
    public String getIpAddress() { return ipAddress; }
    public String getStatus() { return status; }
    public String getPrevHash() { return prevHash; }
    public String getEntryHash() { return entryHash; }
    public Instant getCreatedAt() { return createdAt; }
}
