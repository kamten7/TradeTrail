package com.tradetrail.domain.order;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
public interface OrderStore {
    record StatusLog(String fromStatus,String toStatus,String event,String actor,String commandId,String traceId,LocalDateTime occurredAt){}
    Order create(String customer,List<OrderItem> items,String address,String requestId,int paymentMinutes);
    Optional<Order> find(long id);
    Optional<Order> lock(long id);
    Optional<Order> findByTrackingNo(String trackingNo);
    Optional<Order> findByRequest(String customer,String requestId);
    List<Order> listForCustomer(String customer);
    List<Order> listAll();
    boolean transition(Order order,String nextStatus,String trackingNo);
    void hold(Order order,boolean hold);
    void log(long id,String from,String to,String event,String actor,String commandId,String traceId);
    List<StatusLog> history(long id);
    List<Long> expired();
    LocalDateTime now();
}
