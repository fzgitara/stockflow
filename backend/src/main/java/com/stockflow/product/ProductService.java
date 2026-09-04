package com.stockflow.product;

import com.stockflow.common.ApiException;
import com.stockflow.common.PageResponse;
import com.stockflow.product.dto.ProductRequest;
import com.stockflow.product.dto.ProductResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProductService {

    private static final java.util.Set<String> SORTABLE_FIELDS =
            java.util.Set.of("name", "sku", "unitPrice", "quantityOnHand", "createdAt");

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(UUID userId, String search, int page, int size,
                                              String sortBy, String sortDir) {
        Sort sort = buildSort(sortBy, sortDir);
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), sort);
        Page<Product> result = (search == null || search.isBlank())
                ? productRepository.findByUserId(userId, pageable)
                : productRepository.searchByUser(userId, search.trim(), pageable);
        return PageResponse.from(result.map(ProductResponse::from));
    }

    /** Whitelisted fields only — sortBy never reaches the DB as raw column text. */
    private static Sort buildSort(String sortBy, String sortDir) {
        String field = (sortBy != null && SORTABLE_FIELDS.contains(sortBy)) ? sortBy : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID userId, UUID id) {
        return ProductResponse.from(findOwned(userId, id));
    }

    @Transactional
    public ProductResponse create(UUID userId, ProductRequest request) {
        if (productRepository.existsByUserIdAndSkuIgnoreCase(userId, request.sku().trim())) {
            throw ApiException.badRequest("Validation failed", "sku", "SKU already exists");
        }
        try {
            Product product = new Product(userId, request.sku().trim(), request.name().trim(),
                    emptyToNull(request.description()), request.unitPrice(), request.quantityOnHand());
            return ProductResponse.from(productRepository.save(product));
        } catch (DataIntegrityViolationException e) {
            // unique(user_id, sku) constraint as a second line of defense
            throw ApiException.badRequest("Validation failed", "sku", "SKU already exists");
        }
    }

    @Transactional
    public ProductResponse update(UUID userId, UUID id, ProductRequest request) {
        Product product = findOwned(userId, id);
        String newSku = request.sku().trim();
        if (!product.getSku().equalsIgnoreCase(newSku)
                && productRepository.existsByUserIdAndSkuIgnoreCase(userId, newSku)) {
            throw ApiException.badRequest("Validation failed", "sku", "SKU already exists");
        }
        product.setSku(newSku);
        product.setName(request.name().trim());
        product.setDescription(emptyToNull(request.description()));
        product.setUnitPrice(request.unitPrice());
        product.setQuantityOnHand(request.quantityOnHand());
        return ProductResponse.from(productRepository.save(product));
    }

    /**
     * I4 (D3): hard delete, but blocked with 409 when any invoice line
     * references the product — it must not silently disappear.
     */
    @Transactional
    public void delete(UUID userId, UUID id) {
        Product product = findOwned(userId, id);
        try {
            productRepository.delete(product);
            productRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("Cannot delete product: it is referenced by one or more invoices");
        }
    }

    private Product findOwned(UUID userId, UUID id) {
        return productRepository.findByIdAndUserId(id, userId)
                // 404 (not 403) — does not leak other users' ids
                .orElseThrow(() -> ApiException.notFound("Product not found"));
    }

    private static int clampSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
