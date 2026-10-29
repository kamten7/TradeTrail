package com.tradetrail.application.integration;

import com.tradetrail.application.after_sale.AfterSaleService;
import com.tradetrail.application.payment.PaymentService;
import com.tradetrail.domain.integration.OutboxStore;
import com.tradetrail.domain.payment.PaymentGateway;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class OutboxDispatcher {
    private final OutboxStore store;
    private final PaymentService payments;
    private final AfterSaleService cases;
    private final EventService events;
    private final Map<String, PaymentGateway> gateways;

    public OutboxDispatcher(OutboxStore store, PaymentService payments, AfterSaleService cases, EventService events,
            List<PaymentGateway> gateways) {
        this.store = store;
        this.payments = payments;
        this.cases = cases;
        this.events = events;
        this.gateways = gateways.stream()
                .collect(Collectors.toUnmodifiableMap(PaymentGateway::channel, Function.identity()));
        if (!this.gateways.containsKey("MOCK")) {
            throw new IllegalStateException("缺少默认支付渠道");
        }
    }

    public void dispatch() {
        for (var event : store.due()) {
            if (!store.claim(event.eventId())) {
                continue;
            }
            try {
                switch (event.type()) {
                    case "PAYMENT_NOTICE" -> {
                        var p = payments.find(event.aggregateId());
                        gateways.get(p.channel())
                                .deliver(new PaymentGateway.Notice(event.eventId(), p.id(), p.amountCents(),
                                        event.payload()));
                    }
                    case "REFUND_EXECUTE" -> payments.executeRefund(event.aggregateId());
                    case "REFUND_SUCCEEDED" -> cases.refunded(event.aggregateId());
                    case "ORDER_CHANGED" -> events.consume("order-audit", event.eventId());
                    default -> throw new IllegalStateException("未知事件类型");
                }
                store.success(event.eventId());
            } catch (Exception error) {
                store.failure(event.eventId(), event.attempts() + 1,
                        error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()));
            }
        }
    }
}
