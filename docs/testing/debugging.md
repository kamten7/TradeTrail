# 接口、断点与排错指南

文档核对日期：2026-10-28。先按[手工验收](manual-acceptance.md)启动环境；本页默认商城地址 `http://127.0.0.1:8080`。所有示例仅针对自己的本地测试库，写请求会创建演示数据。

[返回 README](../../README.md) · [业务与接口总览](../implementation-roadmap.md)

## 1. 分清三个请求边界

| 调用位置 | 请求示例 | 身份与防护 |
|---|---|---|
| 用户/店家浏览器 | `http://127.0.0.1:5173/api/orders` | Vite 去掉 `/api`；会话 Cookie、CSRF、业务幂等键 |
| PowerShell / Postman 直连商城 | `http://127.0.0.1:8080/orders` | 路径不带 `/api`；工具自行保留 Cookie 和登录后的 CSRF |
| 物流/支付服务回调 | `/callbacks/logistics/mock`、`/callbacks/payments/mock` | 原始报文 HMAC 签名和时间窗口；不靠浏览器登录授权 |

先 GET `/session` 获取 CSRF，再 POST `/login`，登录后必须重新 GET `/session` 获取更新后的 token。直接访问商城 `/` 返回未登录或未配置页面，不是健康检查失败；检查 `/session` 即可。项目没有配置通用 `/actuator/health` 或 Swagger 页面。

## 2. 可复制的 PowerShell 接口调试

要求 PowerShell 7。按 2.1 → 2.2 → 2.3 顺序在同一终端执行；变量会在后续步骤复用。默认不启用退款故障演练参数，正常流程才会自动退款成功。

文档校验：2026-10-28 已将 2.1–2.5 的代码块连续执行于临时 H2 内存库，仅将地址改为隔离端口 18082，全部断言通过。这个记录验证接口示例可运行；手工文档中的 14 个用例需按实际执行结果填写。

### 2.1 登录与请求助手

以下函数只存在于当前 PowerShell 会话，不会修改项目配置。每个角色有独立 Cookie 容器。

```powershell
$ErrorActionPreference = 'Stop'
$mallUrl = 'http://127.0.0.1:8080'

function Connect-TradeRole([string]$Username, [string]$Password) {
    $cookies = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $before = Invoke-RestMethod -Uri "$mallUrl/session" -WebSession $cookies
    Invoke-RestMethod -Uri "$mallUrl/login" -Method Post -WebSession $cookies `
        -Body @{ username = $Username; password = $Password; _csrf = $before.csrfToken } | Out-Null
    $after = Invoke-RestMethod -Uri "$mallUrl/session" -WebSession $cookies
    if ($after.user -ne $Username) { throw '登录身份不匹配' }
    return @{ Cookies = $cookies; Csrf = $after.csrfToken }
}

function Invoke-Trade {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Auth,
        [string]$Json,
        [string]$Key = ([guid]::NewGuid().ToString())
    )
    $request = @{
        Uri = "$mallUrl$Path"
        Method = $Method
        WebSession = $Auth.Cookies
        SkipHttpErrorCheck = $true
        Headers = @{}
    }
    if ($Method -ne 'GET') {
        $request.Headers['X-XSRF-TOKEN'] = $Auth.Csrf
        $request.Headers['Idempotency-Key'] = $Key
    }
    if ($Json) {
        $request.ContentType = 'application/json; charset=utf-8'
        $request.Body = [System.Text.Encoding]::UTF8.GetBytes($Json)
    }
    $response = Invoke-WebRequest @request
    $data = if ($response.Content) { $response.Content | ConvertFrom-Json } else { $null }
    return [pscustomobject]@{ Status = [int]$response.StatusCode; Data = $data }
}

function Assert-TradeStatus($Result, [int]$Expected = 200) {
    if ($Result.Status -ne $Expected) {
        throw "预期 HTTP $Expected，实际 $($Result.Status)，code=$($Result.Data.code)，traceId=$($Result.Data.traceId)"
    }
}

