# 五类核心代码模板

模板只给结构与关键校验，命名按业务调整；SQL 用 `JdbcClient` 写显式语句。

## 1. 库存条件扣减（下单预留）

```java
@Transactional
public long reserveAndCreateOrder(CreateOrderCommand command, String idempotencyKey) {
    Order order = orderRepository.findByClientRequestId(command.clientRequestId())
            .orElseGet(() -> createOrder(command));

    for (OrderItem item : order.items().sortedBySkuIdAscending()) {
        int rows = jdbcClient.sql("""
                UPDATE stock
                   SET available = available - :qty,
                       reserved  = reserved + :qty,
                       version   = version + 1
                 WHERE sku_id = :skuId
                   AND available >= :qty
                """)
                .param("qty", item.quantity())
                .param("skuId", item.skuId())
                .update();
        if (rows == 0) {
            throw new StockNotEnoughException(item.skuId());
        }
        jdbcClient.sql("""
                INSERT INTO inventory_reservation(order_id, sku_id, qty, state)
                VALUES (:orderId, :skuId, :qty, 'RESERVED')
                """).param("orderId", order.id()).param("skuId", item.skuId()).param("qty", item.quantity()).update();
    }
    return order.id();
}
```

要点：条件写在 `WHERE` 里、检查 `rows`、预留记录同事务、按 skuId 升序。

## 2. 幂等写接口

```java
public OrderResponse create(CreateOrderRequest request, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
        throw new ValidationException("缺少 Idempotency-Key");
    }
    return idempotencyService.execute(idempotencyKey, OrderResponse.class,
            () -> createOrderService.create(request));
}
```

底层靠 `idempotency_record(key, response_json, status)` 唯一索引：先插入占位，冲突时读已有结果返回同一响应；并发冲突用条件更新唤醒等待。

## 3. outbox 事件（业务事实与事件同事务）

```java
@Transactional
public void paySuccess(long orderId, PaymentCallback callback) {
    int rows = jdbcClient.sql("""
            UPDATE orders SET status = 'PAID', version = version + 1
             WHERE id = :id AND status = 'PENDING_PAYMENT'
            """).param("id", orderId).update();
    if (rows == 0) {
        return;
    }
    jdbcClient.sql("""
            INSERT INTO outbox_event(event_id, type, aggregate_id, payload, status, next_retry_at)
            VALUES (:eventId, 'ORDER_PAID', :orderId, :payload, 'PENDING', NOW())
            """)
            .param("eventId", UUID.randomUUID().toString())
            .param("orderId", orderId)
            .param("payload", toJson(orderId))
            .update();
}
```

投递器：`status='PENDING' AND next_retry_at <= NOW()` 取批，发送成功后置 `SENT`；失败记 `retry_count`、退避时间，超阈值转 `MANUAL`。消费方按 `event_id` 写 inbox 去重。

## 4. 订单状态机（守卫 + CAS）

```java
public enum OrderStatus { PENDING_PAYMENT, PAID, SHIPPED, DELIVERED, COMPLETED, CANCELLED, AFTER_SALE }

private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
        PENDING_PAYMENT, Set.of(PAID, CANCELLED),
        PAID,            Set.of(SHIPPED, CANCELLED),
        SHIPPED,         Set.of(DELIVERED),
        DELIVERED,       Set.of(COMPLETED, AFTER_SALE)
);

public void transition(Order order, OrderStatus target) {
    if (!ALLOWED.getOrDefault(order.status(), Set.of()).contains(target)) {
        throw new IllegalTransitionException(order.status(), target);
    }
    int rows = jdbcClient.sql("""
            UPDATE orders SET status = :target, version = version + 1
             WHERE id = :id AND status = :expected
            """).param("target", target.name()).param("id", order.id())
            .param("expected", order.status().name()).update();
    if (rows == 0) {
        throw new ConcurrentTransitionException(order.id());
    }
}
```

要点：转移表是唯一事实、非法转移抛业务异常、CAS 防并发覆盖、状态与流水同事务。

## 5. 接口测试模板（每个接口一个类，五个必测路径）

```java
class CreateOrderApiTest {

    @Test void createsOrderSuccessfully() { /* 201 + 订单体 + 库存已预留 */ }

    @Test void rejectsInvalidQuantity() { /* 400 + code=VALIDATION_ERROR */ }

    @Test void rejectsForeignAddress() { /* 403 + 越权 */ }

    @Test void rejectsWhenStockInsufficient() { /* 409 + allowedActions 不含支付 */ }

    @Test void returnsSameOrderOnRepeatedIdempotencyKey() { /* 两次请求同一订单号 */ }
}
```

回调类接口另加：签名错误、重复事件、乱序事件三条。
