# PRD — F0 内核金融化（脱敏 / 审计防篡改 / 多级审批 / 额度策略）

> 交付对象：实现方（Codex）
> 前置版本：F0-1 完成后的 main（94 tests，5 模块，包根 `com.actiongraph`）
> 战略依据：docs/finance-strategy.md
> 状态：可直接实施

---

## 0. 一句话目标

在**不破坏现有四份核心合同语义**（Action / Policy / Suspension / Trace）的前提下，补齐金融机构准入所需的四项能力：

> 敏感数据脱敏 ＋ 审计链防篡改 ＋ 多级审批链 ＋ 单笔额度策略。

四项全部为 **additive 扩展**：现有测试必须全部保持绿色（允许因新增字段微调断言，不允许语义改变）。

---

## 1. 范围

### 1.1 In scope
- F0-1 DataMaskingPolicy：trace 与审批预览的敏感数据脱敏（含默认正则实现）。
- F0-2 审计链哈希：TraceEvent 链式 SHA-256 + 验证器 + JDBC 列。
- F0-3 多级审批链：经办→复核→授权式的逐级审批，复用现有挂起/恢复，executor 零改动。
- F0-4 单笔额度策略：金额提取 + 硬限额拒绝 + 超阈值升级审批路由。
- Spring Boot starter 对以上全部的自动装配与配置项。
- 每项的回归测试 + 本 PRD §7 的验收测试。

### 1.2 Out of scope（明确不做，防止范围爬行）
- 日累计/月累计额度（F1；本期只做单笔）。
- 审批 UI / console（F2）。
- 信创数据库适配验证（F1）。
- 监管映射文档（与本期并行的文档任务，不在代码交付内）。
- 多币种汇率换算（限额按币种独立配置，币种不匹配按保守拒绝）。

---

## 2. 核心裁决（消解歧义，必须遵守）

### 裁决 A：脱敏只作用于"人类可读面"，挂起快照不脱敏
脱敏点有且仅有两个咽喉：
1. `RunTrace.append(...)`——所有 trace 事件的 `detail` 与 `data` 值（含批量 flush 前的缓冲内容）；
2. `GoapExecutor.objectPreview(...)` 产出的审批预览（进入 `HumanReviewRequest.blackboardPreview` 与 `HumanReviewTask`）。

**`SuspendedRun` 的 Blackboard 序列化快照不脱敏**——它必须可无损往返恢复。该表的访问控制要求写入 jdbc-persistence 文档（仅 runtime 服务账号可读）。

### 裁决 B：哈希在脱敏之后计算
审计链哈希对**落库形态**（已脱敏）计算。否则验证者拿存储数据永远算不出匹配哈希。实现顺序因此固定：F0-1 先于 F0-2。

### 裁决 C：多级审批完全收在 Policy 层，executor 与挂起/恢复零改动
对 executor 而言世界不变：`review(request)` 返回 PENDING 就挂起、APPROVED 就继续、DENIED 就补偿。多级状态机是 `RepositoryBackedHumanReviewPolicy` 与 `HumanReviewTask` 的内部事务：未走完审批链 → 永远返回 PENDING。这保证 v2 验收过的挂起/原子认领/恢复语义原样复用。

### 裁决 D：PolicyDecision 枚举不加值
"超额升级审批"不引入新枚举。表达方式：
- 超**硬限额** → `DENY`（走既有 DENIED_BY_POLICY + 补偿路径）；
- 超**审批阈值** → `REQUIRES_HUMAN_REVIEW`（走既有挂起路径），并通过新增的 `HumanReviewRequest.attributes` 把金额信息传给审批链路由器。

### 裁决 E：核心 record 的 additive 变更清单（仅限以下四处，不得再多）
1. `TraceEvent` + `String prevHash` + `String hash`（带默认空值的兼容构造器保留）；
2. `HumanReviewRequest` + `Map<String,String> attributes`（默认空 map）;
3. `HumanReviewTask` + 审批链字段（见 §5）；
4. `GoapExecutor` 新增 Builder（构造参数已达 6 个，本期再加 2 个注入点；旧构造器全部保留并委托 Builder 默认值）。

---

## 3. F0-1 敏感数据脱敏

### 3.1 接口（core，`com.actiongraph.policy` 包）
```java
public interface DataMaskingPolicy {
    /** 对单个可读文本脱敏（trace detail、审批 message 等）。 */
    String maskText(String text);

    /** 对 key-value 数据脱敏（trace data、blackboardPreview）。可按 key 决定强度。 */
    Map<String, String> maskData(Map<String, String> data);
}
```

### 3.2 实现
- `NoopMaskingPolicy`（core 默认，保证非金融用户零行为变化）。
- `RegexMaskingPolicy`（core 提供，金融默认），规则与保留格式：

