package com.tradetrail.domain.inventory;

/**
 * 定义库存数据访问端口，使业务规则不依赖数据库实现。
 */
public interface StockStore {
    void initialize(long productId, int quantity);

    boolean adjust(long productId, int delta, String reason, String operator);

    boolean reserve(long productId, int quantity, long orderId);

    boolean confirm(long productId, int quantity, long orderId);

    boolean release(long productId, int quantity, long orderId);

    boolean restock(long productId, int quantity, long orderId);
}
