# 开发、配置与演示部署参考

本页收录运行参数、接口约定、开发流程和部署排错说明。项目介绍与功能展示见 [README](../README.md)，逐项测试见 [手工验收](testing/manual-acceptance.md)。

## 配置参考

后端参数可追加到启动命令，例如 `--server.port=18080`；前端变量在启动 Vite 的终端设置。配置源见[商城默认配置](../tradetrail-boot/src/main/resources/application.yml)、[商城 MySQL 配置](../tradetrail-boot/src/main/resources/application-mysql.yml)和[物流默认配置](../tradetrail-logistics-simulator/src/main/resources/application.yml)。

### 服务与数据库

| 配置 | 适用端 | 默认值 / 说明 |
|---|---|---|
| `server.port` | 商城 / 物流 | `8080` / `8081` |
| `spring.profiles.active` | 两个后端 | 默认 H2；`mysql` 使用 MySQL profile |
| `spring.datasource.url` | 两个后端 | 可直接覆盖 JDBC URL；默认文件相对于进程工作目录 |
| `TRADETRAIL_DB_URL` | 商城 MySQL | `localhost:3310/tradetrail`，完整 URL 见配置文件 |
| `TRADETRAIL_LOGISTICS_DB_URL` | 物流 MySQL | `localhost:3310/tradetrail_logistics` |
| `TRADETRAIL_DB_USER` / `TRADETRAIL_DB_PASSWORD` | 两个后端 MySQL | 本地演示均为 `tradetrail` |
| `TRADETRAIL_API_TARGET` | 两个前端 | `http://127.0.0.1:8080`，修改后重启 Vite |
| `tradetrail.payment.callback-url` | 商城 | `http://127.0.0.1:8080`，mock 支付通知回调目标 |
| `tradetrail.mall-url` / `TRADETRAIL_MALL_URL` | 物流 | `http://localhost:8080`，商城推送目标 |
| `TRADETRAIL_LOGISTICS_SECRET` | 两个后端 | 双方必须一致；替代本地演示物流密钥 |
| `tradetrail.payment.secret` | 商城 mock 支付 | 替代本地演示支付签名密钥；不要提交真实密钥 |

商城改到 18080 时，需要协调三个方向，不能只改 HTTP 监听端口：

```powershell
# 商城终端，选择需要的 profile
java -jar tradetrail-boot/target/tradetrail-boot-0.1.0-SNAPSHOT.jar --spring.profiles.active=default --server.port=18080 --tradetrail.payment.callback-url=http://127.0.0.1:18080
```

```powershell
# 物流终端
java -jar tradetrail-logistics-simulator/target/tradetrail-logistics-simulator-0.1.0-SNAPSHOT.jar --spring.profiles.active=default --tradetrail.mall-url=http://127.0.0.1:18080
```

```powershell
# 两个前端的启动终端分别设置，再执行各自的 npm run dev
$env:TRADETRAIL_API_TARGET = 'http://127.0.0.1:18080'
```

### 业务规则与故障演练

| 启动参数 | 默认值 | 说明 |
|---|---:|---|
| `tradetrail.payment-minutes` | 30 | 新订单付款期限，分钟 |
| `tradetrail.auto-confirm-days` | 7 | 妥投后的自动确认等待天数 |
| `tradetrail.after-sale-days` | 15 | 已完成订单的售后申请期限，演示规则 |
| `tradetrail.jobs.delay-ms` | 2000 | 商城 outbox / 物流恢复任务的执行间隔 |
| `tradetrail.jobs.lifecycle-delay-ms` | 10000 | 商城超时关单与自动确认间隔 |
| `tradetrail.jobs.enabled` | true | 商城定时任务开关，关闭后异步支付也不会自动投递 |
| `tradetrail.mock-payments.enabled` | true | 允许 mock 新支付与收银台操作 |
| `tradetrail.mock-payments.refund-failures-before-success` | 0 | 每笔模拟退款成功前的故意失败次数 |

退款失败演练可追加 `--tradetrail.mock-payments.refund-failures-before-success=1`。随后新建订单并申请退款，首次执行会记录失败；店家在“异常恢复”沿用原退款号重试。售后在此期间保持退款处理中，合格退货的实物可能已经回库，重试不得再次回补。完整步骤及恢复默认配置的方法见[故障演练](../docs/testing/manual-acceptance.md)。

