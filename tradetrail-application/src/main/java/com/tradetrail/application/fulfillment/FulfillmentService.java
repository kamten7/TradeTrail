package com.tradetrail.application.fulfillment;

import com.tradetrail.application.integration.CommandService;
import com.tradetrail.application.order.OrderService;
import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.fulfillment.Shipment;
import com.tradetrail.domain.fulfillment.ShipmentStore;
import com.tradetrail.domain.fulfillment.Tracking;
import com.tradetrail.domain.fulfillment.TrackingStore;
import com.tradetrail.domain.order.Order;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FulfillmentService {
    private final OrderService orders;
    private final TrackingStore tracking;
    private final ShipmentStore shipments;
    private final CommandService commands;

    public FulfillmentService(OrderService orders, TrackingStore tracking, ShipmentStore shipments,
            CommandService commands) {
        this.orders = orders;
        this.tracking = tracking;
        this.shipments = shipments;
        this.commands = commands;
    }

    @Transactional
    public Order ship(long id) {
        var o = orders.lock(id);
        if ("SHIPPED".equals(o.status())) {
            return o;
        }
        String no = "MOCK-" + UUID.randomUUID();
        var result = orders.ship(id, no);
        shipments.create(id, no);
        return result;
    }

    public List<Tracking> timeline(long id, String customer) {
        orders.get(id, customer);
        return shipments.byOrder(id).map(s -> tracking.byTrackingNo(s.trackingNo())).orElse(List.of());
    }

    public Optional<Shipment> shipment(long id, String customer) {
        orders.get(id, customer);
        return shipments.byOrder(id);
    }

    @Transactional
    public void receive(Tracking e) {
        if (e == null || e.status() == null || e.eventId() == null || e.eventId().isBlank()
                || e.eventId().length() > 100 || e.trackingNo() == null || e.occurredAt() == null
                || e.location() == null || e.location().isBlank() || e.location().length() > 120
                || !List.of("PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "EXCEPTION", "RESUMED")
                        .contains(e.status())) {
            throw new TradeFailure("INVALID_TRACKING_EVENT", "物流事件不合法");
        }
        var s = shipments.byTracking(e.trackingNo()).orElseThrow(() -> new TradeFailure("TRACKING_NOT_FOUND", "运单不存在"));
        orders.lock(s.orderId());
        commands.lock("tracking:" + e.eventId(), e.toString());
        if (e.occurredAt().isAfter(orders.now().plusMinutes(5))) {
            throw new TradeFailure("INVALID_TRACKING_EVENT", "物流发生时间超出允许范围");
        }
        if (!tracking.add(e)) {
            return;
        }
        s = shipments.byOrder(s.orderId()).orElseThrow();
        if ("DELIVERED".equals(s.status())
                || s.lastOccurredAt() != null && e.occurredAt().isBefore(s.lastOccurredAt())) {
            return;
        }
        int progress = switch (e.status()) {
            case "PICKED_UP" -> 1;
            case "IN_TRANSIT", "RESUMED" -> 2;
            case "OUT_FOR_DELIVERY" -> 3;
            case "DELIVERED" -> 4;
            default -> s.progress();
        };
        if ("RESUMED".equals(e.status()) && !"EXCEPTION".equals(s.status())) {
            throw new TradeFailure("INVALID_TRACKING_EVENT", "只有异常运单可以恢复");
        }
        if (!"EXCEPTION".equals(e.status()) && !"RESUMED".equals(e.status()) && progress < s.progress()) {
            return;
        }
        String status = "RESUMED".equals(e.status()) ? (s.progress() >= 3 ? "OUT_FOR_DELIVERY" : "IN_TRANSIT")
                : e.status();
        shipments.update(s.orderId(), status, Math.max(progress, s.progress()), e.occurredAt(),
                "DELIVERED".equals(status));
    }

    public Optional<Tracking> receipt(String event) {
        return tracking.find(event);
    }

    public List<Long> autoConfirmDue(int days, long afterOrderId) {
        return shipments.autoConfirmDue(days, afterOrderId);
    }
}
