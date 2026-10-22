package com.tradetrail.domain.after_sale;

public record AfterSale(Long id, long orderId, String customer, String reason, String status) {
}
