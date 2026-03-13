package com.sapo.flashsale.service;

import com.sapo.flashsale.dto.OrderRequest;
import com.sapo.flashsale.dto.OrderResponse;
import com.sapo.flashsale.entity.FlashSaleProduct;
import com.sapo.flashsale.entity.Order;
import com.sapo.flashsale.repository.FlashSaleProductRepository;
import com.sapo.flashsale.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FlashSaleService {
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

    public OrderResponse placeOrder(OrderRequest request) {
        if (!isValidQuantity(request.getQuantity())) {
            return buildErrorResponse("Số lượng không hợp lệ", "INVALID_QUANTITY");
        }

        String userLimitKey = userLimitKeyPrefix + request.getUserId() + ":" + request.getProductId();
        String stockKey = stockKeyPrefix + request.getProductId();

        // 1. Check user limit
        if (!checkAndIncrementUserLimit(userLimitKey, request.getQuantity())) {
            return buildErrorResponse("Mỗi khách hàng chỉ được mua tối đa " + maxQuantityPerUser + " sản phẩm", "EXCEED_USER_LIMIT");
        }

        // 2. Deduct stock 
        if (!deductStock(stockKey, request.getQuantity())) {
            rollbackUserLimit(userLimitKey, request.getQuantity());
            return buildErrorResponse("Sản phẩm đã hết hàng", "OUT_OF_STOCK");
        }

        // 3. Save DB
        try {
            Long orderId = createOrder(request);
            return OrderResponse.builder()
                .success(true)
                .message("Đặt hàng thành công! Cảm ơn bạn đã tham gia Flash Sale")
                .orderId(orderId)
                .build();
        } catch (Exception e) {
            System.err.println("Error saving order! " + e.getMessage());
            e.printStackTrace();
            rollbackTransaction(stockKey, userLimitKey, request.getQuantity());
            return buildErrorResponse("Có lỗi xảy ra, vui lòng thử lại", "SYSTEM_ERROR");
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

    protected Long createOrder(OrderRequest request) {
        return transactionTemplate.execute(status -> {
            // SELECT FOR UPDATE to prevent race condition at DB level
            FlashSaleProduct product = productRepository.findByIdWithLock(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Sản phẩm không tồn tại"));

            Order order = Order.builder()
                .userId(request.getUserId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .salePrice(product.getSalePrice())
                .status("PENDING")
                .build();

            return orderRepository.save(order).getId();
        });
    }

    public List<FlashSaleProduct> getActiveProducts() {
        java.util.List<FlashSaleProduct> products = productRepository.findActiveSaleProducts();
        for (FlashSaleProduct p : products) {
            String stockStr = redisTemplate.opsForValue().get(stockKeyPrefix + p.getId());
            if (stockStr != null) {
                p.setStock(Integer.parseInt(stockStr));
            }
        }
        return products;
    }
}