$merchantAuth = Connect-TradeRole 'merchant' 'merchant123'
$customerAuth = Connect-TradeRole 'customer' 'customer123'
```

助手会返回 `Status` 和 `Data`，包括业务拒绝的 JSON，不会把 4xx 当作网络异常。它默认每次生成新键；测试重复请求或网络故障重试时，必须显式传入同一个 `-Key`。不要直接输出整个登录对象或 Cookie。

### 2.2 创建商品、订单并验证幂等

```powershell
$productJson = @{
    name = 'QA-API-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
    description = '接口调试专用'
    priceCents = 1299
    initialStock = 10
} | ConvertTo-Json -Compress
$created = Invoke-Trade -Method POST -Path '/admin/products' -Auth $merchantAuth -Json $productJson
Assert-TradeStatus $created 201
$productId = $created.Data.id
$published = Invoke-Trade -Method POST -Path "/admin/products/$productId/publish" -Auth $merchantAuth
Assert-TradeStatus $published

$orderKey = [guid]::NewGuid().ToString()
$orderJson = @{
    items = @(@{ productId = $productId; quantity = 2; expectedPriceCents = 1299 })
    address = '测试收件人，测试电话，香港测试地址'
} | ConvertTo-Json -Depth 5 -Compress
$placed = Invoke-Trade -Method POST -Path '/orders' -Auth $customerAuth -Json $orderJson -Key $orderKey
Assert-TradeStatus $placed 201
$orderId = $placed.Data.id
$replayed = Invoke-Trade -Method POST -Path '/orders' -Auth $customerAuth -Json $orderJson -Key $orderKey
Assert-TradeStatus $replayed 201
if ($replayed.Data.id -ne $orderId) { throw '重复请求生成了不同订单' }
if ($placed.Data.totalCents -ne 2598) { throw '订单金额不正确' }

$changedJson = $orderJson.Replace('测试收件人', '另一收件人')
$conflict = Invoke-Trade -Method POST -Path '/orders' -Auth $customerAuth -Json $changedJson -Key $orderKey
Assert-TradeStatus $conflict 409
if ($conflict.Data.code -ne 'IDEMPOTENCY_CONFLICT') { throw '未返回幂等冲突' }
Write-Output "PASS product=$productId order=$orderId replay=same-order conflict=409"
```

幂等键按用户和请求键隔离，不能跨不同 URL/请求体复用；指纹包含原始请求体，重放时保留同一 `$orderJson` 字符串，不要重新排版 JSON。

### 2.3 异步支付、发货、确认收货

```powershell
$payment = Invoke-Trade -Method POST -Path "/orders/$orderId/payments" -Auth $customerAuth
Assert-TradeStatus $payment
$paymentId = $payment.Data.id
$completed = Invoke-Trade -Method POST -Path "/mock/checkout/$paymentId/complete" -Auth $customerAuth -Json '{"success":true}'
Assert-TradeStatus $completed

$deadline = [DateTime]::UtcNow.AddSeconds(30)
do {
    $order = Invoke-Trade -Method GET -Path "/orders/$orderId" -Auth $customerAuth
    Assert-TradeStatus $order
    if ($order.Data.status -eq 'AWAITING_SHIPMENT') { break }
    Start-Sleep -Milliseconds 500
} while ([DateTime]::UtcNow -lt $deadline)
if ($order.Data.status -ne 'AWAITING_SHIPMENT') { throw '支付未完成，请检查 outbox 与回调地址' }

