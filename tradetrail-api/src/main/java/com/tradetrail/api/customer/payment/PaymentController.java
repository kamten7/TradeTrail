package com.tradetrail.api.customer.payment;

import com.tradetrail.application.order.OrderService;
import com.tradetrail.application.payment.PaymentQueryService;
import com.tradetrail.application.payment.PaymentService;
import com.tradetrail.domain.payment.PaymentAttempt;
import java.security.Principal;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {
    private final PaymentService payments;
    private final PaymentQueryService query;
    private final OrderService orders;

    public PaymentController(PaymentService payments, PaymentQueryService query, OrderService orders) {
        this.payments = payments;
        this.query = query;
        this.orders = orders;
    }

    @PostMapping("/orders/{id}/payments")
    public PaymentAttempt create(@PathVariable long id, Principal p) {
        return payments.create(id, p.getName());
    }

    @GetMapping("/orders/{id}/payments")
    public Map<String, Object> status(@PathVariable long id, Principal p) {
        orders.get(id, p.getName());
        return Map.of("orderId", id, "payment", query.payment(id).<Object>map(v -> v).orElse(Map.of()), "refund",
                query.refund(id).<Object>map(v -> v).orElse(Map.of()));
    }
}
