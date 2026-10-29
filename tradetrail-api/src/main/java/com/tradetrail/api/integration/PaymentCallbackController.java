package com.tradetrail.api.integration;

import com.tradetrail.application.payment.PaymentService;
import com.tradetrail.domain.payment.PaymentGateway;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentCallbackController {
    private final PaymentService payments;
    private final PaymentGateway gateway;

    public PaymentCallbackController(PaymentService payments, PaymentGateway gateway) {
        this.payments = payments;
        this.gateway = gateway;
    }

    @PostMapping("/callbacks/payments/mock")
    public Map<String, String> receive(@RequestBody String raw, @RequestHeader("X-Payment-Timestamp") String timestamp,
            @RequestHeader("X-Payment-Signature") String signature) {
        var notice = gateway.verify(raw, timestamp, signature);
        payments.receive(notice.eventId(), notice.paymentId(), notice.amountCents(), notice.result());
        return Map.of("result", "accepted");
    }
}
