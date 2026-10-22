package com.tradetrail.api.customer.fulfillment;

import com.tradetrail.application.fulfillment.FulfillmentService;
import com.tradetrail.application.order.OrderService;
import com.tradetrail.domain.fulfillment.Tracking;
import com.tradetrail.domain.order.Order;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TrackingController {
    private final FulfillmentService fulfillment;
    private final OrderService orders;

    public TrackingController(FulfillmentService fulfillment, OrderService orders) {
        this.fulfillment = fulfillment;
        this.orders = orders;
    }

    @GetMapping("/orders/{id}/tracking")
    public List<Tracking> timeline(@PathVariable long id, Principal principal) {
        return fulfillment.timeline(id, principal.getName());
    }

    @GetMapping("/orders/{id}/shipment")
    public Map<String, Object> shipment(@PathVariable long id, Principal principal) {
        Order order = orders.get(id, principal.getName());
        return Map.of("orderId", order.id(), "trackingNo", order.trackingNo() == null ? "" : order.trackingNo());
    }
}
