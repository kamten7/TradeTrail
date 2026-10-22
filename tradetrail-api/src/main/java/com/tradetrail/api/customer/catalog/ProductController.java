package com.tradetrail.api.customer.catalog;

import com.tradetrail.application.catalog.CatalogService;
import com.tradetrail.domain.catalog.Product;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {
    private final CatalogService catalog;

    public ProductController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/products")
    public List<Product> list() {
        return catalog.list(false);
    }

    @GetMapping("/products/{id}")
    public Product detail(@PathVariable long id) {
        return catalog.get(id, false);
    }
}
