package com.tradetrail.domain.payment;
import java.util.Optional;
public interface PaymentStore {
    Optional<PaymentAttempt> byOrder(long order);
    Optional<PaymentAttempt> find(String id);
    void create(PaymentAttempt attempt);
    void providerResult(String id,String result);
    void status(String id,String status);
    boolean recordNotice(String eventId,long orderId);
    Optional<RefundAttempt> refund(String id);
    Optional<RefundAttempt> refundForPayment(String payment);
    java.util.List<RefundAttempt> failedRefunds();
    void createRefund(RefundAttempt refund);
    void refundStatus(String id,String status,String error);
}
