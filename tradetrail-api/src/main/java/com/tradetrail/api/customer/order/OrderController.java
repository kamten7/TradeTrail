package com.tradetrail.api.customer.order;

import com.tradetrail.api.PageSlice;
import com.tradetrail.application.order.OrderService;
import com.tradetrail.domain.order.Order;
import com.tradetrail.domain.order.OrderStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    public record CreateOrder(@NotEmpty List<@Valid Line> items, @NotBlank @Size(max = 500) String address) {

    }

    public record Line(@Min(1) long productId, @Min(1) @Max(10000) int quantity,
            @NotNull @Min(1) Long expectedPriceCents) {
        public OrderService.Line command() {
            return new OrderService.Line(productId, quantity, expectedPriceCents);
        }
    }

    @PostMapping("/orders")
    public ResponseEntity<Order> create(@Valid @RequestBody CreateOrder request,
            @RequestHeader("Idempotency-Key") String key, Principal principal) {
        var order = orders.create(principal.getName(), request.items().stream().map(Line::command).toList(),
                request.address(), key);
        return ResponseEntity.created(URI.create("/orders/" + order.id())).body(order);
    }

    @GetMapping("/orders")
    public List<Order> list(Principal principal, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageSlice.of(orders.list(principal.getName()), page, size);
    }

    @GetMapping("/orders/{id}")
    public Order detail(@PathVariable long id, Principal principal) {
        return orders.get(id, principal.getName());
    }

    @PostMapping("/orders/{id}/cancel")
    public Order cancel(@PathVariable long id, Principal principal) {
        return orders.cancel(id, principal.getName());
    }

    @PostMapping("/orders/{id}/confirm")
    public Order confirm(@PathVariable long id, Principal principal) {
        return orders.confirm(id, principal.getName());
    }

    @GetMapping("/orders/{id}/history")
    public List<OrderStore.StatusLog> history(@PathVariable long id, Principal principal) {
        return orders.history(id, principal.getName());
    }
}
