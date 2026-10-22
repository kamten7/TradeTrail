package com.tradetrail.domain.payment;

public interface PaymentStore {
    boolean recordNotice(String eventId, long orderId);
}
