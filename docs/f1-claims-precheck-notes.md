# F1 Claims Precheck Scenario Notes

F1 的第一刀不是再扩 runtime，而是用一个新的保险场景验证 F0 金融化内核能不能承载真实业务语义。

## Scenario

**理赔资料预审 + 赔付申请草稿**

链路：

1. `claim.lookup` 查询理赔案
2. `claim.documents.query` 查询资料包
3. `claim.precheck.evaluate` 校验资料完整性
4. `claim.payout.draft.create` 生成赔付申请草稿
5. `claim.approval.request` 发起人工审批

目标条件：`claims:CLAIM_APPROVAL_REQUESTED`

## Evidence

成功路径：

```bash
./gradlew :actiongraph-samples:runClaimsPrecheckSample \
  --args="--approve-human-review 帮我预审理赔 CLM100 并准备赔付申请"
```

实跑结果：

```text
Plan: claim.lookup -> claim.documents.query -> claim.precheck.evaluate -> claim.payout.draft.create -> claim.approval.request
status=COMPLETED
claimsPrecheckSummary status=COMPLETED, payoutDraft=CLAIM-DRAFT-1, approvalRequest=CLAIM-APPROVAL-1, missingDocuments=none
traceEvents=30
```

资料缺失拦截：

```bash
./gradlew :actiongraph-samples:runClaimsPrecheckSample \
  --args="--missing-invoice --approve-human-review Prepare payout application for claim CLM100"
```

实跑结果：

```text
status=HALTED_UNREACHABLE
executedActions=[claim.lookup, claim.documents.query, claim.precheck.evaluate]
claimsPrecheckSummary status=HALTED_UNREACHABLE, payoutDraft=none, approvalRequest=none, missingDocuments=invoice
traceEvents=21
```

## Covered Branches

- happy path: 完整资料 + 人审通过 -> `COMPLETED`
- default review: 高风险审批动作前挂起 -> `SUSPENDED_PENDING_REVIEW`
- business intercept: 发票缺失 -> runtime guard 拦截，草稿未创建
- denied review: 审批拒绝 -> 赔付草稿补偿作废
- service failure: 审批服务异常 -> 已执行草稿补偿作废
- amount escalation: 26 万 CNY 理赔金额超过 review limit -> 审批链追加 `amount-authorization`

## Batch Metrics

第三刀已把样板域扩成可读取样本文件、可交付报告的准真实批量指标；第四刀补上 JDBC 输入；第五刀补上 suspend/resume 审批等待统计；第六刀支持从外部审批决策文件驱动 resume，方便接近真实数据源和真实审批链路：

```bash
./gradlew :actiongraph-samples:runClaimsPrecheckBatchMetrics \
  --args="--input actiongraph-samples/src/main/resources/claims-precheck-cases.csv --report-dir actiongraph-samples/build/reports/claims-precheck --batch-id F1-CLAIMS-001 --environment local"

./gradlew :actiongraph-samples:runClaimsPrecheckBatchMetrics \
  --args="--input actiongraph-samples/src/main/resources/claims-precheck-cases.csv --report-dir actiongraph-samples/build/reports/claims-precheck --batch-id F1-CLAIMS-REVIEW-001 --environment local --review-mode suspend-resume --simulate-review-wait-ms 5"

./gradlew :actiongraph-samples:runClaimsPrecheckBatchMetrics \
  --args="--input actiongraph-samples/src/main/resources/claims-precheck-cases.csv --review-decisions actiongraph-samples/src/main/resources/claims-precheck-review-decisions.csv --report-dir actiongraph-samples/build/reports/claims-precheck --batch-id F1-CLAIMS-EXTERNAL-REVIEWS --environment local"

./gradlew :actiongraph-samples:runClaimsPrecheckBatchMetrics \
  --args='--jdbc-url jdbc:postgresql://db.example/claims --jdbc-user actiongraph_reader --report-dir actiongraph-samples/build/reports/claims-precheck --batch-id F1-CLAIMS-JDBC-001 --environment staging'
```

