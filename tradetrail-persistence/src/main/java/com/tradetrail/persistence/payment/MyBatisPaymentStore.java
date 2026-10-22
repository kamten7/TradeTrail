package com.tradetrail.persistence.payment;

import com.tradetrail.domain.payment.PaymentStore;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisPaymentStore implements PaymentStore {
    private final PaymentNoticeMapper mapper;

    public MyBatisPaymentStore(PaymentNoticeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean recordNotice(String eventId, long orderId) {
        if (mapper.count(eventId) > 0) return false;
        return mapper.insertNotice(eventId, orderId) == 1;
    }
}
