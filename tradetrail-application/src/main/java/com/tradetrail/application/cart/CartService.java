package com.tradetrail.application.cart;

import com.tradetrail.application.catalog.CatalogService;
import com.tradetrail.application.integration.CommandService;
import com.tradetrail.application.order.OrderService;
import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.cart.CartItem;
import com.tradetrail.domain.cart.CartStore;
import com.tradetrail.domain.order.Order;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {
    private final CartStore cart;
    private final CatalogService catalog;
    private final OrderService orders;
    private final CommandService commands;

    public CartService(CartStore cart, CatalogService catalog, OrderService orders, CommandService commands) {
        this.cart = cart;
        this.catalog = catalog;
        this.orders = orders;
        this.commands = commands;
    }

    public record Quote(long productId, String productName, int quantity, long unitPriceCents, long subtotalCents,
            boolean available) {
    }

    public List<CartItem> list(String customer) {
        return cart.list(customer);
    }

    public List<Quote> quote(String customer) {
        return cart.list(customer).stream().filter(CartItem::selected).map(i -> {
            var p = catalog.get(i.productId(), true);
            return new Quote(p.id(), p.name(), i.quantity(), p.priceCents(),
                    Math.multiplyExact(p.priceCents(), i.quantity()), p.published() && p.available() >= i.quantity());
        }).toList();
    }

    @Transactional
    public CartItem add(String customer, long product, int qty) {
        lock(customer);
        validate(qty);
        var p = catalog.get(product, true);
        if (!p.published()) {
            throw new TradeFailure("PRODUCT_UNAVAILABLE", "商品未上架");
        }
        int current = cart.list(customer)
                .stream()
                .filter(i -> i.productId() == product)
                .mapToInt(CartItem::quantity)
                .sum();
        validate(Math.addExact(current, qty));
        return cart.add(customer, product, qty);
    }

    @Transactional
    public CartItem update(long id, String customer, int qty, boolean selected) {
        lock(customer);
        validate(qty);
        if (!cart.update(id, customer, qty, selected)) {
            throw new TradeFailure("CART_ITEM_NOT_FOUND", "购物车项目不存在");
        }
        return cart.find(id).orElseThrow();
    }

    @Transactional
    public void delete(long id, String customer) {
        lock(customer);
        if (!cart.delete(id, customer)) {
            throw new TradeFailure("CART_ITEM_NOT_FOUND", "购物车项目不存在");
        }
    }

    @Transactional
    public Order checkout(String customer, List<OrderService.Line> lines, String address, String key) {
        lock(customer);
        // 请求带已确认的明细；同键重试可在购物车清空后返回原订单。
        var current = cart.list(customer).stream().filter(CartItem::selected).toList();
        var existing = orders.list(customer).stream().filter(o -> o.requestId().equals(key)).findFirst();
        if (existing.isEmpty() && (lines == null || lines.size() != current.size()
                || current.stream()
                        .anyMatch(i -> lines.stream()
                                .noneMatch(l -> l.productId() == i.productId() && l.quantity() == i.quantity())))) {
            throw new TradeFailure("CART_CHANGED", "购物车已变化，请重新结算");
        }
        var order = orders.create(customer, lines, address, key);
        if (existing.isEmpty()) {
            for (var item : current) {
                cart.delete(item.id(), customer);
            }
        }
        return order;
    }

    private void lock(String customer) {
        commands.lock("cart:" + customer, "cart");
    }

    private void validate(int qty) {
        if (qty < 1 || qty > 10000) {
            throw new TradeFailure("INVALID_CART", "数量须在 1 至 10000 之间");
        }
    }
}
