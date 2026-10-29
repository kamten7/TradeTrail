package com.tradetrail.domain.after_sale;

import java.time.LocalDateTime;

public record AfterSale(Long id, long orderId, String customer, String reason, String status, String type,
        String decisionReason, String returnTrackingNo, boolean restocked, LocalDateTime createdAt) {

}
