package com.tradetrail.persistence.catalog;

import com.tradetrail.domain.catalog.CatalogStore;
import com.tradetrail.domain.catalog.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisCatalogStore implements CatalogStore {
    private final ProductMapper mapper;

    public MyBatisCatalogStore(ProductMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Product create(String name, String description, long priceCents, int initialStock) {
        ProductRow row = new ProductRow();
        row.name = name;
        row.description = description;
        row.priceCents = priceCents;
        row.published = false;
        mapper.insert(row);
        return new Product(row.id, name, description, priceCents, false, initialStock);
    }

    @Override
    public Optional<Product> find(long id) {
        return Optional.ofNullable(mapper.detail(id));
    }

    @Override
    public List<Product> list(boolean includeUnpublished) {
        return mapper.browse(includeUnpublished);
    }

    @Override
    public boolean publish(long id) {
        return mapper.publish(id) == 1;
    }
}
