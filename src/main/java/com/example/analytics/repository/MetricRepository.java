package com.example.analytics.repository;

import com.example.analytics.model.Metric;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MetricRepository extends JpaRepository<Metric, Long> {

    /**
     * Upsert atómico (MariaDB): crea la fila o suma a la existente. Evita la carrera entre los hilos del consumidor
     * que procesan particiones distintas y tocan la misma métrica.
     */
    @Modifying
    @Query(value = "INSERT INTO metric (metric_type, dim_key, label, event_count, amount) "
            + "VALUES (:type, :key, :label, :count, :amount) "
            + "ON DUPLICATE KEY UPDATE event_count = event_count + VALUES(event_count), "
            + "amount = amount + VALUES(amount), label = COALESCE(VALUES(label), label)", nativeQuery = true)
    void add(@Param("type") String type, @Param("key") String key, @Param("label") String label,
             @Param("count") long count, @Param("amount") long amount);

    @Query("select m from Metric m where m.metricType = :type order by m.amount desc, m.eventCount desc, m.dimKey")
    List<Metric> topByAmount(@Param("type") String type, Pageable pageable);

    @Query("select m from Metric m where m.metricType = :type order by m.eventCount desc, m.dimKey")
    List<Metric> topByCount(@Param("type") String type, Pageable pageable);
}
