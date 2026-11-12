package com.example.analytics.service;

import com.example.analytics.event.EventEnvelope;
import com.example.analytics.event.MalformedEventException;
import com.example.analytics.model.AuditEntry;
import com.example.analytics.model.ProcessedEvent;
import com.example.analytics.repository.AuditEntryRepository;
import com.example.analytics.repository.MetricRepository;
import com.example.analytics.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Convierte cada evento en métricas (grupo analytics-aggregator) o en un registro de auditoría (grupo audit-writer).
 * Es idempotente: si el mismo eventId llega dos veces para un grupo, la segunda vez no hace nada.
 */
@Service
public class EventProcessor {

    private static final Logger log = LoggerFactory.getLogger(EventProcessor.class);

    public static final String GROUP_AGGREGATOR = "analytics-aggregator";
    public static final String GROUP_AUDIT = "audit-writer";

    public static final String EVENT_TYPE = "EVENT_TYPE";
    public static final String CATEGORY = "CATEGORY";
    public static final String PRODUCT = "PRODUCT";
    public static final String SELLER = "SELLER";
    public static final String INSPECTION_RESULT = "INSPECTION_RESULT";

    private final JsonMapper json;
    private final MetricRepository metrics;
    private final ProcessedEventRepository processed;
    private final AuditEntryRepository audit;

    public EventProcessor(JsonMapper json, MetricRepository metrics, ProcessedEventRepository processed,
                          AuditEntryRepository audit) {
        this.json = json;
        this.metrics = metrics;
        this.processed = processed;
        this.audit = audit;
    }

    /** Lee el mensaje; si no es JSON válido o le faltan campos obligatorios lanza MalformedEventException (va al DLT). */
    public EventEnvelope parse(String raw) {
        EventEnvelope event;
        try {
            event = json.readValue(raw, EventEnvelope.class);
        } catch (JacksonException e) {
            throw new MalformedEventException("El mensaje no es un evento JSON valido", e);
        }
        if (event == null || blank(event.eventId()) || blank(event.eventType())) {
            throw new MalformedEventException("El evento no trae eventId o eventType");
        }
        return event;
    }

    @Transactional
    public void processBusiness(String raw) {
        EventEnvelope e = parse(raw);
        if (alreadyProcessed(GROUP_AGGREGATOR, e)) {
            return;
        }
        metrics.add(EVENT_TYPE, e.eventType(), e.eventType(), 1, 0);
        JsonNode p = e.payload();
        switch (e.eventType()) {
            case "ORDER_PAID" -> orderPaid(p);
            case "INSPECTION_COMPLETED" -> {
                // reports manda el resultado como inspectionStatus (PASSED / FAILED); se acepta tambien "result".
                String result = text(p, "result", text(p, "inspectionStatus", "SIN_RESULTADO"));
                metrics.add(INSPECTION_RESULT, result, result, 1, 0);
            }
            default -> { /* el resto de tipos solo se cuenta por EVENT_TYPE */ }
        }
    }

    @Transactional
    public void processAudit(String raw) {
        EventEnvelope e = parse(raw);
        if (alreadyProcessed(GROUP_AUDIT, e)) {
            return;
        }
        audit.save(new AuditEntry(e.eventId(), e.eventType(), e.source(), e.actorId(), e.occurredAt(),
                e.payload() == null ? null : e.payload().toString()));
    }

    private void orderPaid(JsonNode payload) {
        if (payload == null || !payload.path("items").isArray()) {
            throw new MalformedEventException("ORDER_PAID sin items");
        }
        for (JsonNode item : payload.path("items")) {
            long price = item.path("price").asLong(0);
            String category = text(item, "category", "SIN_CATEGORIA");
            // La clave es el id de la subcategoria (si viene): asi un renombre no parte las ventas en dos filas.
            String categoryKey = text(item, "categoryId", category);
            String publicationId = text(item, "publicationId", null);
            String sellerId = text(item, "sellerId", null);
            metrics.add(CATEGORY, categoryKey, category, 1, price);
            if (publicationId != null) {
                metrics.add(PRODUCT, publicationId, text(item, "title", publicationId), 1, price);
            }
            if (sellerId != null) {
                metrics.add(SELLER, sellerId, sellerId, 1, price);
            }
        }
    }

    private boolean alreadyProcessed(String group, EventEnvelope e) {
        String id = group + ":" + e.eventId();
        if (processed.existsById(id)) {
            log.info("Evento {} ya procesado por {}: se ignora", e.eventId(), group);
            return true;
        }
        processed.save(new ProcessedEvent(id));
        return false;
    }

    private static String text(JsonNode node, String field, String fallback) {
        if (node == null) {
            return fallback;
        }
        String v = node.path(field).asString("");
        return v.isBlank() ? fallback : v;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
