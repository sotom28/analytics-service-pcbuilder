package com.example.analytics.config;

import com.example.analytics.event.MalformedEventException;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    /**
     * Reintenta con espera creciente y, agotados los intentos, publica el mensaje original en <tópico>.dlt
     * (misma partición) con la excepción en los headers. Los mensajes malformados van directo, sin reintentos.
     */
    @Bean
    CommonErrorHandler errorHandler(KafkaOperations<Object, Object> template,
                                    @Value("${analytics.retry.max-attempts:3}") int maxAttempts,
                                    @Value("${analytics.retry.initial-interval-ms:1000}") long initialInterval) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> {
                    log.error("Mensaje {}-{}@{} al dead-letter {}.dlt: {}", record.topic(), record.partition(),
                            record.offset(), record.topic(), ex.getMessage());
                    return new TopicPartition(record.topic() + ".dlt", record.partition());
                });
        // maxAttempts incluye el primer intento: con 3 son 2 reintentos.
        ExponentialBackOff backOff = new ExponentialBackOff(initialInterval, 2.0);
        backOff.setMaxAttempts(Math.max(maxAttempts - 1, 0));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(MalformedEventException.class);
        return handler;
    }
}
