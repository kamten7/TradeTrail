# TradeTrail 分层与跨域速查

依赖图和所有权的完整定义见[业务分层](../../../../docs/design/02-业务分层设计图.md)，本页只列编辑代码时需要定位的规则。

## 实际命名

```text
tradetrail-api          com.tradetrail.api.customer.order.OrderController
tradetrail-application  com.tradetrail.application.order.OrderService
tradetrail-domain       com.tradetrail.domain.order.Order / OrderStore / OrderState
tradetrail-persistence  com.tradetrail.persistence.order.MyBatisOrderStore / OrderMapper
tradetrail-channel-mock com.tradetrail.channel.mock.MockPaymentGateway
tradetrail-boot         com.tradetrail.TradeTrailApplication / TradeJobs
```

同一 Controller 管理同角色同业务资源的一组接口。应用服务按业务域组织，不为每个 URL 新建 Controller，也不强制为每个动作拆一个 Service。`internal` 只在存在明确私有实现时使用，不预置空包。

## 跨域交互

| 需求 | 现有做法 |
|---|---|
| 下单预留库存 | `OrderService → InventoryService`，库存域写库存表 |
| 售后冻结订单 | `AfterSaleService → OrderService.hold` |
| 售后退款 | `AfterSaleService → PaymentService.requestRefund` |
| 退款结果推进售后 | outbox `REFUND_SUCCEEDED → AfterSaleService.refunded` |
| 物流推送 | 物流服务签名 HTTP → 商城回调 → `FulfillmentService` |

不在业务服务中直接执行其他域 SQL；不让 Controller 依赖持久化实体；不让独立物流依赖商城领域 JAR。

## 事务与数据访问

- 应用用例声明事务；HTTP 业务幂等过滤器的外层事务不能替代回调及任务直接调用时的事务边界。
- 同一方法内部调用不会额外触发 Spring 代理，不能依赖自调用注解创建新事务；理解现有外层事务再改传播行为。
- MySQL READ COMMITTED 与 MyBatis STATEMENT 缓存用于锁后读取最新已提交事实，修改时必须补真实数据库竞争回归。
- 多商品按 `productId` 排序，单个条件更新 `rows=0` 转为业务拒绝或不变量异常，不能静默忽略。
- 订单状态检查、库存转换、业务记录和审计不能分散成各自提交的步骤。
- 代码中的查询组合与表的写入所有权分开评估，不为简单查询预设复杂事件读模型。

当前部分实现和完整规范之间的差距维护在 [Plan](../../../../docs/Plan.md)，审查时明确标注，不能仅凭架构测试通过就推断所有要求已满足。
