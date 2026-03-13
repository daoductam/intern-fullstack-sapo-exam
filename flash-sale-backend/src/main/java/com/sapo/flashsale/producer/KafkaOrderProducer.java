package com.sapo.flashsale.producer;

import com.sapo.flashsale.config.KafkaTopicConfig;
import com.sapo.flashsale.event.OrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaOrderProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendOrderEvent(OrderEvent event) {
        log.info("Sending OrderEvent to Kafka. UserID: {}, ProductID: {}", event.getUserId(), event.getProductId());
        
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
            KafkaTopicConfig.ORDER_TOPIC, 
            String.valueOf(event.getUserId()), // Using UserID as key for partitioning to ensure order per user
            event
        );

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Successfully pushed order to Kafka partition {} with offset {}", 
                        result.getRecordMetadata().partition(), 
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to push order to Kafka for UserID: {} - Reason: {}", 
                        event.getUserId(), ex.getMessage(), ex);
            }
        });
    }
}
