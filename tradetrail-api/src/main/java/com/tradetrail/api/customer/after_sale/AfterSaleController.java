package com.tradetrail.api.customer.after_sale;

import com.tradetrail.application.after_sale.AfterSaleService;
import com.tradetrail.domain.after_sale.AfterSale;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AfterSaleController {
    public record ReturnParcel(@NotBlank @Size(max = 100) String trackingNo) {
    }

    @PostMapping("/after-sales/{id}/return")
    public AfterSale returnParcel(@PathVariable long id, @Valid @RequestBody ReturnParcel request,
            Principal principal) {
        return cases.returnParcel(id, principal.getName(), request.trackingNo());
    }

    private final AfterSaleService cases;

    public AfterSaleController(AfterSaleService cases) {
        this.cases = cases;
    }

    public record ApplyRequest(@NotBlank String reason) {
    }

    @PostMapping("/orders/{id}/after-sales")
    public AfterSale apply(@PathVariable long id, @Valid @RequestBody ApplyRequest request, Principal principal) {
        return cases.apply(id, principal.getName(), request.reason());
    }

    @GetMapping("/orders/{id}/after-sales")
    public AfterSale status(@PathVariable long id, Principal principal) {
        return cases.get(id, principal.getName());
    }
}
