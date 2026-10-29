# TradeTrail AI Agent 规范与工具

核对日期：2026-10-29。仓库行为以根目录 [agent.md](../agent.md) 和用户当前要求为准；[AGENTS.md](../AGENTS.md) 是发现入口。项目范围与实现状态统一维护于 [docs/Plan.md](../docs/Plan.md)。只有用户明确要求时才提交、推送或上传。

## 项目 Skill

| Skill | 适用任务 | 按需参考 |
|---|---|---|
| [tradetrail-backend](skills/tradetrail-backend/SKILL.md) | 修改或审查 Java 后端、接口、SQL、交易规则与测试 | 分层边界、现有交易实现与验证入口 |
| [tradetrail-ui](skills/tradetrail-ui/SKILL.md) | 修改用户商城、店家管理台或物流操作页 | 页面配方、真实样式文件、请求及状态约定 |

Skill 描述能力与使用边界，不赋予执行外部操作或提交的权限。仅更新普通文档时，无需执行后端或页面开发流程；更新 Skill 时仍要核对其引用、示例与当前实现。

## 辅助工具

现有 [tools/smoke.ps1](tools/smoke.ps1) 验证商城下单、异步模拟支付、发货，以及独立物流的签名 HTTP 推送。它从接口创建演示数据，不直接改数据库；不是只读诊断工具。

从仓库根目录执行，要求 PowerShell 7，商城和物流已启动、使用相同物流密钥：

```powershell
./ai-agent/tools/smoke.ps1 -MallUrl http://127.0.0.1:8080 -LogisticsUrl http://localhost:8081
```

| 输入/输出 | 说明 |
|---|---|
| `MallUrl` | 商城后端地址，默认 `http://127.0.0.1:8080`，不带 `/api` |
| `LogisticsUrl` | 独立物流地址，默认 `http://127.0.0.1:8081` |
| 成功 | 终端输出 `PASS` 和本次商品、订单、运单、事件数量 |
| 失败 | 抛出错误；结合订单、支付、outbox 和物流投递记录定位 |

脚本不会安装依赖、启动服务、提交或推送。测试库和故障演练的完整操作见[测试与调试](../docs/testing.md)，参数和部署见[开发与部署](../docs/development-and-deployment.md)。

## 资源维护

- Skill 入口保留真实使用范围、关键业务约束和按需链接，避免复制整个项目设计。
- 规范要求与实现现状分开表述；未完成项写入 Plan，不能靠修改文案视为实现。
- 示例优先链接现有可编译代码，不维护虚构状态名或另一套代码模板。
- 前端运行样式位于各应用源码，不在 AI 目录再保存一份易失同步的 CSS。
- 不加入空目录占位、重复目录 README、临时报告、个人配置、密钥或真实个人数据。
