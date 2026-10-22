package com.tradetrail.application.catalog;

import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.catalog.CatalogStore;
import com.tradetrail.domain.catalog.Product;
import com.tradetrail.domain.inventory.StockStore;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
    private final CatalogStore store;
    private final StockStore stock;

    public CatalogService(CatalogStore store, StockStore stock) {
        this.store = store;
        this.stock = stock;
    }

    @Transactional
    public Product create(String name, String description, long priceCents, int stock) {
        if (name == null || name.isBlank() || priceCents < 1 || stock < 0) {
            throw new TradeFailure("INVALID_PRODUCT", "商品名称、价格或库存不合法");
        }
        Product product = store.create(name.strip(), description == null ? "" : description, priceCents, stock);
        this.stock.initialize(product.id(), stock);
        return get(product.id(), true);
    }

    public Product get(long id, boolean includeUnpublished) {
        Product product = store.find(id).orElseThrow(() -> new TradeFailure("PRODUCT_NOT_FOUND", "商品不存在"));
        if (!includeUnpublished && !product.published()) {
            throw new TradeFailure("PRODUCT_NOT_FOUND", "商品不存在");
        }
        return product;
    }

    public List<Product> list(boolean includeUnpublished) {
        return store.list(includeUnpublished);
    }

    @Transactional
    public Product publish(long id) {
        if (!store.publish(id)) {
            throw new TradeFailure("PRODUCT_NOT_FOUND", "商品不存在");
        }
        return get(id, true);
    }
}