| 类型 | 匹配 | 脱敏后示例 |
|---|---|---|
| 身份证（18 位，含 X 结尾） | `\b\d{17}[\dXx]\b` | `110101********1234` |
| 银行卡（13–19 位连续数字） | `\b\d{13,19}\b` | `622848******1234`（留前 6 后 4） |
| 手机号 | `\b1[3-9]\d{9}\b` | `138****5678` |
| 邮箱 | 常规邮箱正则 | `z***@bank.com` |

- 另支持**字段名黑名单**：构造时传入 key 集合（如 `idCard`、`cardNo`、`customerName`），命中 key 的 value 整体替换为 `***`，不依赖值的形状。
- 规则可叠加自定义：`RegexMaskingPolicy.builder().addRule(pattern, replacer).addBlockedKey("policyNo")`。

### 3.3 注入点
- `GoapExecutor.Builder.maskingPolicy(DataMaskingPolicy)`，默认 Noop。
- `RunTrace.append` 内统一调用（这是唯一 trace 汇聚点，含 flush 缓冲）；
- `objectPreview(...)` 结果过 `maskData` 后再构造 `HumanReviewRequest`。
- starter 配置：`actiongraph.masking.enabled=true`（默认 false→Noop）、`actiongraph.masking.blocked-keys=...`。

---

## 4. F0-2 审计链防篡改

### 4.1 模型
`TraceEvent` 新增 `prevHash`、`hash`。规范化串与哈希：

```
canonical = runId | seq | at(ISO-8601) | type | actionId("" if null) | detail
          | join(sortedByKey(data), k "=" v, ";") | prevHash
hash      = hex(SHA-256(canonical))
```

- 链按 `runId` 隔离；每个 run 的首事件 `prevHash = ""`。
- **计算位置：`RunTrace.append`**（core 内，与持久层无关；in-memory 同样成链）。
- resume 时 `RunTrace` 初始化：现已读取 max seq，同步读取末事件 `hash` 作为链接点（一次查询取两值）。

### 4.2 验证器（core，`com.actiongraph.trace`）
```java
public final class TraceChainVerifier {
    public record ChainVerification(boolean valid, long firstBrokenSeq, String message) {}
    /** events 必须按 seq 升序传入；验证逐事件重算哈希并核对链接。 */
    public ChainVerification verify(List<TraceEvent> events);
}
```

### 4.3 持久化
- JDBC trace 表加 `prev_hash varchar(64)`、`hash varchar(64)` 两列（`create table if not exists` 同步更新；对已存在的旧表执行 `alter table ... add column` 的兼容迁移，旧行两列为空、验证器对空哈希行报 `valid=false` 并指明 seq）。
- `PersistenceJsonCodec` 不参与哈希（哈希在 core 已算好，持久层只存取）。

---

## 5. F0-3 多级审批链

### 5.1 模型（core，`com.actiongraph.policy`）
```java
public record ApprovalStage(String name, String requiredRole) {}      // 如 ("复核", "checker")
public record ApprovalChain(List<ApprovalStage> stages) {
    public static ApprovalChain single() { /* 一级链，等价现行为 */ }
}
public interface ApprovalChainResolver {
    ApprovalChain resolve(HumanReviewRequest request);                 // 按 riskLevel/attributes 路由
}
```
默认实现 `RiskBasedChainResolver`：HIGH → 两级（经办复核+授权），其余 → 单级；若 `request.attributes` 含 `amountEscalated=true` → 在结果链上追加一级授权。

### 5.2 任务状态机（扩展 `HumanReviewTask`）
新增字段：
```java
List<ApprovalStage> stages,            // 本任务的审批链
int currentStageIndex,                 // 0-based，当前待审级
List<StageDecision> stageDecisions     // record StageDecision(String stage, HumanReviewDecision d,
                                       //        String reviewer, String comment, Instant at)
```
`RepositoryBackedHumanReviewPolicy.review(request)` 语义：
- 无任务 → resolve 链、建任务（currentStageIndex=0）→ 返回 PENDING；
- 有任务且 `currentStageIndex < stages.size()` → PENDING；
- 全级通过 → APPROVED（reviewer 取末级审批人，message 聚合各级意见）；
- 任一级 DENIED → DENIED。

`HumanReviewRepository.decide(runId, actionId, decision, reviewer, message)` 语义升级：
- 作用于**当前级**；APPROVED 推进 `currentStageIndex++`，DENIED 终结任务；
- **并发防护**：JDBC 用 `update ... set current_stage_index = ? where run_id=? and action_id=? and current_stage_index=?` 原子推进，同一级第二个 decide 必须失败（抛 `StageAlreadyDecidedException`）；InMemory 用 CAS 等价实现；
- 每级决定追加入 `stageDecisions`，全量留痕。

