package com.tradetrail.domain.order;

/**
 * 订单明细保留成交时的名称、数量和单价。
 */
public record OrderItem(long productId, String productName, int quantity, long unitPriceCents) {
    public long subtotalCents() {
        return Math.multiplyExact(unitPriceCents, quantity);
    }
}
