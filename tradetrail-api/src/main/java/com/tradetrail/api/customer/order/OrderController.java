package com.tradetrail.api.customer.order;

import com.tradetrail.application.order.OrderService;
import com.tradetrail.domain.order.Order;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    public record CreateOrder(@Min(1) long productId, @Min(1) int quantity) {
    }

    @PostMapping("/orders")
    public ResponseEntity<Order> create(@Valid @RequestBody CreateOrder request,
                                        @RequestHeader("Idempotency-Key") String key, Principal principal) {
        Order order = orders.create(principal.getName(), request.productId(), request.quantity(), key);
        return ResponseEntity.created(URI.create("/orders/" + order.id())).body(order);
    }

    @GetMapping("/orders")
    public List<Order> list(Principal principal) {
        return orders.list(principal.getName());
    }

    @GetMapping("/orders/{id}")
    public Order detail(@PathVariable long id, Principal principal) {
        return orders.get(id, principal.getName());
    }

    @PostMapping("/orders/{id}/cancel")
    public Order cancel(@PathVariable long id, Principal principal) {
        return orders.cancel(id, principal.getName());
    }
}