## 接口约定

| 项目 | 约定 |
|---|---|
| 地址 | 浏览器访问 `/api/...`；调试工具直连商城时去掉 `/api` |
| 登录 | GET `/session` 获取 CSRF → POST `/login` → 再次 GET `/session` 更新 token；登录成功返回 JSON |
| 业务写请求 | 携带会话 Cookie、`X-XSRF-TOKEN` 与不超过 100 字符的 `Idempotency-Key` |
| 金额 | `priceCents`、`totalCents` 等字段均为整数分 |
| 下单 | 提交 `items`、每项期望价格与 `address`，由服务端核价 |
| 分页 | 支持分页的接口以 `page=0` 开始，`size` 为 1–100；并非所有列表已使用数据库分页 |
| 支付与物流回调 | 分别校验渠道 HMAC 协议和事件身份，不使用浏览器 CSRF 合同 |

业务失败响应示例，字段值随请求和状态变化：

```json
{
  "code": "ORDER_TRANSITION_DENIED",
  "message": "当前订单不允许此操作",
  "traceId": "example-trace-id",
  "currentStatus": "PENDING_PAYMENT",
  "allowedActions": ["PAY", "CANCEL"]
}
```

400 通常表示参数不合法，401 为未登录，403 为权限或 CSRF 拒绝，404 为资源不存在，409 为业务冲突，500 为服务异常。排错时同时保留 HTTP 状态、`code`、`traceId` 和发生时间。可执行的请求示例见[接口调试指南](../docs/testing/debugging.md)，当前接口变化见[业务与接口说明](../docs/implementation-roadmap.md)。

## 本地开发约定

1. 从仓库根 `pom.xml` 导入 Maven 工程，设置 JDK 21。IDE 启动商城类 `com.tradetrail.TradeTrailApplication`，物流类 `com.tradetrail.simulator.LogisticsSimulatorApplication`；两者工作目录都设为仓库根，避免创建意外的相对路径数据库。
2. 前端分别维护应用依赖，共享请求与模型放在 `frontend/shared`。三个 `package-lock.json` 分别对应两个应用和验收工具；首次安装按所在目录运行 `npm ci`。
3. 后端规则变更核对入口、事务、库存与状态、回调、页面及文档。HTTP 接口按角色与业务资源分组，每个方法和路径有独立测试类；缺陷修复补可复现的回归测试。
4. 新表或字段通过各服务自己的 Flyway 版本迁移交付。不要修改已经在目标数据库执行的历史迁移来实现新结构。
5. 提交前运行受影响的测试和构建，检查 diff。提交说明包括行为变化、验证结果和已知限制，格式沿用 `类型(范围): 中文说明`，例如 `fix(order): 修复售后冻结下的履约校验`。

前端格式化与检查（需先安装 `frontend` 的验收工具依赖）：

```powershell
npm --prefix frontend run format
npm --prefix frontend run format:check
```

仅调试特定后端用例时，可按[定向回归命令](../docs/testing/debugging.md)缩小范围；最终交付再运行与变更对应的集成与浏览器检查。前端热更新不替代生产构建，修改 Java 后只运行 `mvn test` 也不会更新 Playwright 启动的 JAR。

## 本机演示部署

仓库提供开发启动和可独立打包的产物，目前 Docker Compose 只编排 MySQL 与 Redis，未编排商城、物流服务或前端。演示部署由两个 Java 进程、两套静态前端和独立数据库组成。

| 产物 | 生成方式 | 运行方式 |
|---|---|---|
| 商城 JAR | 根目录 `mvn package` | `java -jar tradetrail-boot/target/tradetrail-boot-0.1.0-SNAPSHOT.jar` |
| 物流 JAR | 同一次 Maven 构建 | `java -jar tradetrail-logistics-simulator/target/tradetrail-logistics-simulator-0.1.0-SNAPSHOT.jar` |
| 用户端静态文件 | `npm --prefix frontend/customer run build` | 部署 `frontend/customer/dist` |
| 店家端静态文件 | `npm --prefix frontend/merchant run build` | 部署 `frontend/merchant/dist` |

