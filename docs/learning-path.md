# ActionGraph 学习路径

先走 Golden Path，再按接入阶段逐步外扩。常规业务接入第一天只需要关注 Goal schema、Action 注解和 `ActionGraph` 根门面。

| 阶段 | 阅读入口 | 到达结果 |
|---|---|---|
| L0 Schema-first Goal | `quick-start.html#l0`, `frameworkization/golden-path.md` | 一个注解类、一个 schema record、一个 `ActionGraph.start(...)` 调用 |
| L1 人审与恢复 | `quick-start.html#l1`, `frameworkization/human-review.md`, `frameworkization/permission-policy.md` | 高风险 Action 挂起、外部审批写入决定、同一 run 恢复执行 |
| L2 自然语言入口 | `quick-start.html#l2`, `frameworkization/goal-catalog-prompt.md`, `frameworkization/llm-smoke.md` | `ActionGraph.chat(...)`、缺参澄清、LLM 或规则解释器兜底 |
| L3 生产化 | `quick-start.html#l3`, `frameworkization/jdbc-persistence.md`, `frameworkization/observability-spi.md`, `frameworkization/public-api-compatibility.md` | JDBC Trace/挂起快照、脱敏、指标、公共 API 兼容性纪律 |
| L4 跨服务 | `quick-start.html#l4`, `frameworkization/runtime-api.md`, `frameworkization/runtime-invocation-spi.md`, `frameworkization/control-plane-api.md` | HTTP 网关、Java 8 客户端、回调和外部事件边界 |

## 打包复用

业务域需要作为可复用 artifact 分发时，再阅读这些文档：

- `frameworkization/extension-points.md`
- `frameworkization/dependency-composition.md`
- `frameworkization/module-governance.md`
- `frameworkization/component-catalog.md`

## 进阶 SPI

注解模型覆盖不了的场景，或者要开发框架扩展时，再进入这一层：

- `frameworkization/annotation-action-usage.md`
- `frameworkization/blackboard-multi-instance.md`
- `frameworkization/api-stability-annotations.md`
- `frameworkization/spring-boot-starter.md`
- `frameworkization/control-plane-starter.md`
- `frameworkization/governance-spring-boot-starter.md`
- `frameworkization/java8-legacy-integration.md`
- `frameworkization/v2-suspend-resume.md`
- `frameworkization/v3-dynamic-repair.md`
- `frameworkization/v4-memory-context.md`
- `frameworkization/ms1-durability.md`

## 运营与试点

部署、审计、试点验收和真实系统接入使用这些材料：

- `frameworkization/claims-precheck-console.md`
- `frameworkization/claims-precheck-postgresql.md`
- `frameworkization/claims-precheck-review-callbacks.md`
- `f1-readiness-status.md`
- `f1-pilot-validation-pack.md`
- `dhk-integration.md`

## 内部设计记录

PRD 与策略文档解释决策背景，适合评审和追溯，不作为第一天的接入路径：

- `PRD-v0.md`
- `PRD-F0-finance.md`
- `PRD-DX1.md`
- `PRD-DX2.md`
- `PRD-DX3.md`
- `PRD-MS1.md`
- `PRD-MS2.md`
