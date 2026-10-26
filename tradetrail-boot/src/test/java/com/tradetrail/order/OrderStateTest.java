package com.tradetrail.order;
import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.order.OrderState;
import com.tradetrail.domain.order.OrderState.Event;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OrderStateTest {
    @Test void everyStateEventPairHasExplicitOutcome(){
        for(var state:OrderState.values())for(var event:Event.values())for(boolean hold:new boolean[]{false,true}){
            boolean legal=switch(event){
                case PAYMENT_CONFIRMED,USER_CANCEL,PAY_TIMEOUT->state==OrderState.PENDING_PAYMENT;
                case SHIPMENT_ACCEPTED->state==OrderState.AWAITING_SHIPMENT&&!hold;
                case BUYER_CONFIRM,AUTO_CONFIRM->state==OrderState.SHIPPED&&!hold;
                case FULL_REFUND_SUCCEEDED->state==OrderState.AWAITING_SHIPMENT||state==OrderState.SHIPPED||state==OrderState.COMPLETED;
            };
            if(legal)assertNotNull(state.next(event,hold));else assertThrows(TradeFailure.class,()->state.next(event,hold));
        }
    }
    @Test void completedRefundDoesNotReverseFulfillment(){assertEquals(OrderState.COMPLETED,OrderState.COMPLETED.next(Event.FULL_REFUND_SUCCEEDED,true));}
}
