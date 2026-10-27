# TradeTrail 实施与接口说明

实际实现以单店、单币种、单仓、单包裹、整单退款为边界；最终设计仍见 [Plan.md](../Plan.md)，验收证据见 [交付检查](delivery-checklist.md)。

## 已落地的调用链

1. 店家创建、改价、上下架、调整库存。客户结算提交商品数量、期望价格和地址；服务端重新检查售价、上架状态和可售库存，按商品 ID 排序锁定，保存订单与明细快照并预留库存。
2. 支付创建独立尝试，mock 收银台登记渠道结果。outbox 通过带签名的 HTTP 回调确认订单并把预留转已售。关单与支付共用订单锁；关单后到账只建补偿退款，不重开订单或重复占库存。
3. 发货先检查订单和售后冻结标记，再生成运单。独立物流服务持久化事件后推送，商城验签、幂等去重并维护单调摘要。异常恢复保留已有派送进度；妥投不会直接完成订单。
4. 用户确认收货，或妥投达到配置天数后自动确认。自动任务通过游标遍历批次，已处理的前 100 条不会挡住后续订单。
5. 售后申请立即冻结履约。未发货批准后整单退款；已发货/已完成批准后等待寄回和验收，质检合格才回补库存。退款成功释放冻结，已完成订单保持完成状态。
6. outbox 使用租约、退避、失败队列与人工重试；消费者保存 inbox 去重。物流服务单独保存投递租约，临时失败自动重试，4xx 拒绝交人工核对。

## 接口变更

浏览器统一访问 `/api` 前缀，由前端代理去掉前缀；以下是商城后端路径。页面路由不走接口代理。所有业务写接口携带 `Idempotency-Key` 和 CSRF；同键异参拒绝，同键成功响应可重放。网络错误后前端在 sessionStorage 保留原键，成功或明确业务拒绝后清除。

| 接口 | 用途 |
|---|---|
| `POST /login` | 表单登录，成功 200 JSON，错误 401 JSON；不重定向 |
| `PUT /admin/products/{id}` | 修改名称、描述、价格与发布状态 |
| `POST /orders`、`POST /cart/checkout` | 接收 `items: [{productId, quantity, expectedPriceCents}]` 与 `address`，旧单商品请求体不再支持 |
| `POST /orders/{id}/payments` | 创建/返回模拟支付尝试，尚不代表订单已付款 |
| `POST /mock/checkout/{paymentId}/complete` | `{success: true}` 提交 mock 渠道结果 |
| `POST /callbacks/payments/mock` | 支付渠道签名回调，按通知号去重 |
| `POST /orders/{id}/confirm` | 归属校验、售后冻结守卫、确认收货 |
| `GET /orders/{id}/history` | 状态变化审计 |
| `GET /orders/{id}/payments` | 支付与退款结果 |
| `POST /orders/{id}/after-sales` | 申请整单售后，服务端按发货状态确定类型 |
| `GET /admin/after-sales` | 售后列表，`page` 从 0 开始，`size` 为 1–100 |
| `POST /admin/after-sales/{id}/decisions` | `{approve, reason}` 审核 |
| `POST /after-sales/{id}/return` | `{trackingNo}` 客户寄回运单 |
| `POST /admin/after-sales/{id}/receipts` | `{qualified}` 退货验收 |
| `GET /admin/refunds/failed` | 店家查看失败退款，含订单、原退款号、次数和原因 |
| `POST /admin/refunds/{id}/retry` | 同一退款号重新入队，已成功记录不可再退 |
| `GET /admin/outbox`、`POST /admin/outbox/{id}/retry` | 查看耗尽自动重试的任务及人工恢复 |

## 状态与存储约定

订单主状态为 `PENDING_PAYMENT / AWAITING_SHIPMENT / SHIPPED / COMPLETED / CLOSED_UNPAID / CLOSED_REFUNDED`。履约冻结独立于主状态。售后持久化状态为 `APPLIED / REJECTED / WAITING_RETURN / RETURN_IN_TRANSIT / REFUNDING / REFUNDED`；审核和验收动作另写审计，不创建无停留时间的中间状态。退款 `FAILED` 存在退款流水上，售后仍保持 `REFUNDING`，重试后回到 `REQUESTED`。

金额用整数分，订单项与地址保留成交快照。所有数据库连接和无时区物流时间统一使用 UTC，展示转换为本地时间。MySQL 连接配置 READ COMMITTED，MyBatis 会话缓存使用 STATEMENT，保证拿到订单锁后重读得到最新已提交记录。

退款故障演练参数 `tradetrail.mock-payments.refund-failures-before-success` 默认为 0。设为 1 时，每笔退款第一次执行持久化失败，店家通过失败退款列表重试；请求号不变。重复投递失败任务不会偷偷变成成功，必须重新安排退款。次数只统计执行成功/失败，不把入队算作渠道调用。

V3 迁移在原表基础上增加交易记录与历史快照回填。测试只验证新库迁移，升级前应备份现有演示数据；旧交易不会被凭空补造真实支付渠道流水。新旧商城、前端与物流服务须一起更新。

## 设计中的后续目标

- 真实支付网关、外部退款结果查询与多渠道切换尚未接入；当前故障演练是 mock。
- 退货寄回超时自动终止、物流异常独立工单尚未实现；当前售后由店家审核，物流异常显示于轨迹。
- 管理列表主要为全量查询及前端分页，客户分页也未全面下推数据库；大量数据时需要专门分页与性能优化。
- 并发测试验证正确性，不是 QPS、P95 或生产容量报告；尚未进行多实例部署和进程强杀故障演练。
- `ORDER_CHANGED` 的 inbox 消费仅记录已处理事实，尚无独立查询投影或对外消息订阅。

以上保留为明确缺口，不用修改验收文字把它们当作已完成。
