package com.stockflow.invoice;

/**
 * Invoice lifecycle. Allowed transitions (enforced in InvoiceService):
 * DRAFT -> ISSUED, DRAFT -> CANCELLED, ISSUED -> PAID, ISSUED -> CANCELLED.
 * PAID and CANCELLED are terminal.
 */
public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PAID,
    CANCELLED
}
