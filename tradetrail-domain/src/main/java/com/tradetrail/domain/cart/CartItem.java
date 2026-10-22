package com.tradetrail.domain.cart;

public record CartItem(Long id, String customer, long productId, int quantity, boolean selected) {
}
