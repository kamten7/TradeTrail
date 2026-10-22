package com.tradetrail.application.cart;

import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.cart.CartItem;
import com.tradetrail.domain.cart.CartStore;
import com.tradetrail.domain.catalog.CatalogStore;
import com.tradetrail.domain.catalog.Product;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {
    private final CartStore cart;
    private final CatalogStore catalog;

    public CartService(CartStore cart, CatalogStore catalog) {
        this.cart = cart;
        this.catalog = catalog;
    }

    public record Quote(long productId, int quantity, long unitPriceCents, long subtotalCents, boolean available) {}

    public List<CartItem> list(String customer) {
        return cart.list(customer);
    }

    public List<Quote> quote(String customer) {
        return cart.list(customer).stream().filter(CartItem::selected).map(item -> {
            Product product = catalog.find(item.productId()).orElseThrow();
            return new Quote(item.productId(), item.quantity(), product.priceCents(),
                    Math.multiplyExact(product.priceCents(), item.quantity()),
                    product.published() && product.available() >= item.quantity());
        }).toList();
    }

    @Transactional
    public CartItem add(String customer, long productId, int quantity) {
        if (quantity < 1) throw new TradeFailure("INVALID_CART", "数量必须大于零");
        Product product = catalog.find(productId).orElseThrow(() -> new TradeFailure("PRODUCT_NOT_FOUND", "商品不存在"));
        if (!product.published()) throw new TradeFailure("PRODUCT_UNAVAILABLE", "商品未上架");
        return cart.add(customer, productId, quantity);
    }

    @Transactional
    public CartItem update(long id, String customer, int quantity, boolean selected) {
        if (quantity < 1 || !cart.update(id, customer, quantity, selected)) {
            throw new TradeFailure("CART_ITEM_NOT_FOUND", "购物车项目不存在或数量不合法");
        }
        return cart.find(id).orElseThrow();
    }

    @Transactional
    public void delete(long id, String customer) {
        if (!cart.delete(id, customer)) throw new TradeFailure("CART_ITEM_NOT_FOUND", "购物车项目不存在");
    }
}
