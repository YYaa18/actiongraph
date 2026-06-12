# ActionGraph × DevHarnessKit 生态联动方案

> 状态：方案定稿，第一步可立即执行
> 两个项目：ActionGraph（运行态业务 Agent 底座，本仓库）；DevHarnessKit（开发态 AI 编程协作工具，`~/Desktop/DevHarnessKit`，v1.3.0）
> 总原则：**联动走 artifact 与流程，不走代码依赖**。

---

## 1. 关系定位：同一哲学的两端镜像

| 维度 | DevHarnessKit（DHK） | ActionGraph（AG） |
|---|---|---|
| 管的对象 | 写代码的 AI（Claude Code / Codex） | 办业务的 AI（生产 agent） |
| 生命周期 | 开发态 | 运行态 |
| 核心机制 | Goal + Work Brief + 证据验收 + BDD | Goal + GOAP 规划 + 审批 + Saga 补偿 |
| 审计形态 | SQLite + 证据 artifact | Trace 哈希链 + JDBC 留痕 |
| 共同价值观 | 不信任声明，只信任证据 | 不信任模型，只信任白名单与留痕 |

对外统一叙事（金融客户话术）：

> **开发态，DHK 让写代码的 AI 拿出证据；运行态，ActionGraph 让办业务的 AI 接受审批。AI 进入企业的每一步，都有缰绳。**

## 2. 三层联动

### L1 流程联动（零代码，立即执行）
用 DHK 管理 ActionGraph 自身的迭代：PRD 验收标准录为 DHK BDD scenario；实现方（Codex）每刀交付必须附 JUnit XML 证据关闭 Goal；验收人核 DHK 证据记录而非交付说明。
直接解决两个已发生的问题：交付声明与实际不符（曾声称"未改核心"但实改）；战略文档被进度倒写。

### L2 产品联动（金融 GTM 资产）：Action 接入流水线
AG 的运行时安全依赖接入方的开发纪律（补偿、脱敏、风险标注），而开发纪律恰是 DHK 的管辖范围。产品化为 DHK Goal 模板 `ag-action-onboarding`（命名刻意不带 `actiongraph-` 前缀：那是 Maven 模块命名空间，文档一致性守卫会校验该前缀必须对应真实模块）：

每新增一个 `@AgentAction` 必须走完证据清单，证据不齐 Goal 不可关闭：
1. 补偿测试通过（JUnit XML 证据）；
2. runtimeGuard 拒绝路径有测试；
3. 脱敏黑名单覆盖本域敏感字段（评审证据）;
4. 风险等级与人审标注经复核（人工证据）；
5. BDD scenario 绑定该 Action 的业务验收意图。

对外话术从"运行时很安全"升级为"**交付一条带证据门禁的 Action 接入流水线**"。

### L3 叙事联动
白皮书、演示文稿、行业会议统一使用 §1 的双缰绳叙事；两产品互相引流。

## 3. 技术接口点（全部 artifact 级）

| 接口 | 方向 | 形式 | 开发量 |
|---|---|---|---|
| 验收测试证据 | AG → DHK | JUnit XML（`dhk bdd evidence junit --reports <dir>`） | 零 |
| 运行审计证据 | AG → DHK | Trace 导出 + `TraceChainVerifier` 结果作为证据文件（`dhk bdd evidence add`） | 零（已有导出） |
| 场景回归 | AG → DHK testbeds | 理赔预审样本回放装入 DHK testbed/scorecard 格式，**替代自建豪华报告**（消化 F1 已镀金功能） | 小 |
| 接入流水线模板 | DHK → AG 接入方 | DHK goal/skill 模板（DHK 原生扩展点，见其 SKILL_CONTRACT.md） | 中（L2 阶段） |

## 4. 反目标（吸取 42 模块教训）

- ❌ 不合并代码、不建共享 core（Maven CLI+SQLite vs Gradle 库+Spring，发布节奏与稳定承诺不同）；
- ❌ 不新建第三个"集成平台"项目/模块——联动只走文件、报告、模板；
- ❌ 不统一两边 memory 模型（开发记忆 vs 业务运行记忆，形似神不似）。

## 5. 第一步落地（一天内，已含可执行草稿）

在 ActionGraph 仓库根目录初始化 DHK，并把"下一刀"（模块收敛 + F1 纠偏）的验收标准录为 scenario：

```bash
# 初始化（在 actiongraph 仓库根）
dhk bdd init --project-root .

# Feature：模块收敛
dhk bdd add --feature module-consolidation --title "模块收敛到 10 个以内" \
  --scenario settings-module-count --scenario-title "settings.gradle.kts 模块数 <= 10"
dhk bdd add --feature module-consolidation --title "模块收敛到 10 个以内" \
  --scenario regression-green --scenario-title "收敛后全量测试零失败"

# Feature：F0 金融化回归（取自 docs/PRD-F0-finance.md §7）
dhk bdd add --feature f0-finance --title "F0 内核金融化验收" \
  --scenario masking-no-plaintext --scenario-title "trace 与审批预览无敏感明文，挂起快照无损恢复"
dhk bdd add --feature f0-finance --title "F0 内核金融化验收" \
  --scenario tamper-detect --scenario-title "篡改任一行后 TraceChainVerifier 指出 firstBrokenSeq"
dhk bdd add --feature f0-finance --title "F0 内核金融化验收" \
  --scenario multistage-concurrent-decide --scenario-title "同级并发 decide 第二次抛 StageAlreadyDecidedException"
dhk bdd add --feature f0-finance --title "F0 内核金融化验收" \
  --scenario amount-limits --scenario-title "超硬限额 DENY 未执行；介于阈值审批链+1 级"

# 每刀交付后，证据入库（Gradle 测试报告目录）
dhk bdd evidence junit --reports actiongraph-core/build/test-results/test
dhk bdd verify --feature f0-finance
dhk bdd verify --feature module-consolidation
```

验收人（Claude）的检查方式相应变更：先 `dhk bdd verify` 看证据状态，再抽查代码——**证据缺失或失败的交付直接打回，不进入人工验收**。

## Codex 交付模板

每一刀交付必须把“完成”定义为代码已经进入远端仓库，而不是只停在本地工作区。交付说明固定包含：

1. 本地工作目录路径；
2. 实跑命令与结果，例如 `./gradlew build --rerun-tasks`、demo 命令、DHK 证据命令；
3. 变更范围摘要；
4. `git status --short` 说明，明确哪些未跟踪文件不属于本刀；
5. 已执行 `git commit` 与 `git push`；
6. 本刀 commit 哈希。

验收第一步先核 `git log --oneline -3`、关键符号 grep 与远端同步状态。没有 commit 哈希的交付视为未完成；没有 push 的交付不能进入放行结论。

## 6. 阶段对齐

- 本方案 L1 立即执行，不占 F1 排期；
- L2 模板在 F1 真实场景跑通后启动（流水线内容需要真实接入方的字段反馈，避免再次沙箱镀金）；
- L3 与白皮书（finance-strategy §5）同节奏。
