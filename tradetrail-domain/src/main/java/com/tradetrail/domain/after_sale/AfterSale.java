package com.tradetrail.domain.after_sale;

import java.time.LocalDateTime;

/**
 * 售后状态独立于订单主状态，回补标记记录实物处理结果。
 */
public record AfterSale(Long id, long orderId, String customer, String reason, String status, String type,
        String decisionReason, String returnTrackingNo, boolean restocked, LocalDateTime createdAt) {

}