$shipped = Invoke-Trade -Method POST -Path "/admin/orders/$orderId/shipments" -Auth $merchantAuth
Assert-TradeStatus $shipped
Write-Output "tracking=$($shipped.Data.trackingNo)"
$confirmed = Invoke-Trade -Method POST -Path "/orders/$orderId/confirm" -Auth $customerAuth
Assert-TradeStatus $confirmed
if ($confirmed.Data.status -ne 'COMPLETED') { throw '确认收货未完成' }
$history = Invoke-Trade -Method GET -Path "/orders/$orderId/history" -Auth $customerAuth
Assert-TradeStatus $history
$history.Data | Format-Table fromStatus,toStatus,event,actor
```

这里直接确认收货，用于验证接口；当前服务允许用户在发货后主动确认，不强制先有妥投轨迹。完整物流 HTTP 流程执行 `./ai-agent/tools/smoke.ps1` 或手工 TC-03。签名回调应由模拟服务发送，不能把普通登录请求伪装成渠道通知。

### 2.4 迟到支付补偿

复用 2.1 的助手和 2.2 的商品，但创建一笔新订单。此例用主动关闭模拟“关单先发生”的分支；真正的超时竞争由定时用例及集成测试验证。

```powershell
$lateOrder = Invoke-Trade -Method POST -Path '/orders' -Auth $customerAuth -Json $orderJson
Assert-TradeStatus $lateOrder 201
$lateId = $lateOrder.Data.id
$latePayment = Invoke-Trade -Method POST -Path "/orders/$lateId/payments" -Auth $customerAuth
Assert-TradeStatus $latePayment
$closed = Invoke-Trade -Method POST -Path "/orders/$lateId/cancel" -Auth $customerAuth
Assert-TradeStatus $closed
$latePaymentId = $latePayment.Data.id
$arrived = Invoke-Trade -Method POST -Path "/mock/checkout/$latePaymentId/complete" -Auth $customerAuth -Json '{"success":true}'
Assert-TradeStatus $arrived

$deadline = [DateTime]::UtcNow.AddSeconds(30)
do {
    $result = Invoke-Trade -Method GET -Path "/orders/$lateId/payments" -Auth $customerAuth
    Assert-TradeStatus $result
    if ($result.Data.refund.status -eq 'SUCCEEDED') { break }
    Start-Sleep -Milliseconds 500
} while ([DateTime]::UtcNow -lt $deadline)
$final = Invoke-Trade -Method GET -Path "/orders/$lateId" -Auth $customerAuth
Assert-TradeStatus $final
if ($final.Data.status -ne 'CLOSED_UNPAID' -or $result.Data.payment.status -ne 'LATE_PAYMENT' -or $result.Data.refund.status -ne 'SUCCEEDED') {
    throw '迟到支付补偿未满足预期，请查看支付/退款/outbox 记录'
}
Write-Output "PASS late-order=$lateId refund=$($result.Data.refund.id)"
```

预期关单不被重开，库存不会再次转已售，补偿退款关联原支付号。若启用了首次退款失败演练，此段不会直接成功；应到店家“异常恢复”重试后再查询，不应放宽断言。

### 2.5 权限拒绝示例

```powershell
$denied = Invoke-Trade -Method POST -Path '/admin/products' -Auth $customerAuth -Json $productJson
Assert-TradeStatus $denied 403
$anonymous = Invoke-WebRequest -Uri "$mallUrl/orders" -SkipHttpErrorCheck
if ([int]$anonymous.StatusCode -ne 401) { throw '匿名订单接口没有拒绝访问' }
$customerAuth.Cookies.Headers.Remove('Idempotency-Key') | Out-Null
$missingKey = Invoke-WebRequest -Uri "$mallUrl/orders/$orderId/cancel" -Method Post `
    -WebSession $customerAuth.Cookies -Headers @{ 'X-XSRF-TOKEN' = $customerAuth.Csrf } -SkipHttpErrorCheck
if ([int]$missingKey.StatusCode -ne 400) { throw '缺少幂等键没有被拒绝' }
Write-Output 'PASS customer-admin=403 anonymous-orders=401 missing-key=400'
```

PowerShell 的 WebRequestSession 会保留请求头，所以“缺少幂等键”测试先明确移除旧头，不能仅从本次 `-Headers` 中省略。缺少 CSRF 的写请求也应先移除会话中旧的 `X-XSRF-TOKEN`，预期被 Spring Security 拒绝，通常为 403；需要区分权限/CSRF 与业务幂等校验，不能仅凭状态码推断业务已经执行。

## 3. 配置与定时参数

启动参数优先使用 `--属性名=值`，便于看清本次演练覆盖了什么。

