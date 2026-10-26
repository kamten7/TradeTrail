package com.tradetrail.domain.payment;
public record PaymentAttempt(String id,long orderId,String channel,long amountCents,String status,String providerStatus) {}
