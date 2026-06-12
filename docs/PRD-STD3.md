# PRD — STD3 解释评测（金句集 CI 门禁 / 生产解释质量采样 / 评测飞轮）

> 交付对象：实现方（Codex）
> 前置版本：0.2.0-SNAPSHOT main（4f71877，476 tests）
> 动机：行业共识（2026）把"评测作为可观测性的一部分"列为基建资格项，而我们对 LLM 解释质量零度量。架构反转优势：**解释面极窄且输出结构化（goalType + 参数）——评测可以做成精确断言，不需要 LLM-as-judge 的模糊评分**。开放式 agent 做不到的"可证明的解释质量"，我们能做，这要变成卖点。
> 附带义务：本刀第一件事偿还 STD1 债①（崩溃恢复主体保持断言）。
> 状态：可直接实施

---

## 0. 一句话目标

每个接入域用一份"金句集"在 CI 里为解释质量设门禁（确定性精确匹配）；生产侧以脱敏采样 + 标准遥测度量真实解释质量；被标注的生产样本回流金句集——评测飞轮成形。

## 0.0 先还债（不计入本期范围，但是 DoD 第 0 条）

`GoapExecutorDurabilityTest` 补断言：崩溃恢复后 trace 中 principal == 原发起主体；`RUN_RECOVERED` 事件 `actedBy = system:recoverer`。约十行，实现已在（STD1 验收确认），只缺测试钉住。

## 0.1 兼容性硬约束

- 评测能力完全 opt-in：不配置金句集、不开采样 → 行为零变化，现有 476 测试全绿；
- 新 API `@Experimental(since="0.2.0")`；japicmp 通过；claims 域冻结；
- 交付完成定义：commit + push + 哈希（交付模板全项，**引用验收方清单时逐字对照**）。

---

## 1. 范围

### 1.1 In scope
- **STD3-A 金句集评测**：格式、执行器、JUnit 断言助手、阈值门禁、差异报告产物；renewal 示例集；
- **STD3-B 生产解释质量**：解释器装饰器（指标 + 采样）、脱敏采样存储（InMemory + JDBC）、ObservationSink 事件（接 STD2 的 Micrometer/OTel 双轨）；
- **STD3-C 飞轮文档**：金句集归属、生产样本标注回流流程（操作手册一章）。

### 1.2 Out of scope
- LLM-as-judge / 小模型评审（结构化精确匹配是本框架的优势，模糊评分违背它；开放问题 #1）；
- 多轮对话评测（`chat(input, knownParameters)` 的轮次序列——v1 只评单轮）；
- console 解释质量视图（采样库就绪后 console 演进再做）；
- 非确定性多次采样统计（v1 单次评测；N 次稳定性留开放问题）。

---

## 2. 核心裁决

### 裁决 A：评测 = 精确断言，真值来自人
解释输出是结构化的（goalType / 参数 / 是否澄清 / 缺哪些字段），金句集逐字段精确匹配，**不引入任何模糊评分**。真值只能由人提供（写金句集、标注生产样本）——框架提供格式、执行器、报告与回流通道，不替人判断对错。

### 裁决 B：金句集归域，框架给格式和执行器
- 格式 JSONL（一行一例），文件随域代码入库（接入方仓库）；框架在 samples/renewal 提供示例集（≥15 例）作为模板；
- 评测组件落 **llm 模块**（Jackson 已在该模块；core 保持零 JSON 依赖）。

```jsonl
{"input": "为客户 C001 预约 80 万元的大额转账", "expect": {"goalType": "reserveLargeTransfer", "parameters": {"customerId": "C001", "amount": "800000"}}}
{"input": "帮客户办个转账", "expect": {"clarification": true, "missingFields": ["customerId", "amount"]}}
{"input": "查一下今天的天气", "expect": {"unknownGoal": true}}
```

### 裁决 C：两种解释器，两种 CI 定位
- **规则解释器**：确定性 → 金句集是**硬门禁**（普通 JUnit 测试，失败即红）；
- **LLM 解释器**：env-gated（沿用 DeepSeekRealLlmSmokeTest 的凭据门控模式），**阈值制**（如 goalType 准确率 ≥0.9、参数全对率 ≥0.85，阈值写在评测调用处），无凭据环境自动 skip 不阻塞。
两者用同一份金句集、同一个执行器——解释器实现可替换而评测基准不变，这正是评测的意义。

### 裁决 D：分数不进 trace——指标聚合 + 采样明细两轨
trace 是**运行审计**，评测是**质量工程**，混在一起两边都脏（且解释发生在 run 存在之前——澄清根本没有 runId）。落点：
- 聚合：ObservationSink 事件（→ Micrometer 计数器 + OTel `gen_ai.operation.name=agent.interpretation` span）；
- 明细：独立采样库；样本若导致运行启动则记录 runId 字段（可 join 审计，不污染审计）。