静态构建文件不包含运行中的 Vite 代理。使用 Nginx 等静态服务器时，需要单独处理 `/api/` 反向代理和 Vue History 路由回退。以下为 Nginx `http` 上下文中的示例，域名、绝对目录及后端地址应替换成自己的演示环境；配置文件不由本项目自动安装。

```nginx
server {
    listen 80;
    server_name shop.tradetrail.local;
    root D:/JavaCode/TradeTrail/frontend/customer/dist;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}

server {
    listen 80;
    server_name merchant.tradetrail.local;
    root D:/JavaCode/TradeTrail/frontend/merchant/dist;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:8080/;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

`proxy_pass` 末尾 `/` 用于去掉 `/api/` 前缀。两个域名须先通过本地名称解析指向演示服务器，分别使用各自的会话 Cookie。物流操作页仍由物流服务提供，不属于上述静态产物；mock 支付和物流回调按各服务配置直连商城。

部署后依次检查：页面可打开 → 直接刷新 `/orders` 正常 → `/api/session` 返回 JSON → 登录及写操作成功 → 一笔支付能到账 → 物流能推送。当前演示账户定义在安全配置中，没有注册、密码管理或完整身份管理系统；公网生产部署不在本节的验收范围内。

## 数据、日志与日常维护

| 项目 | 约定 |
|---|---|
| H2 数据 | 默认商城 `data/tradetrail.mv.db`、物流 `data/logistics.mv.db`；相对于进程工作目录 |
| MySQL 数据 | Docker 命名卷保存，两服务使用各自数据库 |
| 应用日志 | 默认输出到启动终端，未配置统一日志平台或固定日志文件 |
| 请求追踪 | 业务失败响应与订单状态日志中的 `traceId`，结合发生时间定位 |
| 生成文件 | `target`、`dist`、`node_modules`、测试报告及本地数据按 `.gitignore` 排除 |
| 停止服务 | 手工启动终端按 Ctrl+C；停止数据库可执行 `docker compose stop mysql redis` |

演示数据需要保留时，H2 应先停服务再备份对应文件，MySQL 使用数据库备份工具并同时保留迁移版本。更新前记录 Git 提交和实际 JDBC URL，避免把切换 profile 当成数据迁移。新建一套测试数据可换用[手工验收文档](../docs/testing/manual-acceptance.md)中的独立路径，保留原库用于问题复现。

异常恢复页分别展示失败退款和耗尽重试的 outbox 任务；仍处于 `PENDING` 或 `PROCESSING` 的投递不一定出现于人工队列。排查时关联支付号、退款号与 outbox 的 `aggregate_id`，不能只看订单主状态。

## 常见问题

| 现象 | 首先检查 |
|---|---|
| 页面出现“暂时无法连接商城服务” | 先请求商城 `/session`，再请求前端 `/api/session`，区分后端与代理问题 |
| 登录后另一端变成无权限 | 用户和店家是否在同一浏览器会话中互相覆盖身份 |
| 付款后仍待支付 | mock 收银台是否成功、调度是否开启、支付回调 URL 是否匹配端口 |
| 售后保持退款处理中 | 查退款 FAILED 还是 SUCCEEDED；分别检查人工重试或退款成功事件投递 |
| 物流被拒绝或持续重试 | 运单是否已创建、密钥与时间是否一致、商城是否可达 |
| 重启后“原数据消失” | 启动 profile、JDBC URL、工作目录是否改变，是否使用了内存数据库 |
| MySQL 缺少物流数据库 | 旧数据卷不会重新运行初始化脚本，按调试文档补库和授权 |
| JAR 无法覆盖或数据库文件被锁 | 原项目进程是否仍在运行，是否有两个进程打开同一 H2 文件 |
| 浏览器验收未启动服务或端口冲突 | 清除遗留 URL 覆盖变量，并确认 18080、15173、15174 空闲 |

完整状态码、SQL、断点和故障复现步骤见[接口、断点与排错指南](../docs/testing/debugging.md)。反馈问题时提供 Git 提交、启动参数、复现步骤、记录 ID、预期与实际结果、脱敏错误响应；可直接使用[验收记录模板](../docs/testing/manual-acceptance.md)。

