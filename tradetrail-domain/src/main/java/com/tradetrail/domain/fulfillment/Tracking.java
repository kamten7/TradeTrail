package com.tradetrail.domain.fulfillment;

import java.time.LocalDateTime;

/**
 * 物流事件以事件 ID 去重，发生时间与接收时间含义不同。
 */
public record Tracking(String eventId, String trackingNo, String status, String location, LocalDateTime occurredAt) {
}
