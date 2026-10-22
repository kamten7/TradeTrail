# 分层规则与跨域交互

## 四层职责

| 层 | 负责 | 不负责 | 允许依赖 |
|---|---|---|---|
| `api` | HTTP 编解码、参数校验、鉴权入口、错误响应组装 | 业务规则、事务、SQL | application |
| `application` | 用例编排、事务边界、幂等键处理、调用 domain 与端口 | SQL 细节、协议细节 | domain + 端口接口 |
| `domain` | 实体、值对象、状态机、业务不变量 | Spring、HTTP、ORM、渠道 SDK | 无（纯 Java） |
| `infrastructure` | 实现端口：JDBC 仓储、渠道适配器、outbox 投递 | 业务判断 | domain 端口 |

依赖只能从上往下：`api → application → domain`，`infrastructure → domain`（实现端口）。启动模块 `tradetrail-boot` 负责装配。

## 包结构速查

```
com.tradetrail.order
├── api                OrderController、OrderAdminController
├── application        CreateOrderService、CancelOrderService
├── domain             Order、OrderStatus、OrderTransition
├── infrastructure     JdbcOrderRepository
└── internal           本域私有实现，其他域不得引用
```

跨域同理：`com.tradetrail.inventory.domain.Stock` 等。

## 跨域交互：三种合法方式

1. **同步查询/动作**：调用对方公开 application API（接口定义在对方 `api` 或明确的公开包内）
2. **异步事实传播**：业务事务内写 `outbox_event`，由投递器发送，消费方用 inbox 去重
3. **领域事件（进程内）**：仅用于同进程通知，不承担跨服务一致性

以下三条是违规，出现即改：

| 违规写法 | 正确写法 |
|---|---|
| `OrderService` 里执行 `UPDATE stock SET ...` | 调 `InventoryService.reserve(orderId, items)`，或在下单用例内通过 inventory 公开端口完成同事务预留 |
| `order` 域注入 `StockRepository` | 通过 inventory 的公开端口接口 |
| 两个域共用一个实体或一张表各自写 | 单一域拥有写入权，另一方走公开接口或事件 |

## 常见违规对照

| 场景 | 错误 | 正确 |
|---|---|---|
| Controller 查订单 | `orderController` 注入 `OrderRepository` | 注入 `GetOrderService` |
| domain 里发通知 | `Order` 里 `pushService.send(...)` | domain 返回状态变更结果，application 负责通知 |
| 库存扣减 | 先 `SELECT` 再 `UPDATE` | 一条条件 UPDATE 并检查行数 |
| 支付回调 | 直接改订单状态 | 验签 → 幂等去重 → 校验业务状态 → 用例内推进 |
| 跨域读数据 | `SELECT * FROM inventory.stock` | 调 inventory 公开查询接口 |
| 事务边界 | Controller 上加 `@Transactional` | application 用例方法上 |

## 事务与并发约定

- 事务边界在 application 用例方法，不在 Controller、不在 Repository
- 多 SKU 扣减按 `sku_id` **固定升序**处理，降低死锁概率；任一失败整个事务回滚
- 条件更新失败（rows=0）视为业务拒绝，抛业务异常，不重试不吞错
- 定时任务与回调都可重入：执行前重新读库确认业务条件，不信任调度触发本身

## 审计与流水

- 库存每次变动写 `stock_ledger`，唯一业务键防重复
- 金额变动写流水，保存变更前后值与业务单号
- 管理端调整库存必须记录操作人与原因
