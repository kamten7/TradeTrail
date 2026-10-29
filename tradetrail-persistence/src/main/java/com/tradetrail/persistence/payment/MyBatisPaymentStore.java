package com.tradetrail.persistence.payment;

import com.tradetrail.domain.payment.PaymentAttempt;
import com.tradetrail.domain.payment.PaymentStore;
import com.tradetrail.domain.payment.RefundAttempt;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisPaymentStore implements PaymentStore {
    private final PaymentMapper mapper;
    private final PaymentNoticeMapper notices;

    public MyBatisPaymentStore(PaymentMapper mapper, PaymentNoticeMapper notices) {
        this.mapper = mapper;
        this.notices = notices;
    }

    public Optional<PaymentAttempt> byOrder(long id) {
        return Optional.ofNullable(mapper.byOrder(id));
    }

    public Optional<PaymentAttempt> find(String id) {
        return Optional.ofNullable(mapper.find(id));
    }

    public void create(PaymentAttempt a) {
        mapper.create(a);
    }

    public void providerResult(String id, String status) {
        mapper.provider(id, status);
    }

    public void status(String id, String status) {
        mapper.status(id, status);
    }

    public boolean recordNotice(String id, long order) {
        if (notices.count(id) > 0) {
            return false;
        }
        return notices.insertNotice(id, order) == 1;
    }

    public Optional<RefundAttempt> refund(String id) {
        return Optional.ofNullable(mapper.refund(id));
    }

    public Optional<RefundAttempt> refundForPayment(String id) {
        return Optional.ofNullable(mapper.refundForPayment(id));
    }

    public List<RefundAttempt> failedRefunds() {
        return mapper.failedRefunds();
    }

    public void createRefund(RefundAttempt r) {
        mapper.createRefund(r);
    }

    public void refundStatus(String id, String status, String error) {
        mapper.refundStatus(id, status, error);
    }
}
