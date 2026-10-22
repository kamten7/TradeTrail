package com.tradetrail.persistence.order;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tradetrail.domain.order.Order;
import com.tradetrail.domain.order.OrderStore;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisOrderStore implements OrderStore {
    private final OrderMapper mapper;

    public MyBatisOrderStore(OrderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Order create(String customer, long productId, int quantity, long unitPriceCents, String requestId) {
        OrderRow row = new OrderRow();
        row.customer = customer;
        row.productId = productId;
        row.quantity = quantity;
        row.unitPriceCents = unitPriceCents;
        row.totalCents = Math.multiplyExact(unitPriceCents, quantity);
        row.status = "PENDING_PAYMENT";
        row.requestId = requestId;
        mapper.insert(row);
        return find(row.id).orElseThrow();
    }

    @Override
    public Optional<Order> find(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::model);
    }

    @Override
    public Optional<Order> findByTrackingNo(String trackingNo) {
        return Optional.ofNullable(mapper.selectOne(new QueryWrapper<OrderRow>()
                .eq("tracking_no", trackingNo))).map(this::model);
    }

    @Override
    public Optional<Order> findByRequest(String customer, String requestId) {
        return Optional.ofNullable(mapper.selectOne(new QueryWrapper<OrderRow>()
                .eq("customer", customer).eq("request_id", requestId))).map(this::model);
    }

    @Override
    public List<Order> listForCustomer(String customer) {
        return mapper.selectList(new QueryWrapper<OrderRow>()
                .eq("customer", customer).orderByDesc("id")).stream().map(this::model).toList();
    }

    @Override
    public List<Order> listAll() {
        return mapper.selectList(new QueryWrapper<OrderRow>().orderByDesc("id"))
                .stream().map(this::model).toList();
    }

    @Override
    public boolean transition(long id, String expectedStatus, String nextStatus) {
        return mapper.transition(id, expectedStatus, nextStatus) == 1;
    }

    @Override
    public boolean ship(long id, String trackingNo) {
        return mapper.ship(id, trackingNo) == 1;
    }

    private Order model(OrderRow row) {
        return new Order(row.id, row.customer, row.productId, row.quantity, row.unitPriceCents,
                row.totalCents, row.status, row.requestId, row.trackingNo, row.createdAt);
    }
}
