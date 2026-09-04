package com.stockflow.invoice;

import com.stockflow.config.AppProperties;
import com.stockflow.common.ApiException;
import com.stockflow.common.PageResponse;
import com.stockflow.invoice.dto.InvoiceItemRequest;
import com.stockflow.invoice.dto.InvoiceRequest;
import com.stockflow.invoice.dto.InvoiceResponse;
import com.stockflow.product.Product;
import com.stockflow.product.ProductRepository;
import com.stockflow.user.User;
import com.stockflow.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InvoiceService {

    /** V8: allowed transitions; anything else is a 409. */
    private static final Map<InvoiceStatus, List<InvoiceStatus>> ALLOWED_TRANSITIONS = Map.of(
            InvoiceStatus.DRAFT, List.of(InvoiceStatus.ISSUED, InvoiceStatus.CANCELLED),
            InvoiceStatus.ISSUED, List.of(InvoiceStatus.PAID, InvoiceStatus.CANCELLED),
            InvoiceStatus.PAID, List.of(),
            InvoiceStatus.CANCELLED, List.of());

    private final InvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final java.math.BigDecimal taxRate;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          ProductRepository productRepository,
                          UserRepository userRepository,
                          AppProperties properties) {
        this.invoiceRepository = invoiceRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.taxRate = properties.taxRateAsDecimal();
    }

    // --- Queries ------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> list(UUID userId, InvoiceStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Invoice> result = (status == null)
                ? invoiceRepository.findByUserId(userId, pageable)
                : invoiceRepository.findByUserIdAndStatus(userId, status, pageable);
        return PageResponse.from(result.map(InvoiceResponse::from));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID userId, UUID id) {
        return InvoiceResponse.from(findOwnedWithItems(userId, id));
    }

    // --- Commands -----------------------------------------------------------

    /**
     * V1/V2/V4: creates a DRAFT invoice. Totals are computed server-side and
     * product name/price are snapshotted onto each line. Stock is NOT touched
     * until the invoice is issued (V6/V7).
     */
    @Transactional
    public InvoiceResponse create(UUID userId, InvoiceRequest request) {
        validateDates(request);
        User user = userRepository.getReferenceById(userId);

        Invoice invoice = new Invoice(user, nextInvoiceNumber(), request.customerName().trim(),
                request.issueDate(), request.dueDate(), blankToNull(request.notes()));
        applyItems(userId, invoice, request.items());
        invoice.recalculateTotals(taxRate);
        return InvoiceResponse.from(invoiceRepository.save(invoice));
    }

    /** V9/D7: only DRAFT invoices may be edited; items are replaced wholesale. */
    @Transactional
    public InvoiceResponse update(UUID userId, UUID id, InvoiceRequest request) {
        validateDates(request);
        Invoice invoice = findOwnedWithItems(userId, id);
        requireTransition(invoice, null, "edit");
        invoice.clearItems();
        applyItems(userId, invoice, request.items());
        invoice.setCustomerName(request.customerName().trim());
        invoice.setIssueDate(request.issueDate());
        invoice.setDueDate(request.dueDate());
        invoice.setNotes(blankToNull(request.notes()));
        invoice.recalculateTotals(taxRate);
        return InvoiceResponse.from(invoiceRepository.save(invoice));
    }

    /** V6: DRAFT -> ISSUED with atomic, all-or-nothing stock decrement. */
    @Transactional
    public InvoiceResponse issue(UUID userId, UUID id) {
        Invoice invoice = findOwnedWithItems(userId, id);
        requireTransition(invoice, InvoiceStatus.ISSUED, "issue");

        for (InvoiceItem item : invoice.getItems()) {
            int updated = productRepository.decrementStock(item.getProductId(), userId, item.getQuantity());
            if (updated == 0) {
                // Row-level guard tripped: not enough stock (or product gone).
                // Throwing rolls back every decrement already applied (V6 atomicity).
                Product product = productRepository.findByIdAndUserId(item.getProductId(), userId).orElse(null);
                String available = product != null ? String.valueOf(product.getQuantityOnHand()) : "0";
                throw ApiException.conflict("Insufficient stock for %s: requested %d, available %s"
                        .formatted(product != null ? product.getName() : item.getProductName(),
                                item.getQuantity(), available));
            }
        }
        invoice.setStatus(InvoiceStatus.ISSUED);
        return InvoiceResponse.from(invoiceRepository.save(invoice));
    }

    /** V8: ISSUED -> PAID. */
    @Transactional
    public InvoiceResponse pay(UUID userId, UUID id) {
        Invoice invoice = findOwnedWithItems(userId, id);
        requireTransition(invoice, InvoiceStatus.PAID, "mark as paid");
        invoice.setStatus(InvoiceStatus.PAID);
        return InvoiceResponse.from(invoiceRepository.save(invoice));
    }

    /**
     * V7/V8: cancelling an ISSUED invoice restores the stock it consumed;
     * cancelling a DRAFT restores nothing. Terminal states are rejected.
     */
    @Transactional
    public InvoiceResponse cancel(UUID userId, UUID id) {
        Invoice invoice = findOwnedWithItems(userId, id);
        requireTransition(invoice, InvoiceStatus.CANCELLED, "cancel");

        if (invoice.getStatus() == InvoiceStatus.ISSUED) {
            for (InvoiceItem item : invoice.getItems()) {
                productRepository.incrementStock(item.getProductId(), userId, item.getQuantity());
            }
        }
        invoice.setStatus(InvoiceStatus.CANCELLED);
        return InvoiceResponse.from(invoiceRepository.save(invoice));
    }

    // --- Internals ----------------------------------------------------------

    private void applyItems(UUID userId, Invoice invoice, List<InvoiceItemRequest> items) {
        List<UUID> productIds = items.stream().map(InvoiceItemRequest::productId).toList();
        Map<UUID, Product> products = productRepository.findAllById(productIds).stream()
                .filter(p -> p.getUserId().equals(userId))
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // V5: a line may not exceed available stock. Aggregate first so two
        // lines for the same product are judged by their combined quantity.
        Map<UUID, Integer> requestedQty = items.stream().collect(
                Collectors.groupingBy(InvoiceItemRequest::productId,
                        Collectors.summingInt(InvoiceItemRequest::quantity)));

        for (Map.Entry<UUID, Integer> entry : requestedQty.entrySet()) {
            Product product = products.get(entry.getKey());
            if (product == null) {
                throw ApiException.badRequest("Product not found: " + entry.getKey());
            }
            if (entry.getValue() > product.getQuantityOnHand()) {
                throw ApiException.conflict(
                        "Insufficient stock for %s: requested %d, available %d".formatted(
                                product.getName(), entry.getValue(), product.getQuantityOnHand()));
            }
        }

        for (InvoiceItemRequest itemRequest : items) {
            Product product = products.get(itemRequest.productId());
            invoice.addItem(new InvoiceItem(product.getId(), product.getName(),
                    product.getUnitPrice(), itemRequest.quantity()));
        }
    }

    private void requireTransition(Invoice invoice, InvoiceStatus target, String action) {
        InvoiceStatus current = invoice.getStatus();
        boolean allowed = (target == null)
                ? current == InvoiceStatus.DRAFT // editing is only ever allowed on DRAFT (V9)
                : ALLOWED_TRANSITIONS.get(current).contains(target);
        if (!allowed) {
            throw ApiException.conflict(
                    "Cannot %s invoice in status %s%s".formatted(action, current,
                            target != null ? " (to " + target + ")" : ""));
        }
    }

    private void validateDates(InvoiceRequest request) {
        if (request.dueDate().isBefore(request.issueDate())) {
            throw ApiException.badRequest("Validation failed", "dueDate", "Due date cannot be before issue date");
        }
    }

    private String nextInvoiceNumber() {
        long seq = invoiceRepository.nextInvoiceSeqValue();
        return "INV-%d-%06d".formatted(Year.now().getValue(), seq);
    }

    private Invoice findOwnedWithItems(UUID userId, UUID id) {
        return invoiceRepository.findWithItemsByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Invoice not found"));
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
