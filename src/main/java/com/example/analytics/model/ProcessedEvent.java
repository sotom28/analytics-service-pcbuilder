package com.example.analytics.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Eventos ya procesados, por grupo de consumidores. Kafka entrega "al menos una vez": esto evita contar dos veces. */
@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

    /** "<grupo>:<eventId>" */
    @Id
    @Column(length = 150)
    private String id;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    protected ProcessedEvent() {
    }

    public ProcessedEvent(String id) {
        this.id = id;
    }

    public String getId() { return id; }
    public Instant getProcessedAt() { return processedAt; }
}
