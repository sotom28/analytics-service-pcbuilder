package com.example.analytics.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Una fila por (tipo de métrica, clave): EVENT_TYPE/ORDER_PAID, CATEGORY/CPU, PRODUCT/<publicationId>,
 * SELLER/<sellerId>, INSPECTION_RESULT/APPROVED... Se actualiza con un upsert atómico (ver MetricRepository).
 */
@Entity
@Table(name = "metric", uniqueConstraints = @UniqueConstraint(columnNames = {"metric_type", "dim_key"}))
public class Metric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "metric_type", nullable = false, length = 30)
    private String metricType;

    @Column(name = "dim_key", nullable = false, length = 100)
    private String dimKey;

    @Column(length = 255)
    private String label;

    @Column(name = "event_count", nullable = false)
    private long eventCount;

    /** Suma de montos en CLP (ventas); 0 en las métricas que solo cuentan. */
    @Column(nullable = false)
    private long amount;

    public Long getId() { return id; }
    public String getMetricType() { return metricType; }
    public String getDimKey() { return dimKey; }
    public String getLabel() { return label; }
    public long getEventCount() { return eventCount; }
    public long getAmount() { return amount; }
}
