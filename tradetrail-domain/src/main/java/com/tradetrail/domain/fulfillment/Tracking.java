package com.tradetrail.domain.fulfillment;

import java.time.LocalDateTime;

public record Tracking(String eventId, String trackingNo, String status, String location, LocalDateTime occurredAt) {
}
