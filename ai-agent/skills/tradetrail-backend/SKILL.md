---
name: tradetrail-backend
description: Apply TradeTrail module boundaries and transaction rules when changing or reviewing Java backend code, REST APIs, SQL, payment or logistics integration, and backend tests. Skip ordinary documentation-only and frontend-only work.
---

# TradeTrail 后端开发规范

先读根目录 [agent.md](../../../agent.md)。范围和未完成项见 [Plan](../../../docs/Plan.md)，本 Skill 不覆盖用户要求，也不授权提交、推送或上传。

## 按任务阅读

| 任务 | 参考 |
|---|---|
| 新类位置、依赖、跨域调用、数据所有权 | [layer-rules.md](references/layer-rules.md) |
| 库存、幂等、状态、回调、outbox 或回归测试 | [code-templates.md](references/code-templates.md)，内容为实际代码阅读入口 |
| 理解整体调用链 | [后端设计](../../../docs/design/TradeTrail-后端设计文档.md) |
| 查真实状态、表和接口 | [状态机](../../../docs/design/01-订单状态机.md)、[模块设计](../../../docs/design/03-模块设计图.md) |

## 关键约束

1. `api → application → domain`；持久化和渠道模块实现领域端口。领域层不依赖 Spring、HTTP、ORM。Controller 不直接调用 Mapper 或 Repository。
2. 同一域拥有表写权限；跨域调用公开应用服务或事件。独立物流服务只通过 HTTP 交互，不读商城数据库。
3. 下单核价、快照、库存预留、日志和事件必须保持事务一致；多商品按 ID 固定顺序处理，条件更新必须检查影响行数。
4. 付款、取消、超时、发货和售后修改前锁定订单，并核对最新状态、版本和冻结。保留已完成订单的历史事实。
5. 浏览器写请求用稳定幂等键；回调先验签，再去重并验证金额、归属渠道和业务状态。不要把“HTTP 超时”解释成操作未执行。
6. 外部调用不能靠本地事务保证原子性。业务与 outbox 同事务，投递按原事件号重试，消费检查已有事实。
7. 接口错误使用 `code/message/traceId/currentStatus/allowedActions`。金额为整数分，时间统一 UTC，订单与库存以服务端为准。

## 交付与验证

按 `agent.md` 要求为每个 HTTP 方法与路径建立独立接口测试类，覆盖成功、入参、权限/归属、关键拒绝及适用幂等；当前合并组织的测试不作为降低规范的依据。领域状态用单元测试，事务和竞争用真实 MySQL，页面链路用浏览器验收。

变更前后核对入口、领域守卫、持久化、回调、消费者和定时任务。验证范围与改动匹配；结果未执行就明确记录，不写成通过。只有用户明确要求时才提交或推送。

不新增尚无需求的架构层、渠道热加载或消息队列；不把 mock 协议描述为第三方标准，也不将并发正确性测试写成容量压测。
