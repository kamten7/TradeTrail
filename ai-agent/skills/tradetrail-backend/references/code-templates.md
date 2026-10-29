# TradeTrail 交易实现与测试参考

本页直接引用真实代码，避免另写不可编译或与业务状态冲突的模板。阅读对应实现后再扩展，不把简化伪代码复制进项目。

## 1. 下单与库存

- [OrderService.create](../../../../tradetrail-application/src/main/java/com/tradetrail/application/order/OrderService.java)：参数、排序、命令锁、快照、库存和日志。
- [MyBatisStockStore](../../../../tradetrail-persistence/src/main/java/com/tradetrail/persistence/inventory/MyBatisStockStore.java)：预留状态转换与流水。
- [StockMapper](../../../../tradetrail-persistence/src/main/java/com/tradetrail/persistence/inventory/StockMapper.java)：条件更新并返回行数。

```sql
UPDATE stock
SET available = available - #{qty},
    reserved = reserved + #{qty},
    version = version + 1
WHERE product_id = #{id} AND available >= #{qty}
```

该 SQL 只完成一个库存动作，还需要同事务的预留记录、流水和订单。重放成功订单必须直接返回已有结果，不能再次执行预留循环。

## 2. 幂等

- [IdempotencyFilter](../../../../tradetrail-api/src/main/java/com/tradetrail/api/IdempotencyFilter.java)：身份与请求键、原始方法/路径/正文指纹、成功 HTTP 响应重放。
- [CommandService](../../../../tradetrail-application/src/main/java/com/tradetrail/application/integration/CommandService.java) 和 [MyBatisCommandStore](../../../../tradetrail-persistence/src/main/java/com/tradetrail/persistence/integration/MyBatisCommandStore.java)：命令锁与指纹冲突。
- [http.ts](../../../../frontend/shared/http.ts)：网络结果不确定时保留 sessionStorage 请求键。

HTTP 业务幂等、直接应用命令幂等和回调事件去重是不同层次，不可只保留其中一种。原始 JSON 的空白或字段顺序变化也会改变 HTTP 指纹。

## 3. 状态与竞争

- [OrderState](../../../../tradetrail-domain/src/main/java/com/tradetrail/domain/order/OrderState.java)：真实枚举与事件守卫。
- [OrderMapper](../../../../tradetrail-persistence/src/main/java/com/tradetrail/persistence/order/OrderMapper.java)：行锁、预期状态与版本条件更新。
- [PaymentService](../../../../tradetrail-application/src/main/java/com/tradetrail/application/payment/PaymentService.java)：通知锁后重读、迟到支付退款。

不使用旧的 `PAID/CANCELLED/AFTER_SALE` 状态新建业务。先判断重复事实，再按当前状态执行；已完成订单退款保持完成，售后状态独立更新。

## 4. outbox 与恢复

- [EventService](../../../../tradetrail-application/src/main/java/com/tradetrail/application/integration/EventService.java)：发布与消费事实。
- [OutboxDispatcher](../../../../tradetrail-application/src/main/java/com/tradetrail/application/integration/OutboxDispatcher.java)：类型分发。
- [OutboxMapper](../../../../tradetrail-persistence/src/main/java/com/tradetrail/persistence/integration/OutboxMapper.java)：领取租约与成功/失败写回。

实际状态为 `PENDING/PROCESSING/SENT/FAILED`，不是 `MANUAL`。领取不保证远端效果只发生一次；验证重复回调、执行成功但确认丢失、租约到期与人工重试，不以异常被捕获作为恢复正确的证据。

## 5. 测试入口与断言

| 参考 | 观察目标 |
|---|---|
| [CreateOrderApiTest](../../../../tradetrail-boot/src/test/java/com/tradetrail/api/CreateOrderApiTest.java) | 成功响应、原键同订单、库存不足 |
| [TransactionRegressionTest](../../../../tradetrail-boot/src/test/java/com/tradetrail/api/TransactionRegressionTest.java) | 并发同键、购物车加量、售后阻断发货 |
| [TransactionLifecycleTest](../../../../tradetrail-boot/src/test/java/com/tradetrail/api/TransactionLifecycleTest.java) | 多商品回滚、快照、100 抢 10、迟到支付、物流及退款 |
| [MySqlTransactionIT](../../../../tradetrail-boot/src/test/java/com/tradetrail/api/MySqlTransactionIT.java) | 同一事务场景在真实 MySQL 验证 |

接口测试至少考虑成功、非法输入、角色及归属、业务拒绝和重复执行。具体类中的已有用例不代表这些维度全部覆盖；新增和修改时按根规范补足，避免空测试方法或复制实现作为断言。

测试命令、报告和环境隔离统一见[测试与调试](../../../../docs/testing.md)。
