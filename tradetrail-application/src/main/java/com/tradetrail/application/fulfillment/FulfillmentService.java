package com.tradetrail.application.fulfillment;

import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.fulfillment.Tracking;
import com.tradetrail.domain.fulfillment.TrackingStore;
import com.tradetrail.domain.order.Order;
import com.tradetrail.domain.order.OrderStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FulfillmentService {
    private final OrderStore orders;
    private final TrackingStore tracking;

    public FulfillmentService(OrderStore orders, TrackingStore tracking) {
        this.orders = orders;
        this.tracking = tracking;
    }

    @Transactional
    public Order ship(long orderId) {
        String trackingNo = "MOCK-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        if (!orders.ship(orderId, trackingNo)) {
            throw new TradeFailure("INVALID_ORDER_STATE", "订单不存在或不可发货");
        }
        return orders.find(orderId).orElseThrow();
    }

    public List<Tracking> timeline(long orderId, String customer) {
        Order order = orders.find(orderId).orElseThrow(() -> new TradeFailure("ORDER_NOT_FOUND", "订单不存在"));
        if (!order.customer().equals(customer)) {
            throw new TradeFailure("FORBIDDEN", "无权查看此订单");
        }
        return order.trackingNo() == null ? List.of() : tracking.byTrackingNo(order.trackingNo());
    }

    @Transactional
    public void receive(Tracking event) {
        if (event.eventId() == null || event.eventId().isBlank() || event.trackingNo() == null
                || !event.trackingNo().startsWith("MOCK-") || event.occurredAt() == null
                || !List.of("PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "EXCEPTION").contains(event.status())
                || event.location() == null || event.location().isBlank()) {
            throw new TradeFailure("INVALID_TRACKING_EVENT", "物流事件不合法");
        }
        if (orders.findByTrackingNo(event.trackingNo()).isEmpty()) {
            throw new TradeFailure("TRACKING_NOT_FOUND", "运单不存在");
        }
        tracking.add(event);
    }

    public Optional<Tracking> receipt(String eventId) {
        return tracking.find(eventId);
    }
}
