package com.example.analytics.event;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Sobre común de todos los eventos (ver kafka-local/EVENTOS.md). El payload es específico de cada tipo y
 * por eso se deja como JSON; no lleva datos personales (el comprador va como id, nunca el correo).
 */
public record EventEnvelope(
        String eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String source,
        String actorId,
        JsonNode payload) {
}
