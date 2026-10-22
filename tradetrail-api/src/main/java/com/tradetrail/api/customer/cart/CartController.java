package com.tradetrail.api.customer.cart;

import com.tradetrail.application.cart.CartService;
import com.tradetrail.domain.cart.CartItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CartController {
    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    public record AddItem(@Min(1) long productId, @Min(1) int quantity) {}
    public record UpdateItem(@Min(1) int quantity, boolean selected) {}

    @GetMapping("/cart/items")
    public List<CartItem> list(Principal principal) {
        return cart.list(principal.getName());
    }

    @GetMapping("/cart/quote")
    public List<CartService.Quote> quote(Principal principal) {
        return cart.quote(principal.getName());
    }

    @PostMapping("/cart/items")
    @ResponseStatus(HttpStatus.CREATED)
    public CartItem add(@Valid @RequestBody AddItem request, Principal principal) {
        return cart.add(principal.getName(), request.productId(), request.quantity());
    }

    @PutMapping("/cart/items/{id}")
    public CartItem update(@PathVariable long id, @Valid @RequestBody UpdateItem request, Principal principal) {
        return cart.update(id, principal.getName(), request.quantity(), request.selected());
    }

    @DeleteMapping("/cart/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, Principal principal) {
        cart.delete(id, principal.getName());
    }
}
