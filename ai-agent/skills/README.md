# 项目 Skills

每项 Skill 独立目录，格式遵循 Agent Skills 规范（`SKILL.md` + YAML frontmatter，可选 `references/`）：

```text
ai-agent/skills/<skill-name>/SKILL.md
ai-agent/skills/<skill-name>/references/*.md
```

Skill 的规则不得覆盖根目录 [agent.md](../../agent.md) 与 [Plan.md](../../Plan.md)；冲突时以设计文档为准，并同步修正 Skill。

## 当前 Skill

| Skill | 触发时机 | 内容 |
|---|---|---|
| `tradetrail-backend` | 写/改/审查 `tradetrail-*` 后端 Java 代码、加接口、写 SQL、加测试前 | 五条架构铁律 + 决策树；`references/layer-rules.md`（分层与跨域）、`references/code-templates.md`（条件更新/幂等/outbox/状态机/测试模板） |
| `tradetrail-ui` | 写/改三端页面、组件、样式、布局前 | 设计 token + 三端基线 + 页面骨架 + 一致性自查；`references/tokens.css`（可直接引入）、`references/page-recipes.md`（五类页面配方） |

## 使用方式

1. 每次让 AI 开发前，明确要求其先读取对应 Skill（例如“先读 `ai-agent/skills/tradetrail-backend/SKILL.md` 再动手”）；支持自动加载 Skill 的客户端会按 `description` 自动触发。
2. 生成或修改代码后，按 `SKILL.md` 末尾的禁止清单与自查表逐条核对。
3. 设计变更（新增业务域、改状态机、改视觉 token）时，先改 `Plan.md` / `docs/design/` / `references/`，再改代码，避免 Skill 与实现分叉。

## 新增 Skill 的判断标准

- 同一类要求被重复交代三次以上，才值得固化为 Skill
- 一个 Skill 只解决一类任务；跨领域内容拆成多个 Skill
- `SKILL.md` 控制在 150 行内，细节放 `references/`，只引用一层深度
