package com.tradetrail.domain.payment;

/**
 * 整单退款沿稳定退款号重试，避免重复外部效果。
 */
public record RefundAttempt(String id, String paymentId, long orderId, Long caseId, long amountCents, String status,
        String reason, int attempts, String lastError) {
}
