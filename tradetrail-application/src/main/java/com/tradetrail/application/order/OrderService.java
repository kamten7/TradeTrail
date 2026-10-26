package com.tradetrail.application.order;
import com.tradetrail.application.catalog.CatalogService;
import com.tradetrail.application.inventory.InventoryService;
import com.tradetrail.application.integration.*;
import com.tradetrail.application.payment.PaymentQueryService;
import com.tradetrail.domain.TradeFailure;
import com.tradetrail.domain.order.*;
import com.tradetrail.domain.order.OrderState.Event;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class OrderService {
    public record Line(long productId,int quantity,Long expectedPriceCents){}
    private final OrderStore orders;
    private final CatalogService catalog;
    private final InventoryService stock;
    private final CommandService commands;
    private final EventService events;
    private final PaymentQueryService payments;
    private final int paymentMinutes;
    public OrderService(OrderStore orders,CatalogService catalog,InventoryService stock,CommandService commands,EventService events,PaymentQueryService payments,
                        @Value("${tradetrail.payment-minutes:30}") int paymentMinutes){
        this.orders=orders;this.catalog=catalog;this.stock=stock;this.commands=commands;this.events=events;this.payments=payments;this.paymentMinutes=paymentMinutes;
    }
    @Transactional public Order create(String customer,long product,int qty,String key){
        return create(customer,List.of(new Line(product,qty,null)),"演示地址",key);
    }
    @Transactional public Order create(String customer,List<Line> lines,String address,String key){
        if(key==null||key.isBlank()||key.length()>100||address==null||address.isBlank()||address.length()>500||lines==null||lines.isEmpty()||lines.size()>50)
            throw new TradeFailure("INVALID_ORDER","订单明细、地址或请求号不合法");
        var sorted=lines.stream().sorted(Comparator.comparingLong(Line::productId)).toList();
        if(sorted.stream().map(Line::productId).distinct().count()!=sorted.size())throw new TradeFailure("INVALID_ORDER","商品不能重复");
        for(var line:sorted)if(line.quantity()<1||line.quantity()>10000)throw new TradeFailure("INVALID_ORDER","购买数量超出范围");
        commands.lock("order:"+customer+":"+key,sorted.toString()+address);
        var prior=orders.findByRequest(customer,key);
        if(prior.isPresent())return prior.get();
        var items=new ArrayList<OrderItem>();
        for(var line:sorted){
            var p=catalog.lockQuote(line.productId());
            if(!p.published())throw new TradeFailure("PRODUCT_UNAVAILABLE","商品已下架");
            if(line.expectedPriceCents()!=null&&line.expectedPriceCents()!=p.priceCents())throw new TradeFailure("PRICE_CHANGED","商品价格已变化，请重新确认结算");
            items.add(new OrderItem(p.id(),p.name(),line.quantity(),p.priceCents()));
        }
        var order=orders.create(customer,items,address.strip(),key,paymentMinutes);
        stock.reserve(order.id(),items);
        log(order,"","PENDING_PAYMENT","PLACE_ORDER",customer,key);
        return order;
    }
    public Order get(long id,String customer){var o=find(id);owned(o,customer);return o;}
    public Order find(long id){return orders.find(id).orElseThrow(()->new TradeFailure("ORDER_NOT_FOUND","订单不存在"));}
    @Transactional public Order lock(long id){return orders.lock(id).orElseThrow(()->new TradeFailure("ORDER_NOT_FOUND","订单不存在"));}
    public List<Order> list(String customer){return orders.listForCustomer(customer);}
    public List<Order> listAll(){return orders.listAll();}
    @Transactional public Order cancel(long id,String customer){
        var o=lock(id);owned(o,customer);
        if("CLOSED_UNPAID".equals(o.status()))return o;
        if(payments.received(id))throw new TradeFailure("PAYMENT_RECEIVED","已收到支付，等待通知处理");
        change(o,Event.USER_CANCEL,customer,null);
        stock.release(id,o.items());return find(id);
    }
    @Transactional public Order paid(long id){
        var o=lock(id);change(o,Event.PAYMENT_CONFIRMED,"payment",null);
        stock.confirm(id,o.items());return find(id);
    }
    @Transactional public Order ship(long id,String tracking){
        var o=lock(id);change(o,Event.SHIPMENT_ACCEPTED,"merchant",tracking);return find(id);
    }
    @Transactional public Order confirm(long id,String customer){
        var o=lock(id);owned(o,customer);
        if("COMPLETED".equals(o.status()))return o;
        change(o,Event.BUYER_CONFIRM,customer,null);return find(id);
    }
    @Transactional public void autoConfirm(long id){var o=lock(id);if("SHIPPED".equals(o.status())&&!o.fulfillmentHold())change(o,Event.AUTO_CONFIRM,"scheduler",null);}
    @Transactional public void hold(long id,boolean hold){var o=lock(id);if(o.fulfillmentHold()!=hold){orders.hold(o,hold);log(o,o.status(),o.status(),hold?"HOLD_FULFILLMENT":"RELEASE_HOLD","after_sale",UUID.randomUUID().toString());}}
    @Transactional public void refunded(long id,boolean restock){
        var o=lock(id);change(o,Event.FULL_REFUND_SUCCEEDED,"refund",null);
        if(restock)stock.restock(id,o.items());
        hold(id,false);
    }
    @Transactional public void restock(long id){var o=lock(id);stock.restock(id,o.items());}
    public java.time.LocalDateTime now(){return orders.now();}
    public List<Long> expired(){return orders.expired();}
    @Transactional public void expire(long id){
        var o=lock(id);
        if(!"PENDING_PAYMENT".equals(o.status())||o.payDeadline().isAfter(now())||payments.received(id))return;
        change(o,Event.PAY_TIMEOUT,"scheduler",null);stock.release(id,o.items());
    }
    public List<OrderStore.StatusLog> history(long id,String customer){get(id,customer);return orders.history(id);}
    private void change(Order o,Event event,String actor,String tracking){
        String next=OrderState.valueOf(o.status()).next(event,o.fulfillmentHold()).name();
        if(!orders.transition(o,next,tracking==null?o.trackingNo():tracking))throw new TradeFailure("ORDER_CONFLICT","订单已变化，请刷新");
        log(o,o.status(),next,event.name(),actor,UUID.randomUUID().toString());
    }
    private void log(Order o,String from,String to,String event,String actor,String command){
        orders.log(o.id(),from,to,event,actor,command,TraceContext.current());
        events.publish("ORDER_CHANGED",o.id().toString(),event);
    }
    private void owned(Order o,String customer){if(!o.customer().equals(customer))throw new TradeFailure("FORBIDDEN","无权操作此订单");}
}
