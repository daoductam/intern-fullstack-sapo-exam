package com.sapo.flashsale.producer;

import com.sapo.flashsale.event.OrderEvent;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RabbitMQProducer {
    // Mock class for demo purpose
    public void sendToQueue(OrderEvent event) {
        log.info("Successfully pushed order to Queue for async processing: {}", event);
    }
}
