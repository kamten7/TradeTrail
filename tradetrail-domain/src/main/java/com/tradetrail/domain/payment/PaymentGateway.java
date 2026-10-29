package com.tradetrail.domain.payment;

public interface PaymentGateway {
    record Notice(String eventId, String paymentId, long amountCents, String result) {
    }

    String channel();

    Notice verify(String raw, String timestamp, String signature);

    void deliver(Notice notice);
}