| 属性或环境变量 | 默认值 | 用途 |
|---|---|---|
| `server.port` | 商城 8080 / 物流 8081 | HTTP 端口 |
| `spring.profiles.active` | 默认 H2；`mysql` 切换 MySQL | 数据库配置选择 |
| `TRADETRAIL_API_TARGET` | `http://127.0.0.1:8080` | Vite 代理目标，修改后重启前端 |
| `tradetrail.payment.callback-url` | `http://127.0.0.1:8080` | mock 支付回调目标，属于商城配置 |
| `tradetrail.mall-url` / `TRADETRAIL_MALL_URL` | `http://localhost:8080` | 物流服务推送目标 |
| `tradetrail.payment-minutes` | 30 | 新订单付款期限，单位分钟 |
| `tradetrail.auto-confirm-days` | 7 | 妥投后自动确认等待天数 |
| `tradetrail.after-sale-days` | 15 | 完成订单的售后申请期限，演示规则，不代表法定期限 |
| `tradetrail.jobs.delay-ms` | 2000 | 商城 outbox 或物流恢复任务间隔 |
| `tradetrail.jobs.lifecycle-delay-ms` | 10000 | 商城超时和自动确认任务间隔 |
| `tradetrail.jobs.enabled` | true | 商城生命周期/outbox 任务开关；关闭会停止异步支付后续处理 |
| `tradetrail.mock-payments.enabled` | true | 是否允许 mock 新支付/收银台操作 |
| `tradetrail.mock-payments.refund-failures-before-success` | 0 | 每笔 mock 退款成功前故意失败次数 |
| `TRADETRAIL_DB_URL` / `TRADETRAIL_LOGISTICS_DB_URL` | 各服务 MySQL URL | 仅 `mysql` 配置使用 |
| `TRADETRAIL_DB_USER` / `TRADETRAIL_DB_PASSWORD` | 本地演示账号 | 两服务 MySQL 凭据 |
| `TRADETRAIL_LOGISTICS_SECRET` | 本地演示密钥 | 商城与物流服务必须一致 |

如改商城为 18080，至少同时设置：商城 `--server.port=18080 --tradetrail.payment.callback-url=http://127.0.0.1:18080`；物流 `--tradetrail.mall-url=http://127.0.0.1:18080`；两个前端 `$env:TRADETRAIL_API_TARGET='http://127.0.0.1:18080'`。接口助手的 `$mallUrl` 也要改。

## 4. IDE 断点调试

在 IntelliJ IDEA 中以根 `pom.xml` 导入 Maven 工程，项目 SDK 设为 JDK 21，等待依赖导入。建立两个 Spring Boot/Application 配置：

| 启动类 | 模块 classpath | 工作目录 |
|---|---|---|
| `com.tradetrail.TradeTrailApplication` | `tradetrail-boot` | 仓库根目录 |
| `com.tradetrail.simulator.LogisticsSimulatorApplication` | `tradetrail-logistics-simulator` | 仓库根目录 |

Program arguments 复制手工文档对应命令中 JAR 路径之后的参数。相对数据库路径取决于工作目录；在子模块目录启动会创建另一份 `data/`，表现为“原有订单不见了”。不要同时启动占用相同端口或 H2 文件的 JAR 与 IDE 进程。

| 要观察的问题 | 建议断点 | 核对内容 |
|---|---|---|
| 请求重放 | `IdempotencyFilter.doFilterInternal`、`CommandService.lock` | 身份、键、指纹、原响应 |
| 下单/改价 | `CartService.checkout`、`OrderService.create` | 选中明细、当前报价、期望价格 |
| 库存 | `InventoryService.reserve/confirm/release/restock` | 当前订单、明细、事务回滚 |
| 支付不推进 | `PaymentService.simulate/receive`、`OutboxDispatcher.dispatch` | 渠道结果、通知验签、订单锁后重读 |
| 发货被阻断 | `FulfillmentService.ship`、`OrderState.next` | 主状态与 `fulfillmentHold` |
| 退款 | `AfterSaleService.decide/receiveReturn/refunded`、`PaymentService.executeRefund` | 退款号、金额、质检与回补标记 |
| 物流恢复 | `DeliveryService.deliver`、`FulfillmentService.receive` | eventId、发生时间、摘要进度 |
| 定时处理 | `TradeJobs.lifecycle` | deadline、妥投时间、冻结标记、批次游标 |

