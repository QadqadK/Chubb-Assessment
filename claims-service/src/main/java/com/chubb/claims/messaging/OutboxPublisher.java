package com.chubb.claims.messaging;

import com.chubb.claims.domain.OutboxEvent;
import com.chubb.claims.event.ClaimEventTopics;
import com.chubb.claims.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Polls unpublished outbox rows and publishes them to Kafka, keyed by claim id.
 * Ordering within a claim is preserved: the batch stops at the first failure and is retried.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.outbox.publisher-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private final OutboxEventRepository outbox;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.outbox.batch-size:100}")
    private int batchSize;

    @Scheduled(
            fixedDelayString = "${app.outbox.poll-interval-ms:1000}",
            initialDelayString = "${app.outbox.initial-delay-ms:2000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = outbox.findByPublishedAtIsNullOrderByCreatedAtAsc(
                PageRequest.of(0, Math.max(batchSize, 1)));
        if (batch.isEmpty()) {
            return;
        }

        for (OutboxEvent row : batch) {
            String topic = ClaimEventTopics.topicFor(row.getEventType());
            if (topic == null) {
                log.error("No topic mapped for outbox event type {} (id={})", row.getEventType(), row.getId());
                break;
            }
            try {
                kafkaTemplate.send(topic, row.getAggregateId().toString(), row.getPayload())
                        .get(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while publishing outbox event {}; will retry", row.getId());
                break;
            } catch (ExecutionException | TimeoutException ex) {
                log.warn("Failed to publish outbox event {} to {}; will retry", row.getId(), topic, ex);
                break;
            }

            row.setPublishedAt(Instant.now());
            outbox.save(row);
        }
    }
}
