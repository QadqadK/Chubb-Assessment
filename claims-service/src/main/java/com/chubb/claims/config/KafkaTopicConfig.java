package com.chubb.claims.config;

import com.chubb.claims.event.ClaimEventTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
@ConditionalOnProperty(name = "app.kafka.topics-enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    @Bean
    KafkaAdmin.NewTopics claimEventTopics() {
        NewTopic[] topics = ClaimEventTopics.all().stream()
                .map(name -> TopicBuilder.name(name).partitions(3).replicas(1).build())
                .toArray(NewTopic[]::new);
        return new KafkaAdmin.NewTopics(topics);
    }
}
