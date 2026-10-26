package com.tradetrail.application.payment;
import com.tradetrail.domain.payment.*;
import java.util.Optional;
import org.springframework.stereotype.Service;
@Service
public class PaymentQueryService {
    private final PaymentStore store;
    public PaymentQueryService(PaymentStore store){this.store=store;}
    public boolean received(long order){return store.byOrder(order).map(p->"SUCCEEDED".equals(p.providerStatus())).orElse(false);}
    public Optional<PaymentAttempt> payment(long order){return store.byOrder(order);}
    public Optional<RefundAttempt> refund(long order){return store.byOrder(order).flatMap(p->store.refundForPayment(p.id()));}
}
