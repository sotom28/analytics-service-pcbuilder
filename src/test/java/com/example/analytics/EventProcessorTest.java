package com.example.analytics;

import com.example.analytics.auth.AuthContext;
import com.example.analytics.controller.AnalyticsController;
import com.example.analytics.event.MalformedEventException;
import com.example.analytics.exception.ApiExceptions.ForbiddenException;
import com.example.analytics.exception.ApiExceptions.UnauthorizedException;
import com.example.analytics.model.Metric;
import com.example.analytics.repository.AuditEntryRepository;
import com.example.analytics.repository.MetricRepository;
import com.example.analytics.repository.ProcessedEventRepository;
import com.example.analytics.service.EventProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Consumidores de Kafka probados sin broker: se les entrega el mensaje tal como llegaria al @KafkaListener. */
@SpringBootTest
@ActiveProfiles("test")
class EventProcessorTest {

    @Autowired EventProcessor processor;
    @Autowired MetricRepository metrics;
    @Autowired AuditEntryRepository audit;
    @Autowired ProcessedEventRepository processed;
    @Autowired AnalyticsController controller;

    @BeforeEach
    void clean() {
        metrics.deleteAll();
        audit.deleteAll();
        processed.deleteAll();
    }

    static String event(String id, String type, String payload) {
        return """
                {"eventId":"%s","eventType":"%s","version":1,"occurredAt":"2026-11-12T15:04:05Z",
                 "source":"payments","actorId":"buyer-1","payload":%s}""".formatted(id, type, payload);
    }

    static String orderPaid(String id) {
        return event(id, "ORDER_PAID", """
                {"orderId":"o-1","items":[
                  {"publicationId":"pub-1","sellerId":"seller-1","title":"RTX 4070","category":"GPU","price":450000},
                  {"publicationId":"pub-2","sellerId":"seller-1","title":"Ryzen 5","category":"CPU","price":120000}]}""");
    }

    Metric metric(String type, String key) {
        return metrics.findAll().stream().filter(m -> m.getMetricType().equals(type) && m.getDimKey().equals(key))
                .findFirst().orElseThrow(() -> new AssertionError("no existe " + type + "/" + key));
    }

    @Test
    void unaCompraSumaVentasPorCategoriaProductoYVendedor() {
        processor.processBusiness(orderPaid("e1"));

        assertEquals(1, metric(EventProcessor.EVENT_TYPE, "ORDER_PAID").getEventCount());
        assertEquals(450000, metric(EventProcessor.CATEGORY, "GPU").getAmount());
        assertEquals(120000, metric(EventProcessor.CATEGORY, "CPU").getAmount());
        assertEquals("RTX 4070", metric(EventProcessor.PRODUCT, "pub-1").getLabel());
        var seller = metric(EventProcessor.SELLER, "seller-1");
        assertEquals(2, seller.getEventCount());
        assertEquals(570000, seller.getAmount());
    }

    @Test
    void elMismoEventoEntregadoDosVecesSeCuentaUnaSolaVez() {
        processor.processBusiness(orderPaid("e2"));
        processor.processBusiness(orderPaid("e2")); // Kafka entrega "al menos una vez"

        assertEquals(1, metric(EventProcessor.EVENT_TYPE, "ORDER_PAID").getEventCount());
        assertEquals(450000, metric(EventProcessor.CATEGORY, "GPU").getAmount());
    }

    @Test
    void eventosDistintosSeAcumulan() {
        processor.processBusiness(orderPaid("e3"));
        processor.processBusiness(orderPaid("e4"));
        assertEquals(2, metric(EventProcessor.EVENT_TYPE, "ORDER_PAID").getEventCount());
        assertEquals(900000, metric(EventProcessor.CATEGORY, "GPU").getAmount());
    }

    @Test
    void elResultadoDeLaInspeccionSeLeeDeInspectionStatusOResult() {
        processor.processBusiness(event("i1", "INSPECTION_COMPLETED", "{\"inspectionStatus\":\"PASSED\"}"));
        processor.processBusiness(event("i2", "INSPECTION_COMPLETED", "{\"inspectionStatus\":\"FAILED\"}"));
        processor.processBusiness(event("i3", "INSPECTION_COMPLETED", "{\"result\":\"PASSED\"}"));
        processor.processBusiness(event("i4", "INSPECTION_COMPLETED", "{}"));

        assertEquals(2, metric(EventProcessor.INSPECTION_RESULT, "PASSED").getEventCount());
        assertEquals(1, metric(EventProcessor.INSPECTION_RESULT, "FAILED").getEventCount());
        assertEquals(1, metric(EventProcessor.INSPECTION_RESULT, "SIN_RESULTADO").getEventCount());
    }

