# PRD — STD1 运行主体与 Agent 身份（Principal 贯穿 / 委托链入审计 / 控制面 OAuth 2.1）

> 交付对象：实现方（Codex）
> 前置版本：0.2.0-SNAPSHOT main（DX4 放行后开工；本期 DoD 延续"必须已提交推送"条款）
> 动机：行业坐标（NIST AI Agent 标准计划、OAuth 2.1/MCP/A2A 被点名为基石、"每次调用动态重评权限"共识）对照下，框架的两个缺口：① 运行没有"代表谁"的主体概念——审计能证明发生了什么，证不了**谁让它发生**；② control-plane 仍用静态共享密钥（行业正在抛弃的形态）。金融安全评审必问，先于试点补齐。
> 状态：可直接实施，A/B 分段验收

---

## 0. 一句话目标

每次运行从发起那一刻就携带**主体**（谁、经哪个系统、代表谁），贯穿权限评估、审批任务、挂起恢复与审计链；控制面鉴权升级到 OAuth 2.1，共享密钥降级为开发模式。

## 0.1 兼容性硬约束

- 未提供主体时一切照旧：`RunPrincipal.anonymous()` 默认，现有 456 测试全绿、语义零变化；
- 新 API `@Experimental(since="0.2.0")`；japicmp 通过；
- **框架不内置 IAM**：主体的认证归行内身份系统，框架只负责接收、传播、评估挂点与留痕（与"业务规则归业务系统"同一哲学）；
- claims 域冻结；交付须含 commit 哈希（DoD #10 惯例条款）。

---

## 1. 范围

### 1.1 In scope
- **STD1-A 主体贯穿（core）**：`RunPrincipal` 模型 → 正门/执行上下文/权限策略/审批任务/挂起快照/审计链全链路传播；内置可配的角色门禁策略；
- **STD1-B 控制面 OAuth 2.1（starter）**：control-plane / console / studio / 回调端点支持 OAuth2 Resource Server（client credentials JWT），`shared-secret` 降级为显式的开发模式；
- 扩展点手册新增「身份与权限」一章（含 Spring Security 桥接示例）。

### 1.2 Out of scope
- 框架内建用户/角色管理（永不——IAM 归行内系统）；
- proof-of-possession / token 实时吊销 / SPIFFE（NIST 标准成熟后再议）；
- 细粒度数据权限（行级/字段级——属业务服务裁决权）；
- MCP/A2A 身份互通（MS3 议题）。

---

## 2. STD1-A 主体贯穿

### 2.1 模型（core，新包 `com.actiongraph.identity`，全部 `@Experimental`）
```java
public record RunPrincipal(
        String subject,                 // 操作者标识（工号/服务账号），不可为空
        String clientId,                // 经由哪个系统/渠道发起（可空 → ""）
        List<String> delegationChain,   // 委托链：user → app → agent（可空 → List.of()）
        Map<String, String> attributes  // 行内扩展（部门/租户等），过脱敏管道后入审计
) {
    public static RunPrincipal anonymous();      // subject = "anonymous"
    public static RunPrincipal of(String subject);
}
```

### 2.2 裁决 A1：core 显式传参，starter 负责便利
- 正门 additive 重载：`start(goalType, params, principal)` / `chat(input, known, principal)` / `resume(runId, principal)`；无主体重载委托 `anonymous()`；
- **不引入 ThreadLocal 上下文**（隐式传播是审计的敌人）；Spring 集成靠 starter 的 `RunPrincipalResolver` Bean（默认实现从 Spring SecurityContext 取认证主体，无 Spring Security 时返回 anonymous）——正门 Bean 在未显式传参时调用 resolver。显式参数永远优先。

### 2.3 裁决 A2：主体是运行的属性，跨挂起/恢复/崩溃不变
- `SuspendedRun` + `principal` 字段（兼容构造器，旧快照恢复为 anonymous）；
- **恢复后的执行继续以"原发起主体"为准**——审批通过不改变"这事是谁发起的"；
- 恢复动作的执行者单独留痕：resume/deliver/recover 在 trace 事件 data 中记 `actedBy`（审批人/投递方/`system:recoverer`），与 `principal` 两列并存——审计同时回答"谁发起"与"谁推进"。

### 2.4 裁决 A3：权限挂点 additive，评估时机不变
```java
// PermissionPolicy additive default（japicmp 安全）：
default boolean canExecute(Action action, Blackboard blackboard, RunPrincipal principal) {
    return canExecute(action, blackboard);   // 旧实现自动兼容
}
```
- executor 每步调用三参版（动态重评共识保持）；
- `ExecutionContext` + `default RunPrincipal principal()`（Action 可读，如需把操作者传给业务服务）；
- `HumanReviewRequest`/`HumanReviewTask` + `requestedBy`（审批人看得见"谁在申请"）。

