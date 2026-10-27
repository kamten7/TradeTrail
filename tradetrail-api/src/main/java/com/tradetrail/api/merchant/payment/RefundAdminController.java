package com.tradetrail.api.merchant.payment;
import com.tradetrail.application.payment.PaymentService;
import com.tradetrail.application.payment.PaymentQueryService;
import com.tradetrail.domain.payment.RefundAttempt;
import org.springframework.web.bind.annotation.*;
@RestController
public class RefundAdminController {
    private final PaymentService payments;
    private final PaymentQueryService query;
    public RefundAdminController(PaymentService payments,PaymentQueryService query){this.payments=payments;this.query=query;}
    @GetMapping("/admin/refunds/failed") public java.util.List<RefundAttempt> failed(){return query.failedRefunds();}
    @PostMapping("/admin/refunds/{id}/retry") public RefundAttempt retry(@PathVariable String id){return payments.retryRefund(id);}
}
