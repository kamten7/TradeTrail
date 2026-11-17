package com.tradetrail.domain.fulfillment;

import java.time.LocalDateTime;

/**
 * 运单摘要与轨迹事件分开保存，避免旧节点覆盖当前进度。
 */
public record Shipment(long orderId, String carrier, String trackingNo, String status, int progress,
        LocalDateTime lastOccurredAt, LocalDateTime deliveredAt) {
}
