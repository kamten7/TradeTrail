# TradeTrail（商迹）

单店官方商城演示工程。包含用户商城、店家管理台、商城服务和独立物流模拟服务。当前版本覆盖商品发布、库存调整、购物车、下单与库存预留、模拟付款、发货、物流轨迹推送和基础售后申请。详细设计见 [Plan.md](Plan.md)，分阶段记录见 [实施路线](docs/implementation-roadmap.md)。

## 环境

- JDK 21、Maven 3.9、Node.js 20 以上
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

## 演示路径

1. 店家登录，新建商品并上架，可按原因调整库存。
2. 用户登录，浏览商品、加入购物车、下单并点击模拟付款。
3. 店家在订单列表发货，复制生成的 `MOCK-...` 运单号。
4. 物流操作者在独立页面输入运单号和轨迹节点，页面显示推送结果；失败可以重试。
5. 用户在订单页面查看物流时间线，也可提交基础售后申请。

模拟付款当前由商城内部立即处理，暂无真实支付渠道。售后目前只受理申请，审核、退款与退货闭环以及 outbox 恢复仍在后续阶段；详见实施路线。

## 验证

```powershell
mvn test
cd frontend/customer; npm run build
cd ../merchant; npm run build
```

如需改变连接参数，商城使用 `TRADETRAIL_DB_URL`，物流端使用 `TRADETRAIL_LOGISTICS_DB_URL`；两者共用 `TRADETRAIL_DB_USER` 和 `TRADETRAIL_DB_PASSWORD`。MySQL 映射到本机 `127.0.0.1:3310`，Redis 映射到 `127.0.0.1:6382`；容器内部仍分别使用 3306 和 6379。Redis 目前不参与登录、库存或订单流程。可以用 `docker compose exec redis redis-cli ping` 检查连接。交易 SQL 仍需在 MySQL 上完成集成验证。

两个后端服务启动后，可从仓库根目录运行 `./ai-agent/tools/smoke.ps1`。脚本会使用演示账号创建商品与订单，并由物流模拟端推送一条轨迹；成功时输出 `PASS` 和相应编号。脚本会写入演示数据。
