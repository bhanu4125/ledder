package com.ledgerline.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "idempotency_keys", uniqueConstraints = @UniqueConstraint(columnNames = {"request_key", "endpoint"}))
public class IdempotencyRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "request_key", nullable = false, length = 100)
    private String requestKey;
    @Column(nullable = false, length = 160)
    private String endpoint;
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
    @Column(name = "response_body", nullable = false, columnDefinition = "text")
    private String responseBody;
    @Column(name = "response_status", nullable = false)
    private int responseStatus;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    protected IdempotencyRecord() {}
    public IdempotencyRecord(String key, String endpoint, String hash, String body, int status) {
        this.requestKey = key; this.endpoint = endpoint; this.requestHash = hash;
        this.responseBody = body; this.responseStatus = status; this.createdAt = Instant.now();
    }
    public String getRequestHash() { return requestHash; }
    public String getResponseBody() { return responseBody; }
    public int getResponseStatus() { return responseStatus; }
}