### 裁决 E：生产采样默认关，入库必脱敏
- `actiongraph.interpretation.sampling.rate = 0.0`（默认关；0~1 采样率）；
- 样本的 input 文本**入库前过 `DataMaskingPolicy`**（客户口述里有卡号手机号是常态）；DoD 含无明文断言（沿用 F0 验收手法）；
- 存储复用既有 JDBC 形态：新表 `agent_interpretation_sample`（id/at/masked_input/outcome/goal_type/missing_fields/fallback_used/parse_failure/run_id/labeled 标注位），InMemory 对应实现。

### 裁决 F：度量点用装饰器，不侵入解释器实现
`MeasuredGoalInterpreter implements GoalInterpreter` 包裹任意实现（含 Fallback 链整体），记录：结果形态（ready/clarification/unknown）、耗时、`fallback_used`（主解释器抛 `LlmClientException` 降级）、`parse_failure`（`StructuredOutputException` 兜底）。starter 在指标或采样任一开启时自动包裹正门与 RuntimeApiService 所用的解释器 Bean；全关时不包裹（零开销）。

---

## 3. 接口（additive 清单）

```java
// llm 模块，com.actiongraph.llm.evals，全部 @Experimental
public record GoldenCase(String input, Expectation expect) { ... }
public final class GoldenSetEvalRunner {
    public EvalReport evaluate(GoalInterpreter interpreter, Path goldenSet);
}
public record EvalReport(int total, int goalTypeCorrect, int parametersCorrect,
                         int clarificationCorrect, List<CaseDiff> failures) {
    public double goalTypeAccuracy(); ...
    public String toMarkdown();            // CI 产物 + DHK 证据可直接引用
}
// JUnit 助手：
GoldenSetAssertions.assertMeets(interpreter, path,
        Thresholds.exact());                       // 规则解释器：全对
GoldenSetAssertions.assertMeets(interpreter, path,
        Thresholds.of(0.9, 0.85));                 // LLM：阈值制
```

```java
// core 不动；starter/llm 侧：
public final class MeasuredGoalInterpreter implements GoalInterpreter { ... }   // 裁决 F
public interface InterpretationSampleRepository { save/findRecent/markLabeled }  // + InMemory/Jdbc 实现
```

starter 配置：
```yaml
actiongraph:
  interpretation:
    metrics: true            # 默认 false；开启即包裹装饰器发 ObservationSink 事件
    sampling:
      rate: 0.05             # 默认 0.0
```

## 4. 验收标准（Definition of Done）

0. **债①**：DurabilityTest 含恢复主体保持与 `actedBy=system:recoverer` 断言。
1. **金句集门禁**：renewal 示例集 ≥15 例（ready/澄清/未知目标/参数变体如"80 万"），规则解释器全对通过；故意改坏一例 → 测试红且 `CaseDiff` 列出 input/期望/实际三元组。
2. **报告产物**：`EvalReport.toMarkdown()` 写入 build 目录（路径稳定，CI/DHK 可采）。
3. **LLM 门控评测**：env-gated 测试用同一金句集 + 阈值制跑 DeepSeek；无凭据 skip（计入 skipped 不计失败）。
4. **指标**：开 metrics 后，ready/澄清/unknown 三形态计数、`fallback_used`、`parse_failure` 经 ObservationSink 可见（Micrometer 断言 + OTel sink 属性断言，`gen_ai.operation.name=agent.interpretation`）；全关时解释器 Bean 未被包裹（零开销断言）。
5. **采样**：rate=1.0 时样本入库且 input 已脱敏（构造含身份证/手机号的输入，断言库内无明文）；rate=0 零记录；导致运行的样本带 runId；JDBC 实现 H2 往返测试。
6. **降级度量**：模拟主解释器抛 `LlmClientException` → 样本与指标记 `fallback_used=true`；畸形 LLM 输出 → `parse_failure=true`。
7. **飞轮文档**：手册一章——金句集归属、阈值选择指引、生产样本标注→回流金句集的操作循环；DHK 证据采集命令示例。
8. **规范**：476 既有测试全绿；新 API `@Experimental`；japicmp、文档守卫、`./gradlew build --rerun-tasks` 全绿；交付含 commit 哈希。

## 5. 实施顺序

债①（十行，先还）→ A（格式/执行器/示例集/门禁）→ F（装饰器与指标）→ E（采样存储与脱敏）→ C 文档 → LLM 门控评测收尾。

## 6. 开放问题（有默认值，不阻塞）

| # | 问题 | 默认 |
|---|---|---|
| 1 | LLM-as-judge / 小模型评审 | 不做——结构化精确匹配是本框架的差异化，模糊评分是开放式 agent 的无奈之举 |
| 2 | LLM 评测的 N 次稳定性统计 | v1 单次；试点出现抖动证据再加 repeat 参数 |
| 3 | 多轮（带 knownParameters）评测 | v1 单轮；格式预留 `known` 字段不实现 |
| 4 | console 解释质量视图 | 采样库 schema 即其 read model 基础，console 演进时做 |
| 5 | 样本标注工具 | v1 SQL/手工 + markLabeled API；不做 UI |
