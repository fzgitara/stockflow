package com.stockflow.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    /**
     * All queries are scoped by user_id (A7): a user never sees or touches
     * another user's rows. Search matches name OR sku (I2), case-insensitive.
     * Ordering comes from the Pageable's Sort (whitelisted in ProductService).
     */
    Page<Product> findByUserId(UUID userId, Pageable pageable);

    @Query("""
            SELECT p FROM Product p
            WHERE p.userId = :userId
              AND (lower(p.name) LIKE lower(concat('%', :search, '%'))
                OR lower(p.sku) LIKE lower(concat('%', :search, '%')))
            """)
    Page<Product> searchByUser(@Param("userId") UUID userId,
                               @Param("search") String search,
                               Pageable pageable);

    Optional<Product> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndSkuIgnoreCase(UUID userId, String sku);

    /**
     * Atomic stock decrement used when issuing an invoice (V5/V6): the
     * WHERE clause makes overselling impossible at the row level — the
     * update affects 0 rows when stock is insufficient.
     */
    @Modifying
    @Query("""
            UPDATE Product p
            SET p.quantityOnHand = p.quantityOnHand - :qty, p.version = p.version + 1
            WHERE p.id = :productId AND p.userId = :userId AND p.quantityOnHand >= :qty
            """)
    int decrementStock(@Param("productId") UUID productId,
                       @Param("userId") UUID userId,
                       @Param("qty") int qty);

    /** Atomic stock restore used when cancelling an issued invoice (V7). */
    @Modifying
    @Query("""
            UPDATE Product p
            SET p.quantityOnHand = p.quantityOnHand + :qty, p.version = p.version + 1
            WHERE p.id = :productId AND p.userId = :userId
            """)
    int incrementStock(@Param("productId") UUID productId,
                       @Param("userId") UUID userId,
                       @Param("qty") int qty);
}
