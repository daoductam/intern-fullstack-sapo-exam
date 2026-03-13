package com.sapo.flashsale.controller;

import com.sapo.flashsale.dto.OrderRequest;
import com.sapo.flashsale.dto.OrderResponse;
import com.sapo.flashsale.service.FlashSaleService;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/flash-sale")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Keep CORS enabled for testing frontend local
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    @PostMapping("/order")
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody OrderRequest request) {
        OrderResponse response = flashSaleService.placeOrder(request);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }

    @GetMapping("/products")
    public ResponseEntity<List<com.sapo.flashsale.entity.FlashSaleProduct>> getActiveProducts() {
        return ResponseEntity.ok(flashSaleService.getActiveProducts());
    }
}
