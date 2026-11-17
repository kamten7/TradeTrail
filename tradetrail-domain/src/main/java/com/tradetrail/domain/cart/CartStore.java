package com.tradetrail.domain.cart;

import java.util.List;
import java.util.Optional;

/**
 * 定义购物车数据访问端口，使业务规则不依赖数据库实现。
 */
public interface CartStore {
    List<CartItem> list(String customer);

    Optional<CartItem> find(long id);

    CartItem add(String customer, long productId, int quantity);

    boolean update(long id, String customer, int quantity, boolean selected);

    boolean delete(long id, String customer);
}
