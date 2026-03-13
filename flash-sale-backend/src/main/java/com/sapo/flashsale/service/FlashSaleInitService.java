package com.sapo.flashsale.service;

import com.sapo.flashsale.entity.FlashSaleProduct;
import com.sapo.flashsale.repository.FlashSaleProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlashSaleInitService {
    
    @Value("${flashsale.redis.stock-key-prefix}")
    private String stockKeyPrefix;

    private final FlashSaleProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;

    @Bean
    public ApplicationRunner initFlashSaleStock() {
        return args -> {
            // Mock data into database first (since we use H2 or start fresh)
            if (productRepository.count() == 0) {
                productRepository.saveAll(List.of(
                    FlashSaleProduct.builder().id(1L).name("Sony WH-1000XM5").salePrice(new BigDecimal("4495000")).stock(80).saleActive(true).image("🎧").build(),
                    FlashSaleProduct.builder().id(2L).name("Samsung QLED 55\"").salePrice(new BigDecimal("11000000")).stock(30).saleActive(true).image("📺").build(),
                    FlashSaleProduct.builder().id(3L).name("iPhone 15 Pro 256GB").salePrice(new BigDecimal("14995000")).stock(0).saleActive(true).image("📱").build(),
                    FlashSaleProduct.builder().id(4L).name("Dyson V15 Detect").salePrice(new BigDecimal("8950000")).stock(45).saleActive(true).image("🔧").build()
                ));
            }

            productRepository.findActiveSaleProducts().forEach(product -> {
                String key = stockKeyPrefix + product.getId();
                redisTemplate.opsForValue().set(key, String.valueOf(product.getStock()));
                log.debug("Synced stock for product {}: {}", product.getId(), product.getStock());
            });
            log.info("Flash Sale stock successfully synced to Redis during application startup");
        };
    }
}
