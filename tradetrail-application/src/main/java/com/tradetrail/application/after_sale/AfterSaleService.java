package com.tradetrail.application.after_sale;
import com.tradetrail.application.order.OrderService;
import com.tradetrail.application.payment.*;
import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.after_sale.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AfterSaleService {
    private final AfterSaleStore cases;
    private final OrderService orders;
    private final PaymentService payments;
    private final int days;
    public AfterSaleService(AfterSaleStore cases,OrderService orders,PaymentService payments,@Value("${tradetrail.after-sale-days:15}") int days){
        this.cases=cases;this.orders=orders;this.payments=payments;this.days=days;
    }
    @Transactional public AfterSale apply(long orderId,String customer,String reason){
        var o=orders.lock(orderId);orders.get(orderId,customer);
        if(reason==null||reason.isBlank()||reason.length()>500)throw new TradeFailure("INVALID_REASON","售后原因须为 1 至 500 字");
        var prior=cases.byOrder(orderId);if(prior.isPresent())return prior.get();
        if(!List.of("AWAITING_SHIPMENT","SHIPPED","COMPLETED").contains(o.status()))throw new TradeFailure("ORDER_TRANSITION_DENIED","当前订单不可售后",o.status(),o.allowedActions());
        if(o.completedAt()!=null&&o.completedAt().plusDays(days).isBefore(orders.now()))throw new TradeFailure("AFTER_SALE_EXPIRED","已超过配置的售后申请期限");
        orders.hold(orderId,true);
        var result=cases.create(orderId,customer,reason.strip(),"AWAITING_SHIPMENT".equals(o.status())?"REFUND_ONLY":"RETURN_REFUND");
        cases.action(result.id(),"APPLIED",customer,reason);return result;
    }
    public AfterSale get(long order,String customer){orders.get(order,customer);return cases.byOrder(order).orElseThrow(()->new TradeFailure("AFTER_SALE_NOT_FOUND","售后不存在"));}
    public AfterSale find(long id){return cases.find(id).orElseThrow(()->new TradeFailure("AFTER_SALE_NOT_FOUND","售后不存在"));}
    public List<AfterSale> list(){return cases.list();}
    @Transactional public AfterSale decide(long id,boolean approve,String reason,String actor){
        var c=find(id);orders.lock(c.orderId());c=find(id);
        if(reason==null||reason.isBlank()||reason.length()>500)throw new TradeFailure("INVALID_REASON","请填写审核原因");
        require(c,"APPLIED");
        String status;
        if(!approve){status="REJECTED";orders.hold(c.orderId(),false);}
        else if("RETURN_REFUND".equals(c.type()))status="WAITING_RETURN";
        else {status="REFUNDING";payments.requestRefund(c.orderId(),id,"AFTER_SALE");}
        cases.update(id,status,reason,c.returnTrackingNo(),false);cases.action(id,status,actor,reason);return find(id);
    }
    @Transactional public AfterSale returnParcel(long id,String customer,String tracking){
        var c=find(id);orders.lock(c.orderId());orders.get(c.orderId(),customer);c=find(id);
        if(tracking==null||tracking.isBlank()||tracking.length()>100)throw new TradeFailure("INVALID_RETURN","退货运单号不合法");
        require(c,"WAITING_RETURN");
        cases.update(id,"RETURN_IN_TRANSIT",c.decisionReason(),tracking.strip(),false);
        cases.action(id,"RETURN_IN_TRANSIT",customer,tracking);return find(id);
    }
    @Transactional public AfterSale receiveReturn(long id,boolean qualified,String actor){
        var c=find(id);orders.lock(c.orderId());c=find(id);require(c,"RETURN_IN_TRANSIT");
        if(qualified)orders.restock(c.orderId());
        cases.action(id,"RETURN_RECEIVED",actor,qualified?"质检合格，已回补库存":"质检不合格，不回补库存");
        payments.requestRefund(c.orderId(),id,"AFTER_SALE");
        cases.update(id,"REFUNDING",c.decisionReason(),c.returnTrackingNo(),qualified);return find(id);
    }
    @Transactional public void refunded(String refundId){
        var refund=payments.refund(refundId);
        if(!"SUCCEEDED".equals(refund.status()))throw new TradeFailure("REFUND_PENDING","退款未成功");
        if(refund.caseId()==null)return;
        var c=find(refund.caseId());orders.lock(c.orderId());c=find(c.id());
        if("REFUNDED".equals(c.status()))return;
        require(c,"REFUNDING");
        orders.refunded(c.orderId(),"REFUND_ONLY".equals(c.type()));
        cases.update(c.id(),"REFUNDED",c.decisionReason(),c.returnTrackingNo(),c.restocked()||"REFUND_ONLY".equals(c.type()));
        cases.action(c.id(),"REFUNDED","payment",refundId);
    }
    private void require(AfterSale c,String expected){if(!expected.equals(c.status()))throw new TradeFailure("AFTER_SALE_TRANSITION_DENIED","售后状态不允许此操作",c.status(),List.of());}
}
