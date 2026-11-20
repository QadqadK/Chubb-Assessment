package com.chubb.insights.messaging;

import com.chubb.insights.event.ClaimEventEnvelope;
import com.chubb.insights.event.ClaimTopics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClaimEventConsumer {

    private final ObjectMapper objectMapper;
    private final ReadModelProjector projector;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.dlt-topic:claim.dead-letter}")
    private String deadLetterTopic;

    @KafkaListener(
            id = "claims-insights",
            groupId = "${spring.kafka.consumer.group-id}",
            topics = {
                    ClaimTopics.CLAIM_SUBMITTED,
                    ClaimTopics.CLAIM_ASSIGNED,
                    ClaimTopics.INFO_REQUESTED,
                    ClaimTopics.INFO_PROVIDED,
                    ClaimTopics.CLAIM_ASSESSED,
                    ClaimTopics.CLAIM_DECIDED,
                    ClaimTopics.CLAIM_SETTLED
            })
    public void onMessage(ConsumerRecord<String, String> record) {
        try {
            ClaimEventEnvelope envelope = objectMapper.readValue(record.value(), ClaimEventEnvelope.class);
            projector.apply(envelope, record.value());
        } catch (Exception ex) {
            log.error("Failed to process {} offset {}; dead-lettering", record.topic(), record.offset(), ex);
            try {
                kafkaTemplate.send(deadLetterTopic, record.key(), record.value()).get(5, TimeUnit.SECONDS);
            } catch (Exception sendFailure) {
                throw new IllegalStateException("Failed to dead-letter claim event", sendFailure);
            }
        }
    }
}
