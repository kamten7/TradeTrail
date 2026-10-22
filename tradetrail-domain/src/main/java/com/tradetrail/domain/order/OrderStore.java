package com.tradetrail.domain.order;

import java.util.List;
import java.util.Optional;

public interface OrderStore {
    Order create(String customer, long productId, int quantity, long unitPriceCents, String requestId);
    Optional<Order> find(long id);
    Optional<Order> findByTrackingNo(String trackingNo);
    Optional<Order> findByRequest(String customer, String requestId);
    List<Order> listForCustomer(String customer);
    List<Order> listAll();
    boolean transition(long id, String expectedStatus, String nextStatus);
    boolean ship(long id, String trackingNo);
}
