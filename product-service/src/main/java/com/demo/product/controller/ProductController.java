package com.demo.product.controller;

import com.demo.product.dto.ApiResponse;
import com.demo.product.dto.Product;
import com.demo.product.dto.ProductRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RestController
public class ProductController {

    // In-memory store — replace with a real repository in production
    private static final Map<Long, Product> store   = new ConcurrentHashMap<>();
    private static final AtomicLong         counter = new AtomicLong(4);

    static {
        store.put(1L, Product.builder().id(1L).name("Laptop")      .description("High-performance laptop")   .price(999.99).category("Electronics").build());
        store.put(2L, Product.builder().id(2L).name("Desk Chair")   .description("Ergonomic office chair")    .price(299.99).category("Furniture")  .build());
        store.put(3L, Product.builder().id(3L).name("Coffee Mug")   .description("Large ceramic mug")         .price(12.99) .category("Kitchen")    .build());
    }

    // ── Public ───────────────────────────────────────────────────────────────

    /** No token required — anyone can browse the catalogue */
    @GetMapping("/public/products")
    public ResponseEntity<ApiResponse<Collection<Product>>> listPublic() {
        return ResponseEntity.ok(ApiResponse.<Collection<Product>>builder()
                .success(true)
                .message("Product catalogue")
                .data(store.values())
                .build());
    }

    // ── Authenticated (USER+) ────────────────────────────────────────────────

    /** Returns full product details including who is viewing it */
    @GetMapping("/products/{id}")
    public ResponseEntity<ApiResponse<Product>> getById(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Product product = findOrThrow(id);
        log.info("Product {} viewed by {}", id, jwt.getClaimAsString("preferred_username"));
        return ResponseEntity.ok(ApiResponse.<Product>builder()
                .success(true)
                .data(product)
                .build());
    }

    @GetMapping("/products")
    public ResponseEntity<ApiResponse<Collection<Product>>> listAll(@AuthenticationPrincipal Jwt jwt) {
        log.info("Product list viewed by {}", jwt.getClaimAsString("preferred_username"));
        return ResponseEntity.ok(ApiResponse.<Collection<Product>>builder()
                .success(true)
                .data(store.values())
                .build());
    }

    // ── MODERATOR / ADMIN ────────────────────────────────────────────────────

    @PostMapping("/products")
    public ResponseEntity<ApiResponse<Product>> create(
            @Valid @RequestBody ProductRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        Long id = counter.getAndIncrement();
        Product product = Product.builder()
                .id(id)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(request.getCategory())
                .build();
        store.put(id, product);
        log.info("Product {} created by {}", id, jwt.getClaimAsString("preferred_username"));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<Product>builder()
                .success(true)
                .message("Product created")
                .data(product)
                .build());
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<ApiResponse<Product>> update(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        findOrThrow(id);
        Product updated = Product.builder()
                .id(id)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(request.getCategory())
                .build();
        store.put(id, updated);
        log.info("Product {} updated by {}", id, jwt.getClaimAsString("preferred_username"));
        return ResponseEntity.ok(ApiResponse.<Product>builder()
                .success(true)
                .message("Product updated")
                .data(updated)
                .build());
    }

    // ── ADMIN only ───────────────────────────────────────────────────────────

    @DeleteMapping("/products/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        findOrThrow(id);
        store.remove(id);
        log.info("Product {} deleted by {}", id, jwt.getClaimAsString("preferred_username"));
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Product deleted")
                .build());
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private Product findOrThrow(Long id) {
        Product product = store.get(id);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + id);
        }
        return product;
    }
}
