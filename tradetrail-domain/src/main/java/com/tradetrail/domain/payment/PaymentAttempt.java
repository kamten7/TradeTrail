package com.tradetrail.domain.payment;

/**
 * 支付处理状态与模拟渠道结果分别记录。
 */
public record PaymentAttempt(String id, long orderId, String channel, long amountCents, String status,
        String providerStatus) {
}
