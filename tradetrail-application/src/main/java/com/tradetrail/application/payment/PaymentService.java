package com.tradetrail.application.payment;

import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.inventory.StockStore;
import com.tradetrail.domain.order.Order;
import com.tradetrail.domain.order.OrderStore;
import com.tradetrail.domain.payment.PaymentStore;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private final OrderStore orders;
    private final StockStore stock;
    private final PaymentStore notices;

    public PaymentService(OrderStore orders, StockStore stock, PaymentStore notices) {
        this.orders = orders;
        this.stock = stock;
        this.notices = notices;
    }

    @Transactional
    public Order completeMockPayment(long orderId, String customer) {
        Order order = orders.find(orderId).orElseThrow(() -> new TradeFailure("ORDER_NOT_FOUND", "订单不存在"));
        if (!order.customer().equals(customer)) {
            throw new TradeFailure("FORBIDDEN", "无权支付此订单");
        }
        if (!"PENDING_PAYMENT".equals(order.status())) {
            throw new TradeFailure("INVALID_ORDER_STATE", "当前订单不可支付");
        }
        receiveNotice("mock-" + UUID.randomUUID(), orderId);
        return orders.find(orderId).orElseThrow();
    }

    @Transactional
    public void receiveNotice(String eventId, long orderId) {
        if (!notices.recordNotice(eventId, orderId)) {
            return;
        }
        Order order = orders.find(orderId).orElseThrow(() -> new TradeFailure("ORDER_NOT_FOUND", "订单不存在"));
        if (!orders.transition(orderId, "PENDING_PAYMENT", "PAID")) {
            throw new TradeFailure("INVALID_ORDER_STATE", "订单已关闭或已支付");
        }
        if (!stock.confirm(order.productId(), order.quantity(), order.id())) {
            throw new IllegalStateException("库存确认失败");
        }
    }
}
