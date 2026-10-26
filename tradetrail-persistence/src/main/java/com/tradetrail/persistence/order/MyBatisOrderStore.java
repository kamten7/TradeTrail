package com.tradetrail.persistence.order;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tradetrail.domain.order.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Repository;
@Repository
public class MyBatisOrderStore implements OrderStore {
    private final OrderMapper mapper;
    public MyBatisOrderStore(OrderMapper mapper){this.mapper=mapper;}
    public Order create(String customer,List<OrderItem> items,String address,String requestId,int minutes) {
        OrderRow row=new OrderRow();
        var first=items.getFirst();
        row.customer=customer; row.productId=first.productId(); row.quantity=first.quantity();
        row.unitPriceCents=first.unitPriceCents(); row.totalCents=items.stream().mapToLong(OrderItem::subtotalCents).reduce(0,Math::addExact);
        row.status="PENDING_PAYMENT"; row.requestId=requestId; row.version=0L; row.fulfillmentHold=false;
        row.address=address; row.payDeadline=now().plusMinutes(minutes);
        mapper.insert(row);
        for(var item:items) mapper.item(row.id,item);
        return find(row.id).orElseThrow();
    }
    public Optional<Order> find(long id){return Optional.ofNullable(mapper.selectById(id)).map(this::model);}
    public Optional<Order> lock(long id){return Optional.ofNullable(mapper.lock(id)).map(this::model);}
    public Optional<Order> findByTrackingNo(String tracking){return one(new QueryWrapper<OrderRow>().eq("tracking_no",tracking));}
    public Optional<Order> findByRequest(String customer,String key){return one(new QueryWrapper<OrderRow>().eq("customer",customer).eq("request_id",key));}
    private Optional<Order> one(QueryWrapper<OrderRow> query){return Optional.ofNullable(mapper.selectOne(query)).map(this::model);}
    public List<Order> listForCustomer(String customer){return mapper.selectList(new QueryWrapper<OrderRow>().eq("customer",customer).orderByDesc("id")).stream().map(this::model).toList();}
    public List<Order> listAll(){return mapper.selectList(new QueryWrapper<OrderRow>().orderByDesc("id")).stream().map(this::model).toList();}
    public boolean transition(Order o,String next,String tracking){return mapper.transition(o.id(),o.version(),o.status(),next,tracking)==1;}
    public void hold(Order o,boolean hold){if(mapper.hold(o.id(),o.version(),hold)!=1)throw new IllegalStateException("订单版本冲突");}
    public void log(long id,String from,String to,String event,String actor,String command,String trace){mapper.log(id,from,to,event,actor,command,trace);}
    public List<StatusLog> history(long id){return mapper.history(id);}
    public List<Long> expired(){return mapper.expired();}
    public LocalDateTime now(){return mapper.now();}
    private Order model(OrderRow r){return new Order(r.id,r.customer,r.productId,r.quantity,r.unitPriceCents,r.totalCents,r.status,r.requestId,r.trackingNo,r.createdAt,r.version,r.fulfillmentHold,r.address,r.payDeadline,r.completedAt,mapper.items(r.id));}
}
