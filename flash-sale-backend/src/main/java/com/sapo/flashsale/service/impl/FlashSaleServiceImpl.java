package com.sapo.flashsale.service.impl;

import com.sapo.flashsale.service.FlashSaleService;

import com.sapo.flashsale.dto.OrderRequest;
import com.sapo.flashsale.dto.OrderResponse;
import com.sapo.flashsale.entity.FlashSaleProduct;
import com.sapo.flashsale.entity.Order;
import com.sapo.flashsale.repository.FlashSaleProductRepository;
import com.sapo.flashsale.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import com.sapo.flashsale.producer.KafkaOrderProducer;
import com.sapo.flashsale.event.OrderEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleServiceImpl implements FlashSaleService {
    @Value("${flashsale.max-quantity-per-user}")
    private int maxQuantityPerUser;

    @Value("${flashsale.redis.stock-key-prefix}")
    private String stockKeyPrefix;

    @Value("${flashsale.redis.user-limit-key-prefix}")
    private String userLimitKeyPrefix;

    private final StringRedisTemplate redisTemplate;
    private final FlashSaleProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final TransactionTemplate transactionTemplate;
    private final KafkaOrderProducer kafkaOrderProducer;

    @Override
    public OrderResponse placeOrder(OrderRequest request) {
        log.info("Starting order placement process for UserID: {}, ProductID: {}, Quantity: {}", 
                 request.getUserId(), request.getProductId(), request.getQuantity());
                 
        if (!isValidQuantity(request.getQuantity())) {
            log.warn("Invalid quantity requested: {}", request.getQuantity());
            return buildErrorResponse("Số lượng không hợp lệ", "INVALID_QUANTITY");
        }

        String userLimitKey = userLimitKeyPrefix + request.getUserId() + ":" + request.getProductId();
        String stockKey = stockKeyPrefix + request.getProductId();

        // 1. Check user limit
        log.debug("Checking user purchase limit. Key: {}", userLimitKey);
        if (!checkAndIncrementUserLimit(userLimitKey, request.getQuantity())) {
            log.warn("User {} exceeded purchase limit for product {}", request.getUserId(), request.getProductId());
            return buildErrorResponse("Mỗi khách hàng chỉ được mua tối đa " + maxQuantityPerUser + " sản phẩm", "EXCEED_USER_LIMIT");
        }

        // 2. Deduct stock 
        log.debug("Deducting stock from Redis. Key: {}, Quantity: {}", stockKey, request.getQuantity());
        if (!deductStock(stockKey, request.getQuantity())) {
            log.warn("Out of stock for product {}", request.getProductId());
            rollbackUserLimit(userLimitKey, request.getQuantity());
            return buildErrorResponse("Sản phẩm đã hết hàng", "OUT_OF_STOCK");
        }

        // 3. Publish to Kafka
        try {
            log.debug("Publishing order event to Kafka for UserID: {}", request.getUserId());
            
            OrderEvent event = new OrderEvent(request.getUserId(), request.getProductId(), request.getQuantity());
            kafkaOrderProducer.sendOrderEvent(event);
            
            return OrderResponse.builder()
                .success(true)
                .message("Đặt hàng thành công! Đơn hàng đang được xử lý.")
                // Not returning orderId yet since it's processed async
                .build();
        } catch (Exception e) {
            log.error("Error publishing order event to Kafka for UserID: {}, ProductID: {}. Rolling back Redis operations. Error details: {}", 
                      request.getUserId(), request.getProductId(), e.getMessage(), e);
            rollbackTransaction(stockKey, userLimitKey, request.getQuantity());
            return buildErrorResponse("Có lỗi xảy ra khi gọi Kafka, vui lòng thử lại", "SYSTEM_ERROR");
        }
    }

    private boolean isValidQuantity(Integer quantity) {
        return quantity != null && quantity > 0;
    }

    private boolean checkAndIncrementUserLimit(String userLimitKey, int quantity) {
        Long currentUserCount = redisTemplate.opsForValue().increment(userLimitKey, quantity);
        
        if (currentUserCount != null && currentUserCount == quantity) {
            redisTemplate.expire(userLimitKey, Duration.ofHours(24));
        }

        if (currentUserCount != null && currentUserCount > maxQuantityPerUser) {
            rollbackUserLimit(userLimitKey, quantity);
            return false;
        }
        return true;
    }

    private boolean deductStock(String stockKey, int quantity) {
        Long remainingStock = redisTemplate.opsForValue().decrement(stockKey, quantity);
        if (remainingStock == null || remainingStock < 0) {
            redisTemplate.opsForValue().increment(stockKey, quantity);
            return false;
        }
        return true;
    }

    private void rollbackUserLimit(String userLimitKey, int quantity) {
        redisTemplate.opsForValue().decrement(userLimitKey, quantity);
    }

    private void rollbackTransaction(String stockKey, String userLimitKey, int quantity) {
        redisTemplate.opsForValue().increment(stockKey, quantity);
        redisTemplate.opsForValue().decrement(userLimitKey, quantity);
    }

    private OrderResponse buildErrorResponse(String message, String errorCode) {
        return OrderResponse.builder()
            .success(false)
            .message(message)
            .errorCode(errorCode)
            .build();
    }

    @Override
    public Long createOrder(Long userId, Long productId, Integer quantity) {
        return transactionTemplate.execute(status -> {
            // SELECT FOR UPDATE to prevent race condition at DB level
            FlashSaleProduct product = productRepository.findByIdWithLock(productId)
                .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại ở DB: " + productId));

            Order order = Order.builder()
                .userId(userId)
                .productId(productId)
                .quantity(quantity)
                .salePrice(product.getSalePrice())
                .status("SUCCESS") // Đã trừ stock ở Redis thành công thì lưu DB là SUCCESS
                .build();

            return orderRepository.save(order).getId();
        });
    }

    @Override
    public List<FlashSaleProduct> getActiveProducts() {
        log.debug("Fetching active flash sale products from DB");
        java.util.List<FlashSaleProduct> products = productRepository.findActiveSaleProducts();
        
        log.debug("Enriching {} products with real-time stock from Redis", products.size());
        for (FlashSaleProduct p : products) {
            String stockStr = redisTemplate.opsForValue().get(stockKeyPrefix + p.getId());
            if (stockStr != null) {
                p.setStock(Integer.parseInt(stockStr));
            }
        }
        
        log.info("Successfully fetched and enriched {} active products", products.size());
        return products;
    }
}
