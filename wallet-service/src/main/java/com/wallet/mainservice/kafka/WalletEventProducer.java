package com.wallet.mainservice.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class WalletEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Retryable(
            value = { KafkaException.class, Exception.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public void publish(String topic, String key, String json) {
        try {
            var result = kafkaTemplate.send(topic, key, json).get(5, TimeUnit.SECONDS); // synchronous
            var meta = result.getRecordMetadata();
            log.info("Sent event to topic={}, partition={}, offset={}",
                    meta.topic(), meta.partition(), meta.offset());
        } catch (Exception e) {
            log.error("Failed to send event to topic={} with key={}. Retrying... cause={}",
                    topic, key, e.getMessage(), e);
            throw new KafkaException("Kafka send failed", e);
        }
    }
}
