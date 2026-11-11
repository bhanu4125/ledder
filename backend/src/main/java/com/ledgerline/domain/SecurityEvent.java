package com.ledgerline.domain;

import jakarta.persistence.*;
import java.time.Instant;
import org.slf4j.MDC;

@Entity
@Table(name = "security_events")
public class SecurityEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_type", nullable = false, length = 48)
    private String eventType;
    @Column(nullable = false, length = 500)
    private String description;
    @Column(name = "ip_address", length = 64)
    private String ipAddress;
    @Column(name = "correlation_id", length = 36)
    private String correlationId;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    protected SecurityEvent() {}
    public SecurityEvent(String eventType, String description, String ipAddress, String status) {
        this.eventType=eventType; this.description=description; this.ipAddress=ipAddress;
        this.status=status; this.createdAt=Instant.now(); this.correlationId=MDC.get("correlationId");
    }
    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public String getDescription() { return description; }
    public String getIpAddress() { return ipAddress; }
    public String getCorrelationId() { return correlationId; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
