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

第二刀已把样板域扩成准真实批量指标：

```bash
./gradlew :actiongraph-samples:runClaimsPrecheckBatchMetrics
```

实跑结果摘要：

```text
claimsPrecheckBatch totalRuns=5, completed=1, intercepted=3, failed=1, auditComplete=5
interceptRate=60.00%, auditCompletenessRate=100.00%
case claimId=CLM100, status=COMPLETED, intercepted=false, auditComplete=true
case claimId=CLM101, status=HALTED_UNREACHABLE, intercepted=true, auditComplete=true
case claimId=CLM102, status=HALTED_UNREACHABLE, intercepted=true, auditComplete=true
case claimId=CLM103, status=DENIED_BY_POLICY, intercepted=true, auditComplete=true
case claimId=CLM104, status=FAILED_COMPENSATED, intercepted=false, auditComplete=true
```

当前指标口径：

- 单均处理时长：批量运行 N 个理赔案，记录当前 auto-approve 模式下的端到端运行耗时
- 拦截率：资料缺失、已结案、超硬限额等被 guard/policy 拦截的比例
- 审计完整率：每个 run 的 TraceChainVerifier 通过率与缺失事件率

## F1 Next

下一刀应把批量指标接入更接近真实的样本输入：

- 从 CSV/数据库读取理赔样本，而不是固定 5 个内置 case
- 输出可交付给业务方的 CSV/Markdown 指标报告
- 将业务服务耗时、框架耗时、审批等待耗时从当前端到端耗时中拆成独立字段
