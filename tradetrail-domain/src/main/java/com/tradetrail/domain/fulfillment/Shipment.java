package com.tradetrail.domain.fulfillment;
import java.time.LocalDateTime;
public record Shipment(long orderId,String carrier,String trackingNo,String status,int progress,
                       LocalDateTime lastOccurredAt,LocalDateTime deliveredAt){}
