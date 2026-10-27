package com.tradetrail.api;
import com.tradetrail.application.catalog.CatalogService;
import com.tradetrail.application.order.OrderService;
import com.tradetrail.application.payment.*;
import com.tradetrail.application.after_sale.AfterSaleService;
import com.tradetrail.application.fulfillment.FulfillmentService;
import com.tradetrail.application.integration.*;
import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.fulfillment.Tracking;
import com.tradetrail.domain.order.Order;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
class TransactionLifecycleTest extends ApiTestBase {
    @Autowired CatalogService catalog;
    @Autowired OrderService orders;
    @Autowired PaymentService payments;
    @Autowired PaymentQueryService paymentQuery;
    @Autowired AfterSaleService cases;
    @Autowired FulfillmentService fulfillment;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired JdbcTemplate jdbc;
    long productWithStock(int stock){var p=catalog.create("快照商品","规格：标准",1299,stock);catalog.publish(p.id());return p.id();}
    Order pending(int stock){return orders.create(UUID.randomUUID().toString(),productWithStock(stock),1,UUID.randomUUID().toString());}
    Order paid(int stock){
        var o=pending(stock);var payment=payments.create(o.id(),o.customer());
        payments.simulate(payment.id(),o.customer(),true);
        payments.receive("test-"+payment.id(),payment.id(),payment.amountCents(),"SUCCEEDED");
        return orders.get(o.id(),o.customer());
    }
    @Test void resumedShipmentPreservesDispatchProgress(){
        var o=paid(2);var shipped=fulfillment.ship(o.id());var now=LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(3);
        fulfillment.receive(new Tracking(UUID.randomUUID().toString(),shipped.trackingNo(),"OUT_FOR_DELIVERY","派送点",now));
        fulfillment.receive(new Tracking(UUID.randomUUID().toString(),shipped.trackingNo(),"EXCEPTION","派送点",now.plusMinutes(1)));
        fulfillment.receive(new Tracking(UUID.randomUUID().toString(),shipped.trackingNo(),"RESUMED","派送点",now.plusMinutes(2)));
        var summary=fulfillment.shipment(o.id(),o.customer()).orElseThrow();
        assertEquals("OUT_FOR_DELIVERY",summary.status());assertEquals(3,summary.progress());
    }
    @Test void autoConfirmationScansPastTheFirstBatch(){
        for(int i=0;i<101;i++){
            var o=paid(1);fulfillment.ship(o.id());
            jdbc.update("UPDATE shipment SET delivered_at=TIMESTAMPADD(DAY,-8,CURRENT_TIMESTAMP) WHERE order_id=?",o.id());
        }
        var first=fulfillment.autoConfirmDue(7,0);
        assertEquals(100,first.size());
        var second=fulfillment.autoConfirmDue(7,first.getLast());
        assertFalse(second.isEmpty());assertTrue(second.getFirst()>first.getLast());
    }
    void refund(long order){var r=paymentQuery.refund(order).orElseThrow();payments.executeRefund(r.id());cases.refunded(r.id());}
    @Test void hundredBuyersCompeteForTenUnits()throws Exception{
        long product=productWithStock(10);
        try(var pool=Executors.newFixedThreadPool(20)){
            var tasks=IntStream.range(0,100).<Callable<Boolean>>mapToObj(i->()->{
                try{orders.create("buyer-"+i,product,1,UUID.randomUUID().toString());return true;}
                catch(TradeFailure e){assertEquals("OUT_OF_STOCK",e.code());return false;}
            }).toList();
            int success=0;for(var result:pool.invokeAll(tasks))if(result.get())success++;
            assertEquals(10,success);
        }
        assertEquals(0,catalog.get(product,true).available());
        assertEquals(10,jdbc.queryForObject("SELECT reserved FROM stock WHERE product_id=?",Integer.class,product));
        assertEquals(10,jdbc.queryForObject("SELECT COUNT(*) FROM sales_order WHERE product_id=?",Integer.class,product));
    }
    @Test void multiItemSnapshotSurvivesPriceChangeAndCancelRestoresAll(){
        long a=productWithStock(5),b=productWithStock(8);
        var o=orders.create("snapshot",List.of(new OrderService.Line(b,2,1299L),new OrderService.Line(a,1,1299L)),"香港地址",UUID.randomUUID().toString());
        catalog.update(a,"新名称","新规格",2500,false);
        var stored=orders.get(o.id(),"snapshot");
        assertEquals(3897,stored.totalCents());assertEquals("快照商品",stored.items().getFirst().productName());
        orders.cancel(o.id(),"snapshot");
        assertEquals(5,catalog.get(a,true).available());assertEquals(8,catalog.get(b,true).available());
        assertThrows(TradeFailure.class,()->orders.create("snapshot",a,1,UUID.randomUUID().toString()));
    }
    @Test void failedSecondItemRollsBackFirstReservationAndOrder(){
        long a=productWithStock(5),b=productWithStock(0);
        String key=UUID.randomUUID().toString();
        assertThrows(TradeFailure.class,()->orders.create("rollback",List.of(new OrderService.Line(a,1,1299L),new OrderService.Line(b,1,1299L)),"地址",key));
        assertEquals(5,catalog.get(a,true).available());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sales_order WHERE request_id=?",Integer.class,key));
    }
    @Test void expiryThenLatePaymentCreatesOneCompensationAndDoesNotSellStock(){
        var o=pending(3);var payment=payments.create(o.id(),o.customer());
        jdbc.update("UPDATE sales_order SET pay_deadline=TIMESTAMPADD(MINUTE,-1,CURRENT_TIMESTAMP) WHERE id=?",o.id());
        orders.expire(o.id());assertEquals("CLOSED_UNPAID",orders.find(o.id()).status());
        payments.simulate(payment.id(),o.customer(),true);
        payments.receive("late-"+payment.id(),payment.id(),o.totalCents(),"SUCCEEDED");
        payments.receive("late-repeat-"+payment.id(),payment.id(),o.totalCents(),"SUCCEEDED");
        var r=paymentQuery.refund(o.id()).orElseThrow();
        payments.executeRefund(r.id());payments.executeRefund(r.id());
        assertEquals("SUCCEEDED",payments.refund(r.id()).status());
        assertEquals(3,catalog.get(o.productId(),true).available());
        assertEquals(0,jdbc.queryForObject("SELECT sold FROM stock WHERE product_id=?",Integer.class,o.productId()));
    }
    @Test void receivedPaymentPreventsTimeoutClose(){
        var o=pending(2);var p=payments.create(o.id(),o.customer());payments.simulate(p.id(),o.customer(),true);
        jdbc.update("UPDATE sales_order SET pay_deadline=TIMESTAMPADD(MINUTE,-1,CURRENT_TIMESTAMP) WHERE id=?",o.id());
        orders.expire(o.id());assertEquals("PENDING_PAYMENT",orders.find(o.id()).status());
        payments.receive("notice-"+p.id(),p.id(),p.amountCents(),"SUCCEEDED");
        assertEquals("AWAITING_SHIPMENT",orders.find(o.id()).status());
    }
    @Test void concurrentVerifiedNoticesConfirmStockOnlyOnce()throws Exception{
        var o=pending(3);var p=payments.create(o.id(),o.customer());payments.simulate(p.id(),o.customer(),true);
        try(var pool=Executors.newFixedThreadPool(8)){
            var tasks=IntStream.range(0,8).<Callable<Void>>mapToObj(i->()->{payments.receive("same-"+p.id(),p.id(),p.amountCents(),"SUCCEEDED");return null;}).toList();
            for(var result:pool.invokeAll(tasks))result.get();
        }
        assertEquals(1,jdbc.queryForObject("SELECT sold FROM stock WHERE product_id=?",Integer.class,o.productId()));
        assertEquals(0,jdbc.queryForObject("SELECT reserved FROM stock WHERE product_id=?",Integer.class,o.productId()));
    }
    @Test void approvedUnshippedRefundRestoresStockAndRejectionReleasesHold(){
        var o=paid(3);var c=cases.apply(o.id(),o.customer(),"不需要");
        cases.decide(c.id(),false,"拒绝原因","merchant");
        assertFalse(orders.find(o.id()).fulfillmentHold());fulfillment.ship(o.id());
        var second=paid(4);var approved=cases.apply(second.id(),second.customer(),"退款");
        cases.decide(approved.id(),true,"批准","merchant");refund(second.id());refund(second.id());
        assertEquals("CLOSED_REFUNDED",orders.find(second.id()).status());
        assertEquals(4,catalog.get(second.productId(),true).available());assertEquals("REFUNDED",cases.find(approved.id()).status());
    }
    @Test void completedReturnRefundPreservesCompletionAndOnlyQualifiedGoodsRestock(){
        var o=paid(3);fulfillment.ship(o.id());orders.confirm(o.id(),o.customer());
        var c=cases.apply(o.id(),o.customer(),"退货");cases.decide(c.id(),true,"批准退货","merchant");
        cases.returnParcel(c.id(),o.customer(),"RETURN-001");
        cases.receiveReturn(c.id(),false,"merchant");refund(o.id());
        assertEquals("COMPLETED",orders.find(o.id()).status());assertEquals(2,catalog.get(o.productId(),true).available());
        assertFalse(cases.find(c.id()).restocked());
    }
    @Test void shipmentAndAfterSaleShareOrderLock()throws Exception{
        var o=paid(2);
        try(var pool=Executors.newFixedThreadPool(2)){
            var gate=new CountDownLatch(1);
            var ship=pool.submit(()->{gate.await();try{fulfillment.ship(o.id());return true;}catch(TradeFailure e){return false;}});
            var apply=pool.submit(()->{gate.await();return cases.apply(o.id(),o.customer(),"竞争售后");});gate.countDown();
            boolean shipped=ship.get();var c=apply.get();
            assertTrue(orders.find(o.id()).fulfillmentHold());
            assertEquals(shipped?"RETURN_REFUND":"REFUND_ONLY",c.type());
            if(!shipped)assertEquals("AWAITING_SHIPMENT",orders.find(o.id()).status());
        }
    }
    @Test void trackingDeduplicatesAndLateEventsDoNotRegressDeliveredSummary(){
        var o=paid(2);var shipped=fulfillment.ship(o.id());var now=LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(2);
        var delivered=new Tracking(UUID.randomUUID().toString(),shipped.trackingNo(),"DELIVERED","收件点",now);
        fulfillment.receive(delivered);fulfillment.receive(delivered);
        fulfillment.receive(new Tracking(UUID.randomUUID().toString(),shipped.trackingNo(),"IN_TRANSIT","分拨中心",now.minusMinutes(10)));
        assertEquals(2,fulfillment.timeline(o.id(),o.customer()).size());
        assertEquals("DELIVERED",fulfillment.shipment(o.id(),o.customer()).orElseThrow().status());
        assertEquals("SHIPPED",orders.find(o.id()).status());
    }
    @Test void expiredOutboxLeaseIsRecoveredAndInboxIsIdempotent(){
        var o=pending(2);
        jdbc.update("UPDATE outbox_event SET state='PROCESSING',created_at=TIMESTAMPADD(DAY,-1,CURRENT_TIMESTAMP),next_attempt_at=TIMESTAMPADD(MINUTE,-2,CURRENT_TIMESTAMP) WHERE type='ORDER_CHANGED' AND aggregate_id=?",o.id().toString());
        dispatcher.dispatch();dispatcher.dispatch();
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM inbox_event i JOIN outbox_event o ON o.event_id=i.event_id WHERE o.aggregate_id=? AND o.type='ORDER_CHANGED'",Integer.class,o.id().toString()));
    }
}
