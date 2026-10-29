package com.tradetrail.domain.cart;

import java.util.List;
import java.util.Optional;

public interface CartStore {
    List<CartItem> list(String customer);

    Optional<CartItem> find(long id);

    CartItem add(String customer, long productId, int quantity);

    boolean update(long id, String customer, int quantity, boolean selected);

    boolean delete(long id, String customer);
}
