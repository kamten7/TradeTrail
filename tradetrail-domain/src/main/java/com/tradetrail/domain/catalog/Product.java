package com.tradetrail.domain.catalog;

/**
 * 商品报价包含当前可售量，历史订单仍使用成交快照。
 */
public record Product(Long id, String name, String description, long priceCents, boolean published, int available) {
}
