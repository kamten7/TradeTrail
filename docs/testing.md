# TradeTrail 测试、验收与调试

文档核对日期：2026-10-29。命令使用 PowerShell 7，默认从仓库根目录运行。本文合并页面验收与接口调试，写请求只针对自己的演示库；需要实际执行后填写结果，不预先视为通过。

[返回 README](../README.md) · [范围与历史验收](Plan.md) · [开发与部署](development-and-deployment.md)

- [手工环境与页面验收](#a1-推荐测试顺序)：四端启动、库存断言、14 个用例与记录模板。
- [接口调试](#b1-分清三个请求边界)：可复制请求、幂等、异步支付和迟到补偿。
- [断点与 SQL](#b4-ide-断点调试)：观察调用链和数据库事实。
- [自动化回归](#b7-自动化定向回归)：普通测试、MySQL、构建和浏览器。

## A1 推荐测试顺序

先完成环境准备，再按 **TC-01 → TC-02 → TC-03 → TC-05 → TC-06** 跑通主要页面。之后测试取消、改价、权限及故障恢复。最后执行自动化回归，补充页面难以稳定复现的并发和回调乱序场景。

除正常流程中明确复用订单的步骤外，每个用例新建专属商品和订单，名称带用例号，例如 `QA-TC05-20261029`。当前每单只允许一个售后工单，拒绝后不支持另开工单，因此不要用同一订单测试多个售后分支。

## A2 准备独立的手工环境

本方案使用 `data/manual-qa/` 下的 H2 文件数据库，不改默认的开发库。它在重启后保留订单，适合断网、退款失败和恢复演练。

### A2.1 首次安装与打包

```powershell
Set-Location D:\JavaCode\TradeTrail
java -version
mvn -version
node --version
$PSVersionTable.PSVersion
mvn clean package
npm ci --prefix frontend/customer --include=dev
npm ci --prefix frontend/merchant --include=dev
```

要求 JDK 21、Maven 3.9、建议 Node 22.12+、PowerShell 7。先停止旧 JAR 再打包；当前默认开发服务如果占用了 8080/8081，也先在对应终端按 Ctrl+C 停止。

### A2.2 四个终端分别启动

每个终端先进入仓库根目录。以下命令互相独立，不要将四个服务放到同一个前台终端顺序执行。

**终端 A：商城**

```powershell
java -jar tradetrail-boot/target/tradetrail-boot-0.1.0-SNAPSHOT.jar --spring.profiles.active=default --server.port=8080 '--spring.datasource.url=jdbc:h2:file:./data/manual-qa/mall;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE' --spring.datasource.username=sa --spring.datasource.password= --tradetrail.payment.callback-url=http://127.0.0.1:8080
```

**终端 B：物流模拟服务**

```powershell
java -jar tradetrail-logistics-simulator/target/tradetrail-logistics-simulator-0.1.0-SNAPSHOT.jar --spring.profiles.active=default --server.port=8081 '--spring.datasource.url=jdbc:h2:file:./data/manual-qa/logistics;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE' --spring.datasource.username=sa --spring.datasource.password= --tradetrail.mall-url=http://127.0.0.1:8080
```

**终端 C：用户端**

```powershell
$env:TRADETRAIL_API_TARGET = 'http://127.0.0.1:8080'
npm --prefix frontend/customer run dev
```

**终端 D：店家端**

```powershell
$env:TRADETRAIL_API_TARGET = 'http://127.0.0.1:8080'
npm --prefix frontend/merchant run dev
```

商城与物流端都应显示 `Started ...Application`，前端分别显示 5173、5174。端口占用会报错，不应默默切换到其他端口。

### A2.3 登录与检查

| 身份 | 页面 | 账号 / 密码 | 建议浏览器环境 |
|---|---|---|---|
| 店家 | http://127.0.0.1:5174 | `merchant / merchant123` | 普通窗口 |
| 用户 | http://127.0.0.1:5173 | `customer / customer123` | 无痕窗口或另一个浏览器配置文件 |
| 物流员 | http://localhost:8081 | `logistics / logistics123` | 普通窗口，保持 `localhost` 主机名 |

同主机不同端口共享 Cookie，不要在两个普通标签页登录不同角色。新库没有预置商品，空列表是正常状态。

```powershell
Invoke-RestMethod http://127.0.0.1:8080/session
Invoke-RestMethod http://127.0.0.1:5173/api/session
Invoke-RestMethod http://127.0.0.1:5174/api/session
```

三条都应返回包含 `user`、`roles`、`csrfToken` 的 JSON。PowerShell 使用自己的会话，所以这里的匿名结果不代表浏览器退出登录。若第一条成功、后两条失败，先排查 Vite 代理。

## A3 数据和观察约定

- 每个用例使用独立商品：售价填 **1299 分**，即页面显示 **¥12.99**，初始库存 **10**。
- 需要结算时购买 **2 件**，总金额应为 **2598 分 / ¥25.98**；地址填 `测试收件人，测试电话，香港测试地址`。
- 修改购物车数量后，等金额刷新再结算。取消无关商品勾选，避免将其他用例混进订单。
- 商品管理页只展示可售库存；预留、已售和流水需通过[数据库查询](#b5-数据库只读核对)核对。
- 异步付款通常需要一个投递周期，订单页也有轮询；先等待或刷新。如果 30 秒仍无进展，记录错误并排查，不要反复创建新支付。
- 每次验收记录商品 ID、订单 ID、运单号和售后 ID，不依赖列表排序猜测记录。

### 库存参考值

以下为一件独立商品从库存 10、购买数量 2 开始的账本变化；不同退款分支分别使用新订单。

| 时点 | available 可售 | reserved 预留 | sold 已售 |
|---|---:|---:|---:|
| 新建 | 10 | 0 | 0 |
| 创建待支付订单 | 8 | 2 | 0 |
| 取消或未付超时关闭 | 10 | 0 | 0 |
| 收到有效支付通知 | 8 | 0 | 2 |
| 发货、妥投、确认收货 | 8 | 0 | 2 |
| 未发货退款完成 | 10 | 0 | 0 |
| 退货质检合格并回补 | 10 | 0 | 0 |
| 退货不合格，不回补 | 8 | 0 | 2 |

退货分支在验收合格时回补实物，不等待退款结果。因此“退款失败但合格商品已回库”是当前设计的合法状态；重试退款不得再次回补。

## A4 页面验收用例

### TC-01 登录、路由与错误恢复

1. 店家输入错误密码并登录，预期显示“账号或密码不正确”，仍停留登录页。
2. 改用正确密码，预期进入“商品与库存”；直接刷新 `/products`，应仍是页面，不是 JSON 或 404。
3. 用户访问 `/cart`，未登录时进入登录页；登录后返回购物车。
4. 打开浏览器开发者工具 Network，接口路径应以 `/api/` 开头；成功登录请求为 200 JSON，无跨端口跳转。
5. 在开发者工具切换 Offline 后刷新业务列表，应显示可理解的连接错误；恢复 Online 并重试，应恢复加载。若用户端主页面尚未缓存，整页离线刷新可能是浏览器离线页，应在页面已加载后触发“刷新”操作测试应用错误态。

**通过条件：**没有白屏、原始 `Failed to fetch` 或循环跳转；错误密码不进入管理台。

### TC-02 商品、购物车、结算与付款

1. 店家“新建商品”，按第 3 节填写，保存。未上架前用户列表不能购买。
2. 店家点击“上架”并确认，用户刷新后找到该商品，加购。
3. 用户进入购物车，改为 2 件并选中；金额应为 ¥25.98。
4. 地址留空点击“确认下单”，预期提示填写收货信息，不生成订单。
5. 填写测试地址并结算，预期生成一笔待支付订单，商品和地址快照正确，可售库存变为 8。
6. 点击“立即付款”→“确认付款”，等待订单变为待发货。创建支付尝试本身不等同于已经付款。

**通过条件：**金额由服务端确认；支付后预留转已售；刷新不会额外生成订单或再次扣库存。

### TC-03 发货、轨迹与确认收货

沿用 TC-02 的待发货订单。

1. 店家“订单履约”找到订单，查看详情核对地址和金额，点击“发货”→“确认发货”。
2. 预期订单为已发货，详情出现 `MOCK-...` 运单号。
3. 物流端输入该运单，依次提交 `PICKED_UP`、`IN_TRANSIT`、`OUT_FOR_DELIVERY`、`DELIVERED`，地点分别填写测试揽收点、分拨中心、派送站、收件点。
4. 用户点击“查看物流”，应能看到四个节点，显示时间为本地时间。
5. 妥投后立即刷新，订单仍应为已发货（默认自动确认要等待 7 天）；点击“确认收货”并确认。

**通过条件：**订单变为已完成；物流轨迹仍可查；发货、轨迹及收货不再改变库存数量。

### TC-04 未支付取消

新建商品和待支付订单，不付款，点击“取消订单”并确认。

**预期：**状态为已关闭，预留释放，可售回到 10；页面不再提供付款按钮。重复取消不得再次加库存。重复请求的准确幂等断言用接口或自动化测试验证。

### TC-05 未发货仅退款与履约阻断

1. 新建并支付一笔订单，保持待发货。
2. 用户申请售后，填写原因；店家刷新订单后应显示售后暂停，发货不可操作。
3. 店家“售后管理”审核申请，选择批准并填写理由。
4. 等待异步退款，必要时刷新两端。

**预期：**售后类型为整单退款，不需要填写退货运单；售后退款完成，主订单已退款关闭，可售恢复到 10。退款前不能提前解除售后冻结发货。

### TC-06 已完成订单退货退款

1. 新订单按 TC-02、TC-03 完成收货，再申请售后。
2. 店家批准，用户出现“填写退货运单”，输入 `RETURN-TC06-日期时间` 后提交。
3. 店家刷新售后记录，点击“质检合格”并确认验收。
4. 等待退款完成，核对用户订单和店家售后状态。

**预期：**合格退货只回补一次；售后退款完成，主订单仍为已完成。不要把“已完成订单没有改成已退款关闭”判作缺陷。

### TC-07 拒绝售后与不回补分支

使用两笔新订单分别测试：

| 分支 | 操作 | 预期 |
|---|---|---|
| 拒绝申请 | 待发货订单申请售后，店家审核拒绝并写理由 | 售后已拒绝；解除冻结，订单可继续发货；不创建退款 |
| 不合格退货 | 已发货订单申请并批准退货，填运单后店家点击“不回补库存”验收 | 继续退款，但可售保持 8；退款完成后主订单已退款关闭 |

当前“不回补库存”不代表拒绝退款，它表示收到实物但不适合再销售。

### TC-08 改价、下架与订单快照

1. 用户打开购物车结算页面，保留 1299 分的旧报价；不要提交。
2. 店家将售价改为 1599 分。用户用未刷新的旧报价提交，预期被拒绝并提示重新确认价格。
3. 用户刷新购物车，确认新价并下单；新订单金额为 3198 分。
4. 店家再次改价或下架，历史订单的名称、单价、总额不得随之改变。
5. 另一件商品加购后下架，再刷新购物车；应标记不可用，不能结算。

**通过条件：**旧价不能绕过服务端核价；下架不抹掉历史订单。

### TC-09 库存不足与调整记录

1. 建一件库存 1 的新商品，尝试结算 2 件，预期被拒绝，不能出现负库存或半笔订单。
2. 店家增加库存 3，填写原因，预期可售为 4。
3. 尝试扣减超过当前可售的数量，预期拒绝且库存不变；填写 0 调整量应被页面拒绝。
4. 核对库存调整流水中有原因和操作人。多明细事务回滚使用自动化回归进一步确认。

## A5 故障与定时规则演练

以下参数追加到终端 A 原商城启动命令，保留相同的手工库路径。每次先 Ctrl+C 停止商城，再启动；修改参数只影响重启后的规则。

### TC-10 退款失败与同号重试

追加 `--tradetrail.mock-payments.refund-failures-before-success=1`，新建订单后走 TC-05 或 TC-06。

1. 首次退款执行后，售后仍为退款处理中，退款流水为失败。
2. 店家进入“异常恢复”，在“待重试退款”找到订单和失败原因。
3. 记录原退款 ID，点击“重试退款”，刷新售后和用户订单。

**预期：**退款完成，退款 ID 不变，执行次数为 2；没有第二笔退款、没有第二次库存回补。结束后去掉演练参数，避免后续所有新退款都被故意失败一次。

### TC-11 超时关单

追加 `--tradetrail.payment-minutes=1 --tradetrail.jobs.lifecycle-delay-ms=1000`。

新建待支付订单，不点击付款，等待约 60–75 秒后刷新。

**预期：**订单关闭，预留释放；已付款订单不应因超过此时间被关单。该配置只改变新订单保存的付款期限，不会重算旧订单期限。

迟到支付补偿使用[接口指南](#b2-可复制的-powershell-接口调试)中的专用步骤；页面已关闭订单不提供付款按钮，不能靠重复点击按钮复现该场景。

### TC-12 物流异常与中断恢复

准备一笔已发货订单：

1. 物流端提交 `OUT_FOR_DELIVERY`，再提交 `EXCEPTION`，最后 `RESUMED`；用户轨迹显示完整事件，数据库摘要应恢复为派送中，保留进度 3。
2. 暂停商城服务，物流服务保持运行；提交一条新的有效节点。物流端应记录失败/重试状态，而不是丢失事件。
3. 用同一个手工文件库重新启动商城，等待自动重试，刷新物流端和用户轨迹。

**预期：**同一个事件最终成功，轨迹不重复，主订单仍是已发货。重试采用退避，恢复不一定立即发生；耗尽自动重试后手工点击重试。未知运单、验签失败等 4xx 属于拒绝，不会一直自动重试，应先核对数据和配置。

### TC-13 自动确认收货

追加 `--tradetrail.auto-confirm-days=0 --tradetrail.jobs.lifecycle-delay-ms=1000`，对一笔无售后的已发货订单推送 `DELIVERED`，不手工确认。

**预期：**任务执行后自动完成；另一笔有售后冻结的已发货订单即使妥投，也不自动完成。0 天仅用于演练，测试后移除。当前退货寄回超时自动终止尚未实现，不列为已通过场景。

### TC-14 权限与请求重试

使用[接口指南](#b2-可复制的-powershell-接口调试)验证：匿名访问订单 401；用户访问店家写接口 403；缺少 CSRF 被拒绝；业务写请求缺少幂等键 400；相同键和相同请求重放同一结果；相同键改变请求体返回 409。

系统只内置一个客户账号，跨客户归属校验及多用户竞争使用自动化测试中的测试身份，不自行给演示账号提权。

## A6 自动化补充检查

统一执行本页 [B7 自动化回归](#b7-自动化定向回归) 的命令。手工流程和自动化使用不同的数据库与端口，不混用结果。

## A7 验收记录模板

复制以下内容到自己的测试记录中，不填写真实个人信息或完整 Cookie/CSRF/签名密钥。

```text
测试日期 / 执行人：
Git 提交：git rev-parse --short HEAD 的输出
环境：Windows / JDK / Node / H2 或 MySQL
商城与物流地址：
启用的故障演练参数：
用例编号：
商品 ID / 订单 ID / 售后 ID / 运单号：
复现步骤：
预期状态、金额与库存：
实际状态、金额与库存：
HTTP 状态 / code / traceId：
截图或日志位置：
结果：通过 / 失败 / 未执行
```

退出时对四个手工服务分别按 Ctrl+C。若需全新数据，下一次启动改用新的专属目录，例如 `data/manual-qa-02/`；不要为重测直接删除既有开发库或 Docker 数据卷。

---

## B1 分清三个请求边界

| 调用位置 | 请求示例 | 身份与防护 |
|---|---|---|
| 用户/店家浏览器 | `http://127.0.0.1:5173/api/orders` | Vite 去掉 `/api`；会话 Cookie、CSRF、业务幂等键 |
| PowerShell / Postman 直连商城 | `http://127.0.0.1:8080/orders` | 路径不带 `/api`；工具自行保留 Cookie 和登录后的 CSRF |
| 物流/支付服务回调 | `/callbacks/logistics/mock`、`/callbacks/payments/mock` | 原始报文 HMAC 签名和时间窗口；不靠浏览器登录授权 |

先 GET `/session` 获取 CSRF，再 POST `/login`，登录后必须重新 GET `/session` 获取更新后的 token。直接访问商城 `/` 返回未登录或未配置页面，不是健康检查失败；检查 `/session` 即可。项目没有配置通用 `/actuator/health` 或 Swagger 页面。

## B2 可复制的 PowerShell 接口调试

要求 PowerShell 7。按 B2.1 → B2.2 → B2.3 顺序在同一终端执行；变量会在后续步骤复用。默认不启用退款故障演练参数，正常流程才会自动退款成功。

历史示例校验：2026-10-28 已将 B2.1–B2.5 的代码块连续执行于临时 H2 内存库，仅将地址改为隔离端口 18082，全部断言通过。本次文档合并仅核对语法和引用，没有重新执行业务流程；14 个手工用例需按实际执行结果填写。

### B2.1 登录与请求助手

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

### B2.2 创建商品、订单并验证幂等

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

### B2.3 异步支付、发货、确认收货

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

这里直接确认收货，用于验证接口；当前服务允许用户在发货后主动确认，不强制先有妥投轨迹。完整物流 HTTP 流程从仓库根目录执行 `./ai-agent/tools/smoke.ps1` 或手工 TC-03。签名回调应由模拟服务发送，不能把普通登录请求伪装成渠道通知。

### B2.4 迟到支付补偿

复用 B2.1 的助手和 B2.2 的商品，但创建一笔新订单。此例用主动关闭模拟“关单先发生”的分支；真正的超时竞争由定时用例及集成测试验证。

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

### B2.5 权限拒绝示例

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

## B3 配置与定时参数

参数与改端口时的联动集中维护在[开发与部署](development-and-deployment.md)。退款失败、加速超时及自动确认的具体演练见本页 TC-10 至 TC-13。

## B4 IDE 断点调试

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

## B5 数据库只读核对

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

## B6 常见故障定位

启动、登录、支付、物流和旧 MySQL 卷问题统一见[开发与部署：常见问题](development-and-deployment.md#常见问题)。接口失败同时记录 HTTP 状态、code、traceId 和发生时间。

## B7 自动化定向回归

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

### 浏览器、构建与两服务冒烟

先停止占用 JAR 的手工服务，完成打包；不需要删除任何数据库。

```powershell
mvn package
npm ci --prefix frontend --include=dev
npm ci --prefix frontend/customer --include=dev
npm ci --prefix frontend/merchant --include=dev
npm --prefix frontend run format:check
npm --prefix frontend/customer run build
npm --prefix frontend/merchant run build
Remove-Item Env:MERCHANT_URL, Env:CUSTOMER_URL -ErrorAction SilentlyContinue
Push-Location frontend
npx playwright install chromium
npm run test:e2e
Pop-Location
```

浏览器验收自动启动商城 18080、店家 15174、用户 15173，使用独立 H2 内存库和首次退款失败参数；不自动启动物流服务。两服务 HTTP 闭环需另外启动商城及物流后执行：

```powershell
./ai-agent/tools/smoke.ps1 -MallUrl http://127.0.0.1:8080 -LogisticsUrl http://localhost:8081
```

冒烟脚本会向指定数据库写入演示商品和订单，服务地址必须对应自己的测试环境。查看浏览器报告在 `frontend` 目录运行 `npx playwright show-report`；该命令启动报告服务，结束时 Ctrl+C。

需要保留截图或报告时，在下一次运行前复制到自己的验收记录目录；Playwright 会覆盖输出目录。历史通过数量见[交付记录](Plan.md#6-历史验收证据)，本次调试结论以你当前提交上的实际报告为准。
