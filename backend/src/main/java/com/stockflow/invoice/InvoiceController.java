package com.stockflow.invoice;

import com.stockflow.auth.JwtAuthFilter.AuthUser;
import com.stockflow.common.PageResponse;
import com.stockflow.invoice.dto.InvoiceRequest;
import com.stockflow.invoice.dto.InvoiceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @GetMapping
    public PageResponse<InvoiceResponse> list(@AuthenticationPrincipal AuthUser user,
                                              @RequestParam(required = false) InvoiceStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return invoiceService.list(user.userId(), status, page, size);
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return invoiceService.get(user.userId(), id);
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> create(@AuthenticationPrincipal AuthUser user,
                                                  @Valid @RequestBody InvoiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invoiceService.create(user.userId(), request));
    }

    @PutMapping("/{id}")
    public InvoiceResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id,
                                  @Valid @RequestBody InvoiceRequest request) {
        return invoiceService.update(user.userId(), id, request);
    }

    @PostMapping("/{id}/issue")
    public InvoiceResponse issue(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return invoiceService.issue(user.userId(), id);
    }

    @PostMapping("/{id}/pay")
    public InvoiceResponse pay(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return invoiceService.pay(user.userId(), id);
    }

    @PostMapping("/{id}/cancel")
    public InvoiceResponse cancel(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return invoiceService.cancel(user.userId(), id);
    }
}
