package com.stockflow.product;

import com.stockflow.auth.JwtAuthFilter.AuthUser;
import com.stockflow.common.PageResponse;
import com.stockflow.product.dto.ProductRequest;
import com.stockflow.product.dto.ProductResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public PageResponse<ProductResponse> list(@AuthenticationPrincipal AuthUser user,
                                              @RequestParam(required = false) String search,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        return productService.list(user.userId(), search, page, size);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return productService.get(user.userId(), id);
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@AuthenticationPrincipal AuthUser user,
                                                  @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(user.userId(), request));
    }

    @PutMapping("/{id}")
    public ProductResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id,
                                  @Valid @RequestBody ProductRequest request) {
        return productService.update(user.userId(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        productService.delete(user.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
