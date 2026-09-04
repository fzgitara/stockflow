package com.stockflow.invoice;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    /** Global sequence for human-readable invoice numbers (D5). */
    @Query(value = "SELECT nextval('invoice_number_seq')", nativeQuery = true)
    long nextInvoiceSeqValue();

    /** A7: every listing is scoped to the owning user. */
    Page<Invoice> findByUserId(UUID userId, Pageable pageable);

    Page<Invoice> findByUserIdAndStatus(UUID userId, InvoiceStatus status, Pageable pageable);

    @Query("""
            SELECT i FROM Invoice i
            LEFT JOIN FETCH i.items
            WHERE i.id = :id AND i.user.id = :userId
            """)
    Optional<Invoice> findWithItemsByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    /** Used by the referenced-product delete guard (I4/D3). */
    boolean existsByItemsProductIdAndUserId(UUID productId, UUID userId);
}
