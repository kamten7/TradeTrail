package com.tradetrail.api.merchant.order;

import com.tradetrail.application.fulfillment.FulfillmentService;
import com.tradetrail.application.order.OrderService;
import com.tradetrail.domain.order.Order;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderAdminController {
    private final OrderService orders;
    private final FulfillmentService fulfillment;

    public OrderAdminController(OrderService orders, FulfillmentService fulfillment) {
        this.orders = orders;
        this.fulfillment = fulfillment;
    }

    @GetMapping("/admin/orders")
    public List<Order> list() {
        return orders.listAll();
    }

    @PostMapping("/admin/orders/{id}/shipments")
    public Order ship(@PathVariable long id) {
        return fulfillment.ship(id);
    }
}
