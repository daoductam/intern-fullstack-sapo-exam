package com.sapo.flashsale.service;

import com.sapo.flashsale.dto.OrderRequest;
import com.sapo.flashsale.dto.OrderResponse;
import com.sapo.flashsale.entity.FlashSaleProduct;

import java.util.List;

public interface FlashSaleService {
    OrderResponse placeOrder(OrderRequest request);
    List<FlashSaleProduct> getActiveProducts();
}
