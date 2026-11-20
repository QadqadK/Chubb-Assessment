package com.chubb.insights.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "app.kafka.topics-enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    @Bean
    NewTopic claimDeadLetterTopic(@Value("${app.kafka.dlt-topic:claim.dead-letter}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
