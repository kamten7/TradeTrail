package com.tradetrail.domain.order;

public record OrderItem(long productId, String productName, int quantity, long unitPriceCents) {
    public long subtotalCents() {
        return Math.multiplyExact(unitPriceCents, quantity);
    }
}
