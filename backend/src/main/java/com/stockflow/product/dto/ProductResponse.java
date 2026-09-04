package com.stockflow.product.dto;

import com.stockflow.product.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        String sku,
        String name,
        String description,
        BigDecimal unitPrice,
        int quantityOnHand,
        Instant createdAt,
        Instant updatedAt) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(),
                p.getUnitPrice(), p.getQuantityOnHand(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