支付 HTTP 回调与调度线程不同。IDE 可优先选择只暂停当前线程；长时间暂停整个 JVM 可能导致 HTTP 超时或 60 秒租约过期，看到后续重复投递并不一定是新缺陷。调度被关闭时不要期待订单自动付款或完成。

## 5. 数据库只读核对

以下查询针对当前迁移结构。在自己的 MySQL 调试库连接中执行，先 `USE tradetrail;`，把示例订单号 123 替换成实际编号。SQL 仅查询，不用直接改状态来“完成”业务。

```sql
SET @order_id = 123;
SELECT id, customer, status, total_cents, version, fulfillment_hold,
       pay_deadline, completed_at
FROM sales_order WHERE id = @order_id;
SELECT * FROM order_item WHERE order_id = @order_id;
SELECT s.* FROM stock s
JOIN order_item i ON i.product_id = s.product_id WHERE i.order_id = @order_id;
SELECT * FROM inventory_reservation WHERE order_id = @order_id;
SELECT * FROM stock_ledger WHERE order_id = @order_id ORDER BY id;
SELECT from_status, to_status, event, actor, trace_id, occurred_at
FROM order_status_log WHERE order_id = @order_id ORDER BY id;
SELECT * FROM payment_attempt WHERE order_id = @order_id;
SELECT * FROM refund_attempt WHERE order_id = @order_id;
SELECT * FROM after_sale_case WHERE order_id = @order_id;
SELECT a.* FROM after_sale_action a
JOIN after_sale_case c ON c.id = a.case_id WHERE c.order_id = @order_id ORDER BY a.id;
SELECT * FROM shipment WHERE order_id = @order_id;
SELECT t.* FROM tracking_event t
JOIN shipment s ON s.tracking_no = t.tracking_no
WHERE s.order_id = @order_id ORDER BY t.occurred_at, t.received_at;
```

outbox 的 `aggregate_id` 因事件不同分别是订单号、支付号或退款号，不能只按订单号查全部事件：

```sql
SELECT event_id, type, aggregate_id, state, attempts, next_attempt_at, last_error
FROM outbox_event
WHERE aggregate_id = CAST(@order_id AS CHAR)
   OR aggregate_id IN (SELECT id FROM payment_attempt WHERE order_id = @order_id)
   OR aggregate_id IN (SELECT id FROM refund_attempt WHERE order_id = @order_id)
ORDER BY created_at;
```

在物流库执行 `SELECT event_id, tracking_no, status, delivery_state, retry_count, next_attempt_at, last_error FROM sim_tracking_event ORDER BY occurred_at DESC;`，按运单号关联商城轨迹。数据库与无时区物流报文按 UTC 处理，页面转本地时间，比较前先统一时区。

H2 默认是嵌入式文件模式，没有开放 Web Console 或 TCP 服务。不要在商城占用文件时强行用另一进程打开同一 H2 文件；运行中优先用接口/断点观察，停服务后再用数据库工具读取，或切换 MySQL 做实时 SQL 核对。

## 6. 常见故障定位

