package com.tradetrail.api.customer.payment;

import com.tradetrail.application.order.OrderService;
import com.tradetrail.application.payment.PaymentService;
import com.tradetrail.domain.order.Order;
import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {
    private final PaymentService payments;
    private final OrderService orders;

    public PaymentController(PaymentService payments, OrderService orders) {
        this.payments = payments;
        this.orders = orders;
    }

    @PostMapping("/orders/{id}/payments")
    public Order completeMockPayment(@PathVariable long id, Principal principal) {
        return payments.completeMockPayment(id, principal.getName());
    }

    @GetMapping("/orders/{id}/payments")
    public Map<String, Object> status(@PathVariable long id, Principal principal) {
        Order order = orders.get(id, principal.getName());
        return Map.of("orderId", order.id(), "status", order.status(), "paid",
                "PAID".equals(order.status()) || "SHIPPED".equals(order.status()));
    }
}
