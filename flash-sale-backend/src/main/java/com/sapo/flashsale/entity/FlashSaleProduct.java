package com.sapo.flashsale.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "flash_sale_products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleProduct {
    @Id
    private Long id;
    private String name;
    private BigDecimal salePrice;
    private int stock;
    private boolean saleActive;
    private String image;
}
