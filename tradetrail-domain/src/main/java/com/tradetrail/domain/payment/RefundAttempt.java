package com.tradetrail.domain.payment;

public record RefundAttempt(String id, String paymentId, long orderId, Long caseId, long amountCents, String status,
        String reason, int attempts, String lastError) {
}