| 现象 | 排查步骤 |
|---|---|
| 登录页提示无法连接 | 先请求 8080 `/session`，再请求前端 `/api/session`；前者失败检查后端启动，后者失败检查代理与端口 |
| 登录后另一个页面变成无权限 | 检查是否使用同一浏览器 Cookie 登录了另一角色；分开浏览器配置文件 |
| 订单始终待支付 | 查 `provider_status`；未成功检查收银台请求，已成功检查 `PAYMENT_NOTICE`、回调 URL、调度开关与签名 |
| 售后一直退款处理中 | 查 `refund_attempt.status`；FAILED 去异常恢复重试，SUCCEEDED 查 `REFUND_SUCCEEDED` 投递与售后更新 |
| 异常恢复任务列表为空但仍未完成 | 页面只显示耗尽重试的 outbox；PENDING/PROCESSING 的任务需要查库，不能据空列表认定全部正常 |
| 物流 HTTP 4xx | 检查运单存在、共享密钥一致、时钟和 UTC 报文；拒绝记录不会无限自动重试 |
| 物流网络/5xx 错误 | 保持物流服务运行，恢复商城后等待退避重试；FAILED 时再人工重试 |
| 同请求号返回 409 | 核对方法、路径、原始 JSON 是否变化；同一操作重试用原键，新的业务动作生成新键 |
| 重启后没有原数据 | 核对 profile、JDBC URL、工作目录，H2 与 MySQL 是两份数据；内存库重启会丢失 |
| MySQL 缺 `tradetrail_logistics` | 旧 Docker 卷不会重跑初始化脚本；按下方步骤补库，不删卷 |
| Flyway checksum/迁移失败 | 记录失败版本，核对数据库和当前代码；不要手动改历史校验值或直接 repair 掩盖差异 |
| JAR 无法覆盖或 H2 文件被锁 | 停止该项目原进程再打包/启动；不要结束不明用途的 Java 进程 |
| `SkipHttpErrorCheck` 不识别 | 当前是 Windows PowerShell 5.1，改用 `pwsh` 的 PowerShell 7 |
| Playwright 端口已占用 | 18080、15173、15174 必须空闲；还要清理遗留 `MERCHANT_URL`、`CUSTOMER_URL`，避免跳过自动服务启动 |

旧卷补物流库：确认是自己的本地开发 MySQL，执行 `docker compose exec mysql mysql -uroot -p`，交互输入本地开发 root 密码（见 `compose.yaml`），然后执行仓库 [初始化 SQL](../../docker/mysql/init/01-logistics.sql) 中的建库和授权语句。不要把密码写进命令历史，也不要删除原数据库。

业务失败通常返回 `code / message / traceId / currentStatus / allowedActions`。HTTP 400 常见于参数错误，401 为未登录，403 为角色/资源/CSRF 拒绝，404 为资源不存在，409 为业务冲突，500 为服务异常。保存 `traceId` 与失败时间，结合日志和状态流水定位；开发者工具 Network 的 Preserve log 有助于保留失败请求，分享时删去 Cookie、token 和个人信息。

## 7. 自动化定向回归

以下命令均从仓库根目录执行；模块前置依赖同时构建，`failIfNoSpecifiedTests=false` 仅允许不含指定类的依赖模块继续执行。

```powershell
mvn -pl tradetrail-boot -am '-Dtest=LoginApiTest' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl tradetrail-boot -am '-Dtest=TransactionRegressionTest,TransactionLifecycleTest' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl tradetrail-boot -am '-Dtest=ListFailedRefundsApiTest,RetryRefundApiTest' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -Pmysql-it verify
```

| 检查 | 关注点 | 报告位置 |
|---|---|---|
| 普通后端测试 | HTTP 合同、归属、状态、库存、架构 | `tradetrail-boot/target/surefire-reports` |
| MySQL 集成 | 100 买家抢 10 件、并发通知、事务回滚、售后竞争 | `tradetrail-boot/target/failsafe-reports` |
| 物流接口测试 | 录入页面、创建和重试入口 | `tradetrail-logistics-simulator/target/surefire-reports` |
| 浏览器验收 | 实际页面与真实后端交易、响应丢失、退款失败恢复 | `frontend/playwright-report`、`frontend/test-results` |

并发测试是正确性检查，不是性能报告。浏览器测试需要提前 Maven 打包；只运行 `mvn test` 不会更新供 Playwright 启动的 JAR。`MERCHANT_URL` 或 `CUSTOMER_URL` 任意一个设置后都会禁用自动 webServer，正常验收应清除它们；只有自行准备完整隔离环境且启用首次退款失败参数时才使用此覆盖方式。

需要保留截图或报告时，在下一次运行前复制到自己的验收记录目录；Playwright 会覆盖输出目录。历史通过数量见[交付记录](../delivery-checklist.md)，本次调试结论以你当前提交上的实际报告为准。
