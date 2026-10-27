# TradeTrail（商迹）

单店官方商城演示工程。包含用户商城、店家管理台、商城服务和独立物流模拟服务。支持商品维护、购物车结算、订单快照与库存预留、异步模拟支付、发货、物流推送、确认收货、售后审核、退货验收与整单退款。详细设计见 [Plan.md](Plan.md)，实际验收范围见 [交付检查](docs/delivery-checklist.md)。

## 环境

- JDK 21、Maven 3.9、Node.js 22.12 以上
- Docker Desktop（使用 MySQL + Redis 开发时）
- 文件模式 H2 仍可用于不启动 Docker 的轻量测试

## 启动

推荐先在仓库根目录启动 Docker 中间件。MySQL 分别为商城和物流模拟服务建立 `tradetrail`、`tradetrail_logistics` 两个数据库；Redis 当前仅提供开发环境服务，业务代码尚未使用。

```powershell
docker compose up -d mysql redis
docker compose ps
```

首次启动需要等待 MySQL 状态变为 `healthy`。在仓库根目录构建并启动商城；设置 `mysql` profile 后才会连接 Docker MySQL：

```powershell
mvn clean package
$env:SPRING_PROFILES_ACTIVE = 'mysql'
java -jar tradetrail-boot/target/tradetrail-boot-0.1.0-SNAPSHOT.jar
```

另开终端，在仓库根目录启动物流模拟服务：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'mysql'
java -jar tradetrail-logistics-simulator/target/tradetrail-logistics-simulator-0.1.0-SNAPSHOT.jar
```

两个后端分别运行时，每个终端都要设置 profile。若只想用 H2，省略这两行环境变量即可。MySQL 与 H2 的数据互不迁移；Flyway 会在所连接的数据库中创建表。MySQL 容器数据保存在 Docker 命名卷中，`docker compose down` 不会删除数据。物流数据库的初始化 SQL 只在 MySQL 数据卷首次创建时执行；已有旧数据卷时需先手动建立 `tradetrail_logistics` 数据库并授权，不要直接删除数据卷。

再分别启动两个前端：

```powershell
cd frontend/customer
npm ci
npm run dev
```

```powershell
cd frontend/merchant
npm ci
npm run dev
```

| 入口 | 地址 | 演示账号 |
|---|---|---|
| 用户商城 | `http://127.0.0.1:5173` | `customer / customer123` |
| 店家管理台 | `http://127.0.0.1:5174` | `merchant / merchant123` |
| 物流模拟端 | `http://localhost:8081` | `logistics / logistics123` |

三个账号只用于本地演示。两个服务使用相同的本地演示签名密钥；连接外部环境前须通过 `TRADETRAIL_LOGISTICS_SECRET` 同时设置新的密钥。MySQL 数据由 Docker 卷保存；H2 数据保存在仓库根目录的 `data/`。表结构由 Flyway 自动迁移。

店家与用户演示请使用不同浏览器配置文件或一个普通窗口、一个无痕窗口。同一主机不同端口共享 Cookie，不能靠两个标签页隔离角色。两个前端均通过 `/api` 代理访问后端，页面路径 `/login`、`/products`、`/orders` 不会再与接口争抢路由。后端不在 8080 时，在启动前端的终端设置 `$env:TRADETRAIL_API_TARGET='http://127.0.0.1:实际端口'`。生产静态部署也须代理 `/api/` 到商城并去掉此前缀，同时将页面路由回退到 `index.html`。

登录页出现连接错误时先确认商城后端已启动、代理地址正确；点击“重新检查连接”可恢复。登录成功返回 JSON，不再通过服务端重定向触发跨端口请求。

## 演示路径

1. 店家登录，新建商品并上架，可按原因调整库存。
2. 用户登录，浏览商品、加入购物车、选择商品并填写收货信息，结算后确认模拟付款；订单在签名支付通知到账后变为待发货。
3. 店家在订单列表发货，复制生成的 `MOCK-...` 运单号。
4. 物流操作者在独立页面输入运单号和轨迹节点，页面显示推送结果；失败可以重试。
5. 用户查看物流时间线并确认收货；也可申请售后。未发货走整单仅退款，已发货/已完成走退货退款。
6. 店家审核售后；退货场景由用户填写寄回运单，店家验收并选择是否质检合格。只有合格实物回补库存；退款完成后已完成订单仍保留完成状态。
7. 店家在“异常恢复”检查失败退款或耗尽自动重试的任务，按原业务编号重新处理。

支付和退款均为 mock，不发生真实扣款。支付尝试、渠道结果、签名 HTTP 通知、退款流水和 outbox 独立记录；默认退款成功。需要演示失败后恢复时，为商城启动命令添加 `--tradetrail.mock-payments.refund-failures-before-success=1`，每笔新退款首次失败，店家可在“异常恢复”重试同一退款号。该参数只控制模拟渠道演练，不代表真实支付渠道接入。

超时关单、自动确认和 outbox 任务默认开启。数据库连接使用 READ COMMITTED 和 UTC；物流报文 `occurredAt` 的无时区时间按 UTC 解释，前端转为用户本地时间。商城和物流模拟服务应一起升级，旧版本本地时区报文不能直接混用。

## 验证

```powershell
mvn test
mvn -Pmysql-it verify
cd frontend/customer; npm run build
cd ../merchant; npm run build
```

真实 MySQL 测试通过 Testcontainers 创建并销毁独立容器，需运行 Docker，不连接开发数据库。H2 接口测试每个 Spring 上下文使用独立内存库。

浏览器验收（先在根目录完成 Maven 打包，并安装两端前端依赖）：

```powershell
cd frontend
npm ci
npx playwright install chromium
npm run format:check
npm run test:e2e
```

测试自动启动 18080 的内存数据库后端和 15173/15174 两端服务，结束后关闭；这些端口须空闲。覆盖登录错误、网络中断恢复、响应丢失后同键重试、购物车结算、异步付款、发货、确认收货、退货审核、模拟退款失败和人工重试，以及 375px 窄屏布局。截图和报告在 `frontend/test-results` 与 `frontend/playwright-report`，不提交生成物。GitHub Actions 执行相同的构建、数据库、架构和浏览器检查。

如需改变连接参数，商城使用 `TRADETRAIL_DB_URL`，物流端使用 `TRADETRAIL_LOGISTICS_DB_URL`；两者共用 `TRADETRAIL_DB_USER` 和 `TRADETRAIL_DB_PASSWORD`。MySQL 映射到本机 `127.0.0.1:3310`，Redis 映射到 `127.0.0.1:6382`；容器内部仍分别使用 3306 和 6379。Redis 目前不参与登录、库存或订单流程。可以用 `docker compose exec redis redis-cli ping` 检查连接。

两个后端服务启动后，可从仓库根目录运行 `./ai-agent/tools/smoke.ps1`。脚本会使用演示账号创建商品与订单，并由物流模拟端推送一条轨迹；成功时输出 `PASS` 和相应编号。脚本会写入演示数据。
