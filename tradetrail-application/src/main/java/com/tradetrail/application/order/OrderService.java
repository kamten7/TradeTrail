package com.tradetrail.application.order;

import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.catalog.CatalogStore;
import com.tradetrail.domain.catalog.Product;
import com.tradetrail.domain.inventory.StockStore;
import com.tradetrail.domain.order.Order;
import com.tradetrail.domain.order.OrderStore;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderStore orders;
    private final CatalogStore catalog;
    private final StockStore stock;

    public OrderService(OrderStore orders, CatalogStore catalog, StockStore stock) {
        this.orders = orders;
        this.catalog = catalog;
        this.stock = stock;
    }

    @Transactional
    public Order create(String customer, long productId, int quantity, String requestId) {
        if (quantity < 1 || requestId == null || requestId.isBlank()) {
            throw new TradeFailure("INVALID_ORDER", "数量和请求号必须有效");
        }
        Order prior = orders.findByRequest(customer, requestId).orElse(null);
        if (prior != null) {
            if (prior.productId() != productId || prior.quantity() != quantity) {
                throw new TradeFailure("IDEMPOTENCY_CONFLICT", "请求号已用于其他订单");
            }
            return prior;
        }
        Product product = catalog.find(productId).orElseThrow(() -> new TradeFailure("PRODUCT_NOT_FOUND", "商品不存在"));
        if (!product.published()) {
            throw new TradeFailure("PRODUCT_UNAVAILABLE", "商品未上架");
        }
        Order order = orders.create(customer, productId, quantity, product.priceCents(), requestId);
        if (!stock.reserve(productId, quantity, order.id())) {
            throw new TradeFailure("OUT_OF_STOCK", "库存不足");
        }
        return order;
    }

    public Order get(long id, String customer) {
        Order order = orders.find(id).orElseThrow(() -> new TradeFailure("ORDER_NOT_FOUND", "订单不存在"));
        if (!order.customer().equals(customer)) {
            throw new TradeFailure("FORBIDDEN", "无权访问此订单");
        }
        return order;
    }

    public List<Order> list(String customer) {
        return orders.listForCustomer(customer);
    }

    public List<Order> listAll() {
        return orders.listAll();
    }

    @Transactional
    public Order cancel(long id, String customer) {
        Order order = get(id, customer);
        if (!orders.transition(id, "PENDING_PAYMENT", "CANCELLED")) {
            throw new TradeFailure("INVALID_ORDER_STATE", "当前订单不可取消");
        }
        if (!stock.release(order.productId(), order.quantity(), order.id())) {
            throw new IllegalStateException("库存预留释放失败");
        }
        return get(id, customer);
    }
}
