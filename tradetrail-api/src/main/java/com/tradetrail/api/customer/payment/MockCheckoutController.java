package com.tradetrail.api.customer.payment;
import com.tradetrail.application.payment.PaymentService;
import com.tradetrail.domain.payment.PaymentAttempt;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;
@RestController
public class MockCheckoutController {
    private final PaymentService payments;
    public MockCheckoutController(PaymentService payments){this.payments=payments;}
    public record Result(boolean success){}
    @PostMapping("/mock/checkout/{id}/complete") public PaymentAttempt complete(@PathVariable String id,@RequestBody Result result,Principal p){return payments.simulate(id,p.getName(),result.success());}
}
