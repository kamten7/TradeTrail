package com.tradetrail.application.payment;
import com.tradetrail.application.order.OrderService;
import com.tradetrail.application.integration.*;
import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.payment.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class PaymentService {
    private final PaymentStore store;
    private final OrderService orders;
    private final EventService events;
    private final CommandService commands;
    private final boolean mockEnabled;
    private final int refundFailuresBeforeSuccess;
    public PaymentService(PaymentStore store,OrderService orders,EventService events,CommandService commands,
                          @Value("${tradetrail.mock-payments.enabled:true}") boolean mockEnabled,
                          @Value("${tradetrail.mock-payments.refund-failures-before-success:0}") int refundFailuresBeforeSuccess){
        this.store=store;this.orders=orders;this.events=events;this.commands=commands;this.mockEnabled=mockEnabled;
        if(refundFailuresBeforeSuccess<0)throw new IllegalArgumentException("退款故障演练次数不能为负");
        this.refundFailuresBeforeSuccess=refundFailuresBeforeSuccess;
    }
    @Transactional public PaymentAttempt create(long orderId,String customer){
        requireMock();var o=orders.lock(orderId);orders.get(orderId,customer);
        var existing=store.byOrder(orderId);
        if(existing.isPresent())return existing.get();
        if(!"PENDING_PAYMENT".equals(o.status())||!o.payDeadline().isAfter(orders.now()))throw new TradeFailure("ORDER_TRANSITION_DENIED","订单不可支付",o.status(),o.allowedActions());
        var p=new PaymentAttempt(UUID.randomUUID().toString(),orderId,"MOCK",o.totalCents(),"INITIATED","PENDING");
        store.create(p);return p;
    }
    @Transactional public PaymentAttempt simulate(String id,String customer,boolean success){
        requireMock();var p=find(id);orders.lock(p.orderId());orders.get(p.orderId(),customer);
        p=find(id);
        if("SUCCEEDED".equals(p.providerStatus()))return p;
        if(success){
            store.providerResult(id,"SUCCEEDED");
            events.publish("PAYMENT_NOTICE",id,"SUCCEEDED");
        }else store.providerResult(id,"FAILED");
        return find(id);
    }
    @Transactional public void receive(String eventId,String paymentId,long amount,String result){
        if(eventId==null||eventId.isBlank()||eventId.length()>100||paymentId==null||paymentId.isBlank()||result==null||amount<1)
            throw new TradeFailure("INVALID_PAYMENT_NOTICE","支付通知字段不完整");
        var p=find(paymentId);orders.lock(p.orderId());
        commands.lock("payment-notice:"+eventId,paymentId+":"+amount+":"+result);
        p=find(paymentId);
        if(amount!=p.amountCents()||!"SUCCEEDED".equals(result)||!"SUCCEEDED".equals(p.providerStatus()))
            throw new TradeFailure("INVALID_PAYMENT_NOTICE","支付通知与渠道记录不符");
        if(!store.recordNotice(eventId,p.orderId()))return;
        if("SUCCEEDED".equals(p.status())||"LATE_PAYMENT".equals(p.status()))return;
        var order=orders.find(p.orderId());
        if("PENDING_PAYMENT".equals(order.status())){
            store.status(p.id(),"SUCCEEDED");orders.paid(p.orderId());
        }else if("CLOSED_UNPAID".equals(order.status())){
            store.status(p.id(),"LATE_PAYMENT");requestRefund(p.orderId(),null,"LATE_PAYMENT");
        }else throw new TradeFailure("INVALID_PAYMENT_NOTICE","订单状态与支付不符");
    }
    @Transactional public RefundAttempt requestRefund(long orderId,Long caseId,String reason){
        orders.lock(orderId);
        var p=store.byOrder(orderId).orElseThrow(()->new TradeFailure("PAYMENT_NOT_FOUND","支付不存在"));
        if(!"SUCCEEDED".equals(p.providerStatus()))throw new TradeFailure("REFUND_DENIED","订单未收款");
        var existing=store.refundForPayment(p.id());if(existing.isPresent())return existing.get();
        var refund=new RefundAttempt(UUID.randomUUID().toString(),p.id(),orderId,caseId,p.amountCents(),"REQUESTED",reason,0,null);
        store.createRefund(refund);events.publish("REFUND_EXECUTE",refund.id(),"");return refund;
    }
    @Transactional public void executeRefund(String id){
        var r=refund(id);orders.lock(r.orderId());r=refund(id);
        if(!"REQUESTED".equals(r.status()))return;
        var p=find(r.paymentId());
        if(r.amountCents()!=p.amountCents()||!"SUCCEEDED".equals(p.providerStatus()))throw new TradeFailure("REFUND_DENIED","退款金额与实收不符");
        if(r.attempts()<refundFailuresBeforeSuccess){
            store.refundStatus(id,"FAILED","模拟渠道暂时拒绝退款，请重试");
            return;
        }
        // Mock 的稳定退款号即渠道幂等号；成功记录与后续事实同事务提交。
        store.refundStatus(id,"SUCCEEDED",null);
        events.publish("REFUND_SUCCEEDED",id,r.caseId()==null?"":r.caseId().toString());
    }
    @Transactional public void failRefund(String id,String reason){
        var r=refund(id);orders.lock(r.orderId());if(!"SUCCEEDED".equals(refund(id).status()))store.refundStatus(id,"FAILED",reason);
    }
    @Transactional public RefundAttempt retryRefund(String id){
        var r=refund(id);orders.lock(r.orderId());r=refund(id);
        if("FAILED".equals(r.status())){store.refundStatus(id,"REQUESTED",null);events.publish("REFUND_EXECUTE",id,"");}
        return refund(id);
    }
    public PaymentAttempt find(String id){return store.find(id).orElseThrow(()->new TradeFailure("PAYMENT_NOT_FOUND","支付不存在"));}
    public RefundAttempt refund(String id){return store.refund(id).orElseThrow(()->new TradeFailure("REFUND_NOT_FOUND","退款不存在"));}
    private void requireMock(){if(!mockEnabled)throw new TradeFailure("CHANNEL_DISABLED","模拟支付已禁用");}
}
