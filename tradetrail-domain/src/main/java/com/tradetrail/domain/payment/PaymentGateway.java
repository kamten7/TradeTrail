package com.tradetrail.domain.payment;

/**
 * 渠道端口定义通知验签与投递，不让领域依赖具体 HTTP 实现。
 */
public interface PaymentGateway {
    record Notice(String eventId, String paymentId, long amountCents, String result) {
    }

    String channel();

    Notice verify(String raw, String timestamp, String signature);

    void deliver(Notice notice);
}
