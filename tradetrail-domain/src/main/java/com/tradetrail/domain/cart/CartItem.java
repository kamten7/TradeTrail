package com.tradetrail.domain.cart;

/**
 * 购物车选择状态只影响结算范围，不锁定商品价格。
 */
public record CartItem(Long id, String customer, long productId, int quantity, boolean selected) {
}
