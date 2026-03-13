package com.sapo.flashsale.controller;

import com.sapo.flashsale.dto.OrderRequest;
import com.sapo.flashsale.dto.OrderResponse;
import com.sapo.flashsale.entity.FlashSaleProduct;
import com.sapo.flashsale.service.FlashSaleService;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/flash-sale")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Keep CORS enabled for testing frontend local
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    @PostMapping("/order")
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody OrderRequest request) {
        log.info("Received place order request - UserID: {}, ProductID: {}, Quantity: {}", 
                 request.getUserId(), request.getProductId(), request.getQuantity());
                 
        OrderResponse response = flashSaleService.placeOrder(request);
        
        if (response.isSuccess()) {
            log.info("Order successfully placed - OrderID: {}", response.getOrderId());
            return ResponseEntity.ok(response);
        }
        
        log.warn("Order placement failed for UserID: {} - Reason: {}", request.getUserId(), response.getMessage());
        return ResponseEntity.badRequest().body(response);
    }

    @GetMapping("/products")
    public ResponseEntity<List<FlashSaleProduct>> getActiveProducts() {
        log.info("Received request to fetch active flash sale products");
        List<FlashSaleProduct> products = flashSaleService.getActiveProducts();
        log.info("Successfully fetched {} active flash sale products", products.size());
        return ResponseEntity.ok(products);
    }
}