    @Test
    void unTipoDesconocidoSoloSeCuentaPorTipo() {
        processor.processBusiness(event("v1", "LISTING_VIEWED", "{\"publicationId\":\"pub-1\"}"));
        assertEquals(1, metric(EventProcessor.EVENT_TYPE, "LISTING_VIEWED").getEventCount());
        assertEquals(1, metrics.count());
    }

    @Test
    void losMensajesMalformadosVanAlDeadLetterSinTocarLasMetricas() {
        assertThrows(MalformedEventException.class, () -> processor.processBusiness("esto no es json"));
        assertThrows(MalformedEventException.class, () -> processor.processBusiness("{\"eventType\":\"ORDER_PAID\"}")); // sin eventId
        assertThrows(MalformedEventException.class, () -> processor.processBusiness("null"));
        assertEquals(0, metrics.count());
        assertEquals(0, processed.count());
    }

    @Test
    void unaCompraSinItemsSeRevierteYNoQuedaMarcadaComoProcesada() {
        String bad = event("bad", "ORDER_PAID", "{\"orderId\":\"o-9\"}");
        assertThrows(MalformedEventException.class, () -> processor.processBusiness(bad));
        assertEquals(0, metrics.count(), "la metrica EVENT_TYPE se revierte junto con el error");
        assertEquals(0, processed.count(), "si fallo, un reintento debe poder procesarlo");
    }

    @Test
    void laAuditoriaGuardaQuienHizoQueYNoDuplica() {
        String raw = event("a1", "INSPECTION_APPROVED", "{\"inspectionId\":\"i-1\"}");
        processor.processAudit(raw);
        processor.processAudit(raw);

        assertEquals(1, audit.count());
        var entry = audit.findAll().get(0);
        assertEquals("INSPECTION_APPROVED", entry.getEventType());
        assertEquals("buyer-1", entry.getActorId());
        assertEquals("payments", entry.getSource());
        assertTrue(entry.getPayload().contains("i-1"));
    }

    @Test
    void cadaGrupoDeduplicaPorSuCuenta() {
        String raw = event("same", "ORDER_PAID", "{\"items\":[]}");
        processor.processBusiness(raw);
        processor.processAudit(raw); // mismo eventId pero otro grupo: se procesa
        assertEquals(1, audit.count());
        assertEquals(2, processed.count());
    }

    @Test
    void laApiDeAnaliticaEsSoloDelAdmin() {
        assertThrows(UnauthorizedException.class, () -> controller.events(null, "WORKSHOP_ADMIN"));
        assertThrows(ForbiddenException.class, () -> controller.categories("u1", "BUYER_SELLER"));
        assertThrows(ForbiddenException.class, () -> controller.audit("u1", null, null, 10));

        processor.processBusiness(orderPaid("api1"));
        var categories = controller.categories("admin", AuthContext.ROLE_WORKSHOP_ADMIN);
        assertEquals("GPU", categories.get(0).key()); // ordenadas por monto
        assertEquals(List.of("pub-1", "pub-2"), controller.products("admin", "WORKSHOP_ADMIN", 10).stream()
                .map(m -> m.key()).toList());
    }

    @Test
    void laCategoriaSeAgrupaPorIdYMuestraElNombre() {
        String item = "{\"publicationId\":\"%s\",\"sellerId\":\"s\",\"title\":\"x\",\"category\":\"%s\",\"categoryId\":\"sc-gpu\",\"price\":%d}";
        processor.processBusiness(event("c1", "ORDER_PAID", "{\"items\":[" + item.formatted("p1", "Graphics Cards", 400000) + "]}"));
        // misma subcategoria renombrada despues: sigue siendo la misma fila
        processor.processBusiness(event("c2", "ORDER_PAID", "{\"items\":[" + item.formatted("p2", "Tarjetas graficas", 100000) + "]}"));

        var gpu = metric(EventProcessor.CATEGORY, "sc-gpu");
        assertEquals(500000, gpu.getAmount());
        assertEquals(2, gpu.getEventCount());
        assertEquals("Tarjetas graficas", gpu.getLabel());
    }
}
