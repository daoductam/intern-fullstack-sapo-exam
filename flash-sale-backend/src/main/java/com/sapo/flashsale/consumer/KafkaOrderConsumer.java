package com.sapo.flashsale.consumer;

import com.sapo.flashsale.config.KafkaTopicConfig;
import com.sapo.flashsale.event.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

import com.sapo.flashsale.service.FlashSaleService;
import com.sapo.flashsale.entity.FailedEvent;
import com.sapo.flashsale.repository.FailedEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaOrderConsumer {
    
    private final FlashSaleService flashSaleService;
    private final FailedEventRepository failedEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * @RetryableTopic enables automatic retries. 
     * If all attempts fail, the message is sent to a Dead Letter Topic (DLT) automatically.
     * by default it generates a topic named: original-topic-dlt
     */
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0),
        autoCreateTopics = "true"
    )
    @KafkaListener(topics = KafkaTopicConfig.ORDER_TOPIC, groupId = "flashsale-group")
    public void consumeOrder(OrderEvent event, 
                             @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                             @Header(KafkaHeaders.OFFSET) long offset) {
        log.info("Received OrderEvent from Topic: {}, Offset: {}. Event Data: {}", topic, offset, event);
        
        // Simulating processing logic. Here you would normally update the DB order status or notify users.
        try {
            processOrder(event);
            log.info("Successfully processed OrderEvent for User: {}, Product: {}", event.getUserId(), event.getProductId());
        } catch (Exception e) {
            log.error("Error processing OrderEvent for User: {}. Will be retried.", event.getUserId(), e);
            throw e; // Throwing exception triggers the retry mechanism
        }
    }

    private void processOrder(OrderEvent event) {
        log.debug("Executing database transaction via FlashSaleService for UserID: {}, ProductID: {}", event.getUserId(), event.getProductId());
        
        Long orderId = flashSaleService.createOrder(event.getUserId(), event.getProductId(), event.getQuantity());
        
        log.info("Order created successfully in DB with OrderID: {}", orderId);
    }

    /**
     * This handler picks up messages that failed all retry attempts and ended up in the DLT.
     */
    @DltHandler
    public void handleDltMessage(OrderEvent event, 
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 Exception e) {
        log.error("==== DEAD LETTER TOPIC (DLT) ALERT ====");
        log.error("Failed to process event from topic '{}' after max retries.", topic);
        log.error("Event dropped to DLT: {}", event);
        log.error("Final Error Cause: {}", e.getMessage());
        
        try {
            String payload = objectMapper.writeValueAsString(event);
            FailedEvent failedEvent = FailedEvent.builder()
                    .topic(topic)
                    .payload(payload)
                    .errorMessage(e.getMessage() != null ? e.getMessage() : e.toString())
                    .createdAt(LocalDateTime.now())
                    .status("PENDING")
                    .build();
            
            failedEventRepository.save(failedEvent);
            log.info("Successfully persisted failed event to DB for manual retry later. FailedEvent ID: {}", failedEvent.getId());
        } catch (Exception ex) {
            log.error("CRITICAL: Failed to persist DLT event to database!", ex);
        }
        
        log.error("Action Required: Manual intervention to resolve failed order for User {}", event.getUserId());
        log.error("========================================");
    }
}
