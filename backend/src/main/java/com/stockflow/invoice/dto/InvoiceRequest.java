package com.stockflow.invoice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record InvoiceRequest(
        @NotBlank(message = "Customer name is required")
        @Size(max = 255, message = "Customer name must be at most 255 characters")
        String customerName,

        @NotNull(message = "Issue date is required")
        LocalDate issueDate,

        @NotNull(message = "Due date is required")
        LocalDate dueDate,

        @Size(max = 2000, message = "Notes must be at most 2000 characters")
        String notes,

        @Valid
        @NotNull(message = "At least one line item is required")
        List<InvoiceItemRequest> items) {
}