### 5.3 兼容性要求
- 默认 resolver 对现有场景产出单级链 → 现有全部审批测试不改语义直接通过。
- JDBC 任务表加 `stages_json`、`stage_decisions_json`、`current_stage_index` 列（同 §4.3 的兼容迁移策略）。

---

## 6. F0-4 单笔额度策略

### 6.1 金额从哪来（框架不猜业务语义）
```java
// core, com.actiongraph.policy
public record MonetaryAmount(BigDecimal value, String currency) {}
public interface AmountExtractor {
    /** 返回 empty 表示该 action 与金额无关。由接入域实现（如从 QuoteDraft 取保费）。 */
    Optional<MonetaryAmount> extract(Action action, Blackboard blackboard);
}
```

### 6.2 策略
```java
public final class AmountLimitPolicy implements PermissionPolicy {
    // 配置：per actionId（或 "*"）+ per currency：
    //   hardLimit    —— 超过 → canExecute=false（DENY，走补偿）
    //   reviewLimit  —— 超过 → 不拒绝，但标记升级（见 6.3）
    // 币种无配置时：有金额但币种未知 → 按 DENY（保守）。
}
```
`DefaultPolicyGuard` 构造支持传入多个 `PermissionPolicy` 按序评估（现有单 policy 构造器保留）。

### 6.3 升级审批的传递（衔接 F0-3，遵守裁决 D）
- `GoapExecutor.Builder.reviewAttributeContributor(ReviewAttributeContributor)`：
```java
public interface ReviewAttributeContributor {
    Map<String, String> contribute(Action action, Blackboard blackboard);
}
```
- 默认实现 `AmountAttributeContributor`（包装 AmountExtractor + reviewLimit 配置）：超 reviewLimit 时产出 `{amount, currency, amountEscalated:"true"}`；
- executor 构造 `HumanReviewRequest` 时合并 attributes；`RiskBasedChainResolver` 据此追加授权级。
- 注意：attributes 同样过 `maskData`（金额本身通常不脱敏，但 contributor 可能带出账号类字段）。

### 6.4 starter 配置
```yaml
actiongraph:
  limits:
    rules:
      - action-id: "quote.draft.create"     # 或 "*"
        currency: CNY
        hard-limit: 1000000
        review-limit: 100000
```

---

## 7. 验收标准（Definition of Done）

1. **回归**：现有测试全绿（断言可因新增字段微调，语义不得变）。
2. **脱敏**：构造含身份证/卡号/手机号的 Blackboard 跑完整链 → JDBC trace 落库值与 `HumanReviewTask.blackboardPreview` 中**检索不到任何明文敏感串**；`SuspendedRun` 快照恢复后对象与原值完全一致（证明快照未被脱敏污染）。
3. **防篡改**：完整运行后 `TraceChainVerifier.verify` 通过；手工 UPDATE 任一行 detail 后验证失败且 `firstBrokenSeq` 指向被改行；resume 跨越后链仍连续可验证。
4. **多级审批**：HIGH 风险动作 → 任务两级；第一级 approve 后 run resume 仍 `SUSPENDED_PENDING_REVIEW`；第二级 approve 后 resume → `COMPLETED`；任一级 deny → `DENIED_BY_POLICY` 且补偿执行；同一级并发两次 decide，第二次抛 `StageAlreadyDecidedException`；`stageDecisions` 完整记录各级审批人。
5. **额度**：超 hardLimit → `DENIED_BY_POLICY` 且该 action 未执行；介于 reviewLimit 与 hardLimit → 审批链多一级且 attributes 含金额；低于 reviewLimit → 行为与现状完全一致；无 AmountExtractor 配置的域 → 一切照旧。
6. **starter**：四项均可经 application.yml 启停，全部默认关闭（开箱行为与 F0 之前一致）。
7. `./gradlew build --rerun-tasks` 全绿；新增测试覆盖 §7.2–7.5 的每个分支。

## 8. 实施顺序（依赖关系固定）

F0-1 脱敏 →（裁决 B）→ F0-2 哈希链 → F0-3 多级审批 → F0-4 额度（依赖 F0-3 的 attributes 通道）。

## 9. 开放问题（有默认值，不阻塞）

| # | 问题 | 默认 |
|---|---|---|
| 1 | 哈希算法是否需要国密 SM3 | F0 用 SHA-256，`HashFunction` 留接口，SM3 进信创适配（F1） |
| 2 | stageDecisions 的 comment 是否脱敏 | 是，过 maskText |
| 3 | 旧 trace 表无哈希列的历史行 | 验证器报不通过并注明"pre-F0 数据"，不做回填 |
| 4 | 审批角色与行内权限系统对接 | F0 只存 requiredRole 字符串，校验留给 decide 调用方（行内审批系统） |
