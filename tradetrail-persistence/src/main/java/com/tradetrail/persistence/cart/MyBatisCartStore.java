package com.tradetrail.persistence.cart;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tradetrail.domain.cart.CartItem;
import com.tradetrail.domain.cart.CartStore;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisCartStore implements CartStore {
    private final CartMapper mapper;

    public MyBatisCartStore(CartMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<CartItem> list(String customer) {
        return mapper.selectList(new QueryWrapper<CartRow>().eq("customer", customer).orderByDesc("id"))
                .stream().map(this::model).toList();
    }

    @Override
    public Optional<CartItem> find(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::model);
    }

    @Override
    public CartItem add(String customer, long productId, int quantity) {
        CartRow row = mapper.selectOne(new QueryWrapper<CartRow>().eq("customer", customer).eq("product_id", productId));
        if (row == null) {
            row = new CartRow();
            row.customer = customer;
            row.productId = productId;
            row.quantity = quantity;
            row.selected = true;
            mapper.insert(row);
        } else {
            row.quantity += quantity;
            mapper.updateById(row);
        }
        return model(row);
    }

    @Override
    public boolean update(long id, String customer, int quantity, boolean selected) {
        return mapper.updateOwned(id, customer, quantity, selected) == 1;
    }

    @Override
    public boolean delete(long id, String customer) {
        return mapper.deleteOwned(id, customer) == 1;
    }

    private CartItem model(CartRow row) {
        return new CartItem(row.id, row.customer, row.productId, row.quantity, row.selected);
    }
}
