package com.sapo.flashsale.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderResponse {
    private boolean success;
    private String message;
    private Long orderId;
    private String errorCode;
}
