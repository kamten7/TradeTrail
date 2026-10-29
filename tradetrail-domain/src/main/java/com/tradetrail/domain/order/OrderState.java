package com.tradetrail.domain.order;

import com.tradetrail.domain.TradeFailure;
import java.util.List;

public enum OrderState {
    PENDING_PAYMENT, AWAITING_SHIPMENT, SHIPPED, COMPLETED, CLOSED_UNPAID, CLOSED_REFUNDED;

    public enum Event {
        PAYMENT_CONFIRMED, USER_CANCEL, PAY_TIMEOUT, SHIPMENT_ACCEPTED, BUYER_CONFIRM, AUTO_CONFIRM,
        FULL_REFUND_SUCCEEDED
    }

    public OrderState next(Event event, boolean hold) {
        OrderState next = switch (event) {
            case PAYMENT_CONFIRMED -> this == PENDING_PAYMENT ? AWAITING_SHIPMENT : null;
            case USER_CANCEL, PAY_TIMEOUT -> this == PENDING_PAYMENT ? CLOSED_UNPAID : null;
            case SHIPMENT_ACCEPTED -> this == AWAITING_SHIPMENT && !hold ? SHIPPED : null;
            case BUYER_CONFIRM, AUTO_CONFIRM -> this == SHIPPED && !hold ? COMPLETED : null;
            case FULL_REFUND_SUCCEEDED ->
                this == AWAITING_SHIPMENT || this == SHIPPED ? CLOSED_REFUNDED : this == COMPLETED ? COMPLETED : null;
        };
        if (next == null) {
            throw new TradeFailure("ORDER_TRANSITION_DENIED", "当前订单不允许此操作", name(), actions(hold));
        }
        return next;
    }

    public List<String> actions(boolean hold) {
        return switch (this) {
            case PENDING_PAYMENT -> List.of("PAY", "CANCEL");
            case AWAITING_SHIPMENT -> hold ? List.of("VIEW_AFTER_SALE") : List.of("SHIP", "APPLY_AFTER_SALE");
            case SHIPPED -> hold ? List.of("VIEW_TRACKING", "VIEW_AFTER_SALE")
                    : List.of("CONFIRM", "VIEW_TRACKING", "APPLY_AFTER_SALE");
            case COMPLETED -> List.of("APPLY_AFTER_SALE");
            default -> List.of();
        };
    }
}