JDBC 默认查询 `claims_precheck_cases` 表，字段为 `claim_id`、`claimed_amount`、`missing_invoice`、`closed`、`approval_fails`、`expected_intercept`；也可以通过 `--jdbc-query` 传入自定义 SQL，只要结果列可映射到这些字段即可。`--jdbc-password` 可用于本地验证，报告里的 sample source 会对 URL 中的 password/pwd 参数脱敏。连接真实数据库时，需要把对应 JDBC 驱动加入样例运行 classpath。

`--review-mode suspend-resume` 会使用 `RepositoryBackedHumanReviewPolicy` 和 `SuspendedRunRepository` 跑真实的挂起/恢复路径；`--simulate-review-wait-ms` 用于在 demo 或压测中模拟审批系统回调延迟，`reviewWaitMs` 从 `HumanReviewTask.updatedAt` 时间线计算，接真实审批回调时可以复用同一口径。

`--review-decisions` 读取审批决策 CSV，字段为 `claimId`、`actionId`、`stageIndex`、`decision`、`reviewer`、`comment`、`decisionDelayMs`。运行时按 `claimId + actionId + stageIndex` 匹配 pending task，找不到匹配项会 fail-fast，避免审批样本静默漏配。

实跑结果摘要：

```text
claimsPrecheckBatch totalRuns=5, completed=1, intercepted=3, failed=1, auditComplete=5
interceptRate=60.00%, auditCompletenessRate=100.00%, averageRuntimeMs=14.683, averageBusinessActionMs=0.835, averageFrameworkMs=10.489, averageReviewWaitMs=3.359
case claimId=CLM100, status=COMPLETED, intercepted=false, auditComplete=true, businessActionMs=3.289, frameworkMs=40.224, reviewWaitMs=9.253
case claimId=CLM101, status=HALTED_UNREACHABLE, intercepted=true, auditComplete=true, businessActionMs=0.049, frameworkMs=3.196, reviewWaitMs=0.000
case claimId=CLM102, status=HALTED_UNREACHABLE, intercepted=true, auditComplete=true, businessActionMs=0.038, frameworkMs=3.185, reviewWaitMs=0.000
case claimId=CLM103, status=DENIED_BY_POLICY, intercepted=true, auditComplete=true, businessActionMs=0.712, frameworkMs=2.742, reviewWaitMs=0.000
case claimId=CLM104, status=FAILED_COMPENSATED, intercepted=false, auditComplete=true, businessActionMs=0.087, frameworkMs=3.100, reviewWaitMs=7.542
```

报告产物：

- `claims-precheck-report.md`：业务可读指标摘要与明细表
- `claims-precheck-results.csv`：每个样本的状态、是否拦截、审计完整性、trace 事件数、运行耗时与耗时拆分
- 报告头包含 batch id、environment、sample source、review mode、模拟审批等待、外部审批决策数量和当前限额参数；sample source 可以来自 CSV 路径或 JDBC URL

当前指标口径：

- 单均处理时长：批量运行 N 个理赔案，记录当前审批模式下的端到端运行耗时
- 业务 Action 耗时：样例 Action 的 `execute` / `compensate` 调用耗时，代表业务服务调用成本
- 框架调度耗时：端到端耗时扣除业务 Action 耗时和审批等待耗时后的剩余时间
- 审批等待耗时：auto-approve 模式下统计 human review policy 决策耗时；suspend-resume 模式下按 `HumanReviewTask` 的阶段更新时间线统计挂起到审批决定之间的等待
- 拦截率：资料缺失、已结案、超硬限额等被 guard/policy 拦截的比例
- 审计完整率：每个 run 的 TraceChainVerifier 通过率与缺失事件率

## F1 Next

下一刀应继续把报告推进到更贴近生产的数据资产：

- 接入真实数据库表或视图，并沉淀脱敏抽样脚本
- 把 `--review-decisions` 替换为真实审批系统回调消费者，并将回调结果写入 `HumanReviewRepository`
