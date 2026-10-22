---
name: tradetrail-ui
description: Applies TradeTrail's design system when creating or editing customer H5 pages (Vue 3 + Vant), merchant admin pages (Vue 3 + Element Plus), or logistics simulator pages (Thymeleaf). Use it before writing any .vue component, stylesheet, layout, form, table, or page skeleton. Skip it for backend Java code, SQL, and pure business-logic changes.
---

# TradeTrail 设计系统

## Overview

本技能保证三端界面在配色、间距、圆角和信息层级上一致。用户端与服务端是两个独立 Vue 应用，物流端是 Thymeleaf 服务端页面；三端共用本文件的 token，不各自发明样式。

**关键词**：Vant、Element Plus、设计 token、间距、圆角、页面骨架、一致性、移动优先

## 设计 token（唯一来源，禁止自创数值）

```
主色        #FF6B35   价格、主按钮、选中态
主色-深     #E05520   按下态
辅色        #2D3436   正文标题
文字次级    #636E72   说明文字
边框        #E9ECEF
背景        #F7F8FA   页面底色
卡片        #FFFFFF
成功        #00B578   配送正常
警告        #FF8F1F   异常
危险        #F53F3F   失败、删除

圆角        卡片 8px   按钮 6px   标签/徽章 4px
阴影        卡片 0 2px 8px rgba(0,0,0,.06)
间距        只用 4 的倍数：4 8 12 16 24 32
字号        12 14 16 20 24 32
行高        正文 1.6   标题 1.3
```

完整 CSS 变量见 `references/tokens.css`，两个 Vue 应用直接 import，不在组件里重复定义。

## 三端基线

| 端 | 组件库 | 布局基线 |
|---|---|---|
| 用户端（H5） | Vant 4 | 移动优先，单列卡片流；底部 Tabbar（首页/购物车/订单/我的）；页面左右留白 16px；商品价格 20px 加粗主色 |
| 店家管理端 | Element Plus | 侧边栏 220px + 顶栏 56px + 内容区 padding 24px；表格 `stripe` + 操作列固定右侧；表单 label 右对齐宽 100px，控件 `size="default"`；按钮主操作在左 |
| 物流模拟端 | Thymeleaf + 原生 CSS | 单页表单 + 结果卡片；顶部标题区、中部表单、底部结果列表；不引入前端框架 |

## 每个页面的固定骨架

```
1 页面标题区     标题 20px 加粗 + 右侧主操作按钮
2 筛选/操作区    查询条件、批量操作（管理端）；无则不占位
3 内容区         空态 / 加载态 / 错误态三件套必须都有
4 分页或结束标记  列表页必做；移动端用"加载更多"
```

## 提交前一致性自查（8 条）

1. 颜色只来自 token，无 `#fff` 之外的临时色值
2. 间距是 4 的倍数，无 13px、18px 这类魔法值
3. 卡片圆角 8px、按钮 6px，与既有页面一致
4. 同一层级的字号一致（列表标题 16px、辅助信息 14px 或 12px）
5. 空态、加载态、错误态都有，不是只写成功路径
6. 金额、数量右对齐；状态用徽章不用纯文字
7. 移动端点击区域不小于 44px
8. 无内联 style 堆砌（超过 3 个属性就提到 class）

## 页面配方

- `references/page-recipes.md`：列表页、详情页、表单页、订单时间线、空态的布局与组件选型
- 新页面优先复制既有同类型页面结构，再改内容，不要从空白文件起手

## 禁止清单

- 不使用 HTML 注释（项目规范），需要说明时用相邻文档或组件内 TS 注释
- 不引入第二套 UI 组件库
- 不自定义与 token 冲突的主题色；需要改主色时改 token 文件
- 不用纯文字堆砌表格（管理端必须用 el-table）
- 不在移动端做横向滚动表格
