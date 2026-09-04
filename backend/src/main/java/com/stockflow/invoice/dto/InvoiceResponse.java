package com.stockflow.invoice.dto;

import com.stockflow.invoice.Invoice;
import com.stockflow.invoice.InvoiceItem;
import com.stockflow.invoice.InvoiceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        String invoiceNumber,
        String customerName,
        LocalDate issueDate,
        LocalDate dueDate,
        InvoiceStatus status,
        String notes,
        BigDecimal subtotal,
        BigDecimal taxAmount,
        BigDecimal total,
        List<ItemResponse> items) {

    public record ItemResponse(
            UUID id,
            UUID productId,
            String productName,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal) {

        public static ItemResponse from(InvoiceItem item) {
            return new ItemResponse(item.getId(), item.getProductId(), item.getProductName(),
                    item.getUnitPrice(), item.getQuantity(), item.getLineTotal());
        }
    }

    public static InvoiceResponse from(Invoice invoice) {
        List<ItemResponse> items = invoice.getItems().stream()
                .map(ItemResponse::from)
                .toList();
        return new InvoiceResponse(invoice.getId(), invoice.getInvoiceNumber(), invoice.getCustomerName(),
                invoice.getIssueDate(), invoice.getDueDate(), invoice.getStatus(), invoice.getNotes(),
                invoice.getSubtotal(), invoice.getTaxAmount(), invoice.getTotal(), items);
    }
}
