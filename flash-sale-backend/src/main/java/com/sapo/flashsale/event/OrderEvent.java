package com.sapo.flashsale.event;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent implements Serializable {
    private String userId;
    private String productId;
    private int quantity;
}
