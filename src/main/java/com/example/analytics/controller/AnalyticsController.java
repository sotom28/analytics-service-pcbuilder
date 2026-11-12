package com.example.analytics.controller;

import com.example.analytics.auth.AuthContext;
import com.example.analytics.model.AuditEntry;
import com.example.analytics.model.Metric;
import com.example.analytics.repository.AuditEntryRepository;
import com.example.analytics.repository.MetricRepository;
import com.example.analytics.service.EventProcessor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Métricas y auditoría para el taller. Todas las rutas exigen X-User-Id y X-User-Role=WORKSHOP_ADMIN. */
@RestController
@RequestMapping("/api/v1")
public class AnalyticsController {

    public record MetricDto(String key, String label, long count, long amount) {
        static MetricDto of(Metric m) {
            return new MetricDto(m.getDimKey(), m.getLabel(), m.getEventCount(), m.getAmount());
        }
    }

    public record AuditDto(String eventId, String eventType, String source, String actorId, Instant occurredAt,
                           String payload) {
        static AuditDto of(AuditEntry a) {
            return new AuditDto(a.getEventId(), a.getEventType(), a.getSource(), a.getActorId(), a.getOccurredAt(),
                    a.getPayload());
        }
    }

    private final MetricRepository metrics;
    private final AuditEntryRepository audit;

    public AnalyticsController(MetricRepository metrics, AuditEntryRepository audit) {
        this.metrics = metrics;
        this.audit = audit;
    }

    /** Cantidad de eventos por tipo (sirve para el embudo vistas -> compras). */
    @GetMapping("/analytics/events")
    public List<MetricDto> events(@RequestHeader(value = AuthContext.USER_ID_HEADER, required = false) String userId,
                                  @RequestHeader(value = AuthContext.ROLE_HEADER, required = false) String role) {
        AuthContext.requireAdmin(userId, role);
        return byCount(EventProcessor.EVENT_TYPE, 100);
    }

    @GetMapping("/analytics/categories")
    public List<MetricDto> categories(@RequestHeader(value = AuthContext.USER_ID_HEADER, required = false) String userId,
                                      @RequestHeader(value = AuthContext.ROLE_HEADER, required = false) String role) {
        AuthContext.requireAdmin(userId, role);
        return byAmount(EventProcessor.CATEGORY, 100);
    }

    @GetMapping("/analytics/products")
    public List<MetricDto> products(@RequestHeader(value = AuthContext.USER_ID_HEADER, required = false) String userId,
                                    @RequestHeader(value = AuthContext.ROLE_HEADER, required = false) String role,
                                    @RequestParam(defaultValue = "10") int limit) {
        AuthContext.requireAdmin(userId, role);
        return byAmount(EventProcessor.PRODUCT, limit);
    }

    @GetMapping("/analytics/sellers")
    public List<MetricDto> sellers(@RequestHeader(value = AuthContext.USER_ID_HEADER, required = false) String userId,
                                   @RequestHeader(value = AuthContext.ROLE_HEADER, required = false) String role,
                                   @RequestParam(defaultValue = "10") int limit) {
        AuthContext.requireAdmin(userId, role);
        return byAmount(EventProcessor.SELLER, limit);
    }

    /** Resultado de las inspecciones (APPROVED / REJECTED): da la tasa de rechazo. */
    @GetMapping("/analytics/inspections")
    public List<MetricDto> inspections(@RequestHeader(value = AuthContext.USER_ID_HEADER, required = false) String userId,
                                       @RequestHeader(value = AuthContext.ROLE_HEADER, required = false) String role) {
        AuthContext.requireAdmin(userId, role);
        return byCount(EventProcessor.INSPECTION_RESULT, 100);
    }

    @GetMapping("/audit")
    public List<AuditDto> audit(@RequestHeader(value = AuthContext.USER_ID_HEADER, required = false) String userId,
                                @RequestHeader(value = AuthContext.ROLE_HEADER, required = false) String role,
                                @RequestParam(required = false) String actorId,
                                @RequestParam(defaultValue = "50") int limit) {
        AuthContext.requireAdmin(userId, role);
        PageRequest page = PageRequest.of(0, clamp(limit));
        var rows = actorId == null || actorId.isBlank()
                ? audit.findAllByOrderByOccurredAtDesc(page)
                : audit.findByActorIdOrderByOccurredAtDesc(actorId, page);
        return rows.stream().map(AuditDto::of).toList();
    }

    private List<MetricDto> byAmount(String type, int limit) {
        return metrics.topByAmount(type, PageRequest.of(0, clamp(limit))).stream().map(MetricDto::of).toList();
    }

    private List<MetricDto> byCount(String type, int limit) {
        return metrics.topByCount(type, PageRequest.of(0, clamp(limit))).stream().map(MetricDto::of).toList();
    }

    private static int clamp(int limit) {
        return Math.min(Math.max(limit, 1), 200);
    }
}
