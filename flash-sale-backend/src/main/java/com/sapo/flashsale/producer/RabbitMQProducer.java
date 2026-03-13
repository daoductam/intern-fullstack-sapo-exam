package com.sapo.flashsale.producer;

import com.sapo.flashsale.event.OrderEvent;
import org.springframework.stereotype.Service;

@Service
public class RabbitMQProducer {
    // Mock class for demo purpose
    public void sendToQueue(OrderEvent event) {
        System.out.println("Đã gửi đơn hàng vào Queue để xử lý bất đồng bộ: " + event.toString());
    }
}
