package com.tradetrail.api.merchant.catalog;

import com.tradetrail.application.catalog.CatalogService;
import com.tradetrail.application.inventory.InventoryService;
import com.tradetrail.domain.catalog.Product;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductAdminController {
    public record UpdateProduct(@NotBlank String name,String description,@Min(1) long priceCents,boolean published) {}

    @org.springframework.web.bind.annotation.PutMapping("/admin/products/{id}")
    public Product update(@PathVariable long id,@Valid @RequestBody UpdateProduct request) {
        return catalog.update(id,request.name(),request.description(),request.priceCents(),request.published());
    }
    private final CatalogService catalog;
    private final InventoryService inventory;

    public ProductAdminController(CatalogService catalog, InventoryService inventory) {
        this.catalog = catalog;
        this.inventory = inventory;
    }

    public record CreateProduct(@NotBlank String name, String description, @Min(1) long priceCents,
                                @Min(0) int initialStock) {
    }
    public record StockAdjustment(int delta, @NotBlank String reason) {}

    @GetMapping("/admin/products")
    public List<Product> list() {
        return catalog.list(true);
    }

    @PostMapping("/admin/products")
    public ResponseEntity<Product> create(@Valid @RequestBody CreateProduct request) {
        Product product = catalog.create(request.name(), request.description(), request.priceCents(), request.initialStock());
        return ResponseEntity.created(URI.create("/products/" + product.id())).body(product);
    }

    @PostMapping("/admin/products/{id}/publish")
    public Product publish(@PathVariable long id) {
        return catalog.publish(id);
    }

    @PostMapping("/admin/products/{id}/stock-adjustments")
    public Product adjust(@PathVariable long id, @Valid @RequestBody StockAdjustment request, Principal principal) {
        inventory.adjust(id, request.delta(), request.reason(), principal.getName());
        return catalog.get(id, true);
    }
}
