package com.tradetrail.domain.order;

import java.time.LocalDateTime;

public record Order(Long id, String customer, long productId, int quantity, long unitPriceCents,
                    long totalCents, String status, String requestId, String trackingNo, LocalDateTime createdAt) {
}
