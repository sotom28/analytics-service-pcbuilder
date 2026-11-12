package com.example.analytics.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/** Registro de auditoría: quién hizo qué (un evento de pcb.audit.events). */
@Entity
@Table(name = "audit_entry", indexes = {
        @Index(name = "idx_audit_actor", columnList = "actor_id"),
        @Index(name = "idx_audit_occurred", columnList = "occurred_at")})
public class AuditEntry {

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(length = 50)
    private String source;

    @Column(name = "actor_id", length = 100)
    private String actorId;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    protected AuditEntry() {
    }

    public AuditEntry(String eventId, String eventType, String source, String actorId, Instant occurredAt,
                      String payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.source = source;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
        this.payload = payload;
    }

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public String getSource() { return source; }
    public String getActorId() { return actorId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getPayload() { return payload; }
    public Instant getReceivedAt() { return receivedAt; }
}
