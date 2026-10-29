package com.tradetrail.api.merchant.after_sale;

import com.tradetrail.api.PageSlice;
import com.tradetrail.application.after_sale.AfterSaleService;
import com.tradetrail.domain.after_sale.AfterSale;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AfterSaleAdminController {
    private final AfterSaleService cases;

    public AfterSaleAdminController(AfterSaleService cases) {
        this.cases = cases;
    }

    public record Decision(boolean approve, @NotBlank @Size(max = 500) String reason) {

    }

    public record Receipt(boolean qualified) {

    }

    @GetMapping("/admin/after-sales")
    public List<AfterSale> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageSlice.of(cases.list(), page, size);
    }

    @PostMapping("/admin/after-sales/{id}/decisions")
    public AfterSale decide(@PathVariable long id, @Valid @RequestBody Decision decision, Principal p) {
        return cases.decide(id, decision.approve(), decision.reason(), p.getName());
    }

    @PostMapping("/admin/after-sales/{id}/receipts")
    public AfterSale receive(@PathVariable long id, @RequestBody Receipt receipt, Principal p) {
        return cases.receiveReturn(id, receipt.qualified(), p.getName());
    }
}
