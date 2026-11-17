package com.tradetrail.domain.catalog;

import java.util.List;
import java.util.Optional;

/**
 * 定义商品数据访问端口，使业务规则不依赖数据库实现。
 */
public interface CatalogStore {
    Product create(String name, String description, long priceCents, int initialStock);

    Optional<Product> find(long id);

    List<Product> list(boolean includeUnpublished);

    boolean publish(long id);

    boolean update(long id, String name, String description, long priceCents, boolean published);
}