### 2.5 内置角色门禁（可选启用，模式同 AmountLimitPolicy）
```yaml
actiongraph:
  security:
    action-roles:
      - action-id: "transfer.approval.request"
        any-of: [teller-supervisor, ops-admin]   # principal.attributes["roles"] 命中其一
```
命中规则缺角色 → DENY（走既有 DENIED_BY_POLICY + 补偿）。**只做 any-of 一条规则**——复杂授权写自定义 `PermissionPolicy` 对接行内 IAM（手册示例）。

### 2.6 审计
- `RUN_STARTED` data + `principal.subject` / `clientId` / `delegationChain`（attributes 过脱敏管道）；
- 主体字段参与既有哈希链（落库形态哈希，裁决 B of F0 延续）。

---

## 3. STD1-B 控制面 OAuth 2.1

### 3.1 裁决 B1：模式开关，密钥降级而非删除
```yaml
actiongraph:
  security:
    endpoints:
      mode: oauth2            # oauth2 | shared-secret(默认，启动时 WARN"仅限开发环境")
      oauth2:
        issuer-uri: https://idp.bank.internal/realms/agents
        required-scopes:
          console: actiongraph.console.read
          studio: actiongraph.studio.author
          human-review-callback: actiongraph.review.decide
          events-callback: actiongraph.events.deliver
          runtime-api: actiongraph.run
```
- `oauth2` 模式：标准 Resource Server JWT 校验（`spring-boot-starter-oauth2-resource-server` 作 **optional 依赖**，未引入而开 oauth2 → 启动失败并提示加依赖）；按端点组校验 scope；
- 既有各端点的 `shared-secret`/`token-header` 配置保留原语义（向后兼容），但 mode 缺省时启动日志 WARN。

### 3.2 裁决 B2：JWT 主体直通 RunPrincipal
runtime-api 经 oauth2 进来的请求：`sub` → `RunPrincipal.subject`，`azp/client_id` → `clientId`，可配 claim 映射 roles → `attributes["roles"]`（`actiongraph.security.oauth2.roles-claim`，默认 `roles`）。机器对机器调用因此天然获得"代表谁"语义——委托链场景（用户→渠道→agent）取 `act`/自定义 claim 进 `delegationChain`（可配，缺省不取）。

---

## 4. 验收标准（Definition of Done）

1. **零影响回归**：不传主体、不开任何新配置 → 456 测试全绿，行为零变化（anonymous 贯穿但不改变任何决策）。
2. **主体端到端**：带 principal 的 `start` → `RUN_STARTED` 含 subject/clientId/委托链 → 审批任务 `requestedBy` 可见 → 挂起后恢复，**原主体不变** + resume 事件 `actedBy` 为恢复方 → 哈希链含主体字段且验证通过。
3. **崩溃恢复主体保持**：MS1 恢复路径上 principal 从快照还原，`RUN_RECOVERED` 的 `actedBy=system:recoverer`。
4. **权限挂点**：自定义三参 `PermissionPolicy` 按 principal 拒绝 → `DENIED_BY_POLICY`；旧的两参实现零修改继续工作（兼容性测试）。
5. **角色门禁**：yml 规则命中缺角色 → DENY；具备角色 → 放行；未配置该 action → 不评估。
6. **OAuth2**：合法 JWT（mock issuer / spring-security-test）→ 200 且 RunPrincipal 映射正确（sub/client/roles claim）；scope 不符 → 403；无效签名 → 401；`shared-secret` 模式回归不破 + 启动 WARN 断言。
7. **缺依赖 fail-fast**：mode=oauth2 而 classpath 无 resource-server → 启动失败含补救提示。
8. **附带义务**：手册「身份与权限」章含 SecurityContext 桥接与自定义 PermissionPolicy 对接 IAM 示例；samples 任一域补一个最小角色门禁演示（claims 不动）。
9. **规范**：新 API `@Experimental`；japicmp、文档守卫、`./gradlew build --rerun-tasks` 全绿。
10. **交付完成定义**：已 commit + push，交付说明含哈希（dhk-integration.md「Codex 交付模板」全项执行）。

## 5. 实施顺序

A（模型与正门重载）→ A2.4（策略/上下文/审批挂点）→ A2.2/2.3（快照与恢复贯穿）→ A2.5/2.6（角色门禁与审计）→ **A 段验收** → B（OAuth2 模式与映射）→ B 段验收。

## 6. 开放问题（有默认值，不阻塞）

| # | 问题 | 默认 |
|---|---|---|
| 1 | principal.subject 是否参与脱敏 | 不脱敏（工号是审计要件非客户 PII）；attributes 过脱敏管道 |
| 2 | token 吊销/内省（opaque token） | 不做，JWT 校验够 v1；行内网关吊销兜底 |
| 3 | 委托链格式标准化 | v1 自由字符串列表；NIST/OAuth 链式规范落地后对齐 |
| 4 | studio 的 approve 是否强制非匿名 | 是——studio 端点在 oauth2 模式下 approver 取 JWT sub，shared-secret 模式仍要求显式传 approver |
