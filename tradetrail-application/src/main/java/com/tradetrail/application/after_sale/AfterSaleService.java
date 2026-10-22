package com.tradetrail.application.after_sale;

import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.after_sale.AfterSale;
import com.tradetrail.domain.after_sale.AfterSaleStore;
import com.tradetrail.domain.order.Order;
import com.tradetrail.domain.order.OrderStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AfterSaleService {
    private final AfterSaleStore cases;
    private final OrderStore orders;

    public AfterSaleService(AfterSaleStore cases, OrderStore orders) {
        this.cases = cases;
        this.orders = orders;
    }

    @Transactional
    public AfterSale apply(long orderId, String customer, String reason) {
        Order order = orders.find(orderId).orElseThrow(() -> new TradeFailure("ORDER_NOT_FOUND", "订单不存在"));
        if (!order.customer().equals(customer)) {
            throw new TradeFailure("FORBIDDEN", "无权操作此订单");
        }
        if (!"PAID".equals(order.status()) && !"SHIPPED".equals(order.status())) {
            throw new TradeFailure("INVALID_ORDER_STATE", "当前订单不可申请售后");
        }
        if (reason == null || reason.isBlank()) {
            throw new TradeFailure("INVALID_REASON", "请填写售后原因");
        }
        return cases.byOrder(orderId).orElseGet(() -> cases.create(orderId, customer, reason.strip()));
    }

    public AfterSale get(long orderId, String customer) {
        Order order = orders.find(orderId).orElseThrow(() -> new TradeFailure("ORDER_NOT_FOUND", "订单不存在"));
        if (!order.customer().equals(customer)) {
            throw new TradeFailure("FORBIDDEN", "无权访问此订单");
        }
        return cases.byOrder(orderId).orElseThrow(() -> new TradeFailure("AFTER_SALE_NOT_FOUND", "售后申请不存在"));
    }
}
