package com.tradetrail.api.merchant.payment;
import com.tradetrail.application.payment.PaymentService;
import com.tradetrail.domain.payment.RefundAttempt;
import org.springframework.web.bind.annotation.*;
@RestController
public class RefundAdminController {
    private final PaymentService payments;
    public RefundAdminController(PaymentService payments){this.payments=payments;}
    @PostMapping("/admin/refunds/{id}/retry") public RefundAttempt retry(@PathVariable String id){return payments.retryRefund(id);}
}
