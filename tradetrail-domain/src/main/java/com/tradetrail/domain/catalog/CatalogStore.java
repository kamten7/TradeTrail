package com.tradetrail.domain.catalog;

import java.util.List;
import java.util.Optional;

public interface CatalogStore {
    Product create(String name, String description, long priceCents, int initialStock);
    Optional<Product> find(long id);
    List<Product> list(boolean includeUnpublished);
    boolean publish(long id);
}
