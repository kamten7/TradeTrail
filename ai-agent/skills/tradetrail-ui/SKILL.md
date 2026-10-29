---
name: tradetrail-ui
description: Maintain TradeTrail customer Vue/Vant pages, merchant Vue/Element Plus pages, and logistics Thymeleaf UI with existing styles, request contracts, responsive behavior, and business states. Use for UI changes, not Java-only or ordinary documentation work.
---

# TradeTrail 页面维护规范

先读 [agent.md](../../../agent.md)。本 Skill 约束项目页面维护，不授权提交、推送或上传。纯文档整理不要求启动页面或修改样式。

## 先定位实际入口

| 入口 | 页面及组件 | 样式来源 |
|---|---|---|
| 用户商城 | `frontend/customer/src/pages`，Vant、Pinia | [tokens.css](../../../frontend/customer/src/tokens.css) 和 [style.css](../../../frontend/customer/src/style.css) |
| 店家管理台 | `frontend/merchant/src/pages`，Element Plus、共享页面组件 | [tokens.css](../../../frontend/merchant/src/tokens.css) 和 [style.css](../../../frontend/merchant/src/style.css) |
| 物流端 | 独立服务 Thymeleaf | [index.html](../../../tradetrail-logistics-simulator/src/main/resources/templates/index.html) 中现有布局和样式 |

两端现有 `--tt-*` token 是运行样式来源；AI 目录不再保存可误用的第三份 CSS。改主题时核对两个应用的组件库映射，不要求物流模板直接 import Vue CSS。通用颜色、间距、圆角优先沿用现有 token，必要的新样式按真实页面需求定义。

## 业务与交互

- 页面通过各自 `api.ts` 使用 [shared/http.ts](../../../frontend/shared/http.ts)，不另写绕过 Cookie、CSRF、超时和稳定请求键的 fetch。
- 金额、库存、身份和状态以服务端为准。`allowedActions` 可辅助展示，但操作仍可能因角色、期限、库存或售后规则被拒绝。
- 保留加载、空、错误和可重试状态。请求超时不等于业务失败，重试保留原键，不能为恢复界面随意创建新订单。
- 将订单、支付、物流和售后分别展示；已完成订单退款后主状态仍完成，退款失败从失败退款入口恢复。
- 用户、店家用独立浏览器会话验证，避免端口相同主机的 Cookie 覆盖。
- 沿用用户端移动布局、店家桌面表格及窄屏适配、物流独立表单，不引入第二套组件库。
- 表单有标签及校验反馈，异步按钮有进行中状态，移动触控区域保持可用，状态和错误不只靠颜色区分。

## 参考与验证

按页面类型阅读 [page-recipes.md](references/page-recipes.md)。优先复用现有页面和 `PageHeading/LoadError/AppIcon` 等店家组件，不预设批量导出、预计送达、图片管理等尚未实现能力。

修改后运行对应应用 `npm run build`；结构和交互变化再检查受影响页面、窄屏、错误恢复及必要的浏览器用例。格式命令在 `frontend/package.json`，测试步骤见[测试与调试](../../../docs/testing.md)。不得使用 HTML 注释。
