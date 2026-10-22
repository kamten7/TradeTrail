---
name: tradetrail-backend
description: Enforces TradeTrail backend architecture rules when writing, modifying, or reviewing Java code in tradetrail-* modules. Use it before creating any domain/application/api/persistence class, adding a REST endpoint, writing SQL, or adding tests. Skip it for frontend code, docs-only edits, and pure design discussions.
---

# TradeTrail 后端架构纪律

## Overview

本技能在写后端代码前强制执行四层依赖、跨域边界、事务与幂等要求。规范来源：仓库根 `agent.md` 第 2-6 节与 `docs/design/` 三份设计文档。与设计冲突时先核对业务意图，再同时改设计和代码。

**关键词**：DDD、分层、模块边界、条件更新、幂等、outbox、状态机、接口测试

## 五条铁律（每次提交前逐条自查）

1. **依赖方向**：`api → application → domain`；`persistence` 实现 domain 端口。domain **不得** import Spring、HTTP、ORM、渠道 SDK。
2. **Controller 不碰 Repository**：只依赖 application 用例；跨域只能调对方公开 API 或发事件，不得直接读对方表或 Repository。
3. **所有库存/金额/状态写操作**必须同事务完成，且：条件 UPDATE + 检查影响行数 + 写业务事实 + 写审计或流水。
4. **每个对外接口（HTTP 方法 + 路径）都有独立测试类**：`CreateOrderApiTest`、`GetOrderApiTest` 这类命名，不靠一个空的 Controller 测试类凑覆盖。
5. **错误响应固定四要素**：`code / message / traceId / currentStatus / allowedActions`；金额、身份、库存、订单状态以服务端为准。

任何一条不满足，先改代码，不要提交。

## 决策树：你要写什么，先读哪份参考

| 你要做的事 | 先读 |
|---|---|
| 新建类，不确定放哪一层/哪个包 | `references/layer-rules.md` 的「四层职责表」与「包结构速查」 |
| 写库存扣减、订单状态流转、支付回调、幂等接口 | `references/code-templates.md` 对应模板，按模板改而不是从零写 |
| 写接口测试 | `references/code-templates.md` 的「接口测试模板」（五个必测路径） |
| 跨业务域取数据或改数据 | `references/layer-rules.md` 的「跨域交互」三种合法方式 |
| 判断是否需要新依赖、新表、新模块 | 回到 `agent.md` 1.4 与 `Plan.md` 第 6 节，先证明替代方案不成立 |

## 命名与分层速查

```
api/          CustomerOrderController、MerchantCatalogController（按角色 + 资源分组）
application/  用例服务：CreateOrderService、ShipOrderService（编排事务与端口）
domain/       Order、Stock、OrderStatus、OrderStateMachine（零框架依赖）
infrastructure/ MyBatisPlusOrderRepository、MockPaymentAdapter（实现 domain 端口）
```

- 业务域：`catalog`、`cart`、`order`、`inventory`、`payment`、`fulfillment`、`after_sale`、`integration`
- 同一业务的方法、DTO、服务、测试放同一业务包；**不用 `common`/`util` 收容业务逻辑**
- 对外只暴露公开 API、事件、端口；实现放 `internal`

## 禁止清单

- 持久层统一使用 MyBatis-Plus；交易核心用明确的 Mapper SQL、条件更新和影响行数检查；不引入 JPA 或另一套持久化框架
- 不为尚未出现的需求预置空接口、抽象层或消息队列
- 不用日志代替错误处理，不静默吞异常
- 不把 mock 支付/物流的 HMAC 协议描述成第三方通用协议
- 不在 domain 层做 HTTP 调用、读配置、开事务注解

## References

- `references/layer-rules.md`：四层职责、依赖方向、跨域交互、常见违规对照
- `references/code-templates.md`：条件更新 / 幂等 / outbox / 状态机 / 接口测试五类模板
