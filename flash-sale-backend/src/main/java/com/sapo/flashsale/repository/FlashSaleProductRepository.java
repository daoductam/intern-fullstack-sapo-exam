package com.sapo.flashsale.repository;

import com.sapo.flashsale.entity.FlashSaleProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface FlashSaleProductRepository extends JpaRepository<FlashSaleProduct, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM FlashSaleProduct p WHERE p.id = :id")
    Optional<FlashSaleProduct> findByIdWithLock(Long id);

    @Query("SELECT p FROM FlashSaleProduct p WHERE p.saleActive = true ORDER BY p.id")
    java.util.List<FlashSaleProduct> findActiveSaleProducts();
}
