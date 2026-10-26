package com.tradetrail.domain.order;
import java.time.LocalDateTime;
import java.util.List;
public record Order(Long id,String customer,long productId,int quantity,long unitPriceCents,
                    long totalCents,String status,String requestId,String trackingNo,LocalDateTime createdAt,
                    long version,boolean fulfillmentHold,String address,LocalDateTime payDeadline,
                    LocalDateTime completedAt,List<OrderItem> items) {
    public List<String> allowedActions(){return OrderState.valueOf(status).actions(fulfillmentHold);}
}
