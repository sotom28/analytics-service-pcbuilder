package com.example.analytics.messaging;

import com.example.analytics.service.EventProcessor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Dos consumer groups que leen tópicos distintos. Si falla el procesamiento, el error handler (KafkaConfig)
 * reintenta y al final manda el mensaje al <tópico>.dlt.
 */
@Component
public class EventListeners {

    private final EventProcessor processor;

    public EventListeners(EventProcessor processor) {
        this.processor = processor;
    }

    @KafkaListener(topics = "${analytics.topics.business}", groupId = EventProcessor.GROUP_AGGREGATOR)
    public void onBusinessEvent(String message) {
        processor.processBusiness(message);
    }

    @KafkaListener(topics = "${analytics.topics.audit}", groupId = EventProcessor.GROUP_AUDIT)
    public void onAuditEvent(String message) {
        processor.processAudit(message);
    }
}
