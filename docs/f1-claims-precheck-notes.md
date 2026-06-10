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

## F1 Next

下一刀应把这个样板域扩成准真实批量指标：

- 单均处理时长：批量运行 N 个理赔案，区分框架耗时、业务服务耗时、审批等待耗时
- 拦截率：资料缺失、已结案、超硬限额等被 guard/policy 拦截的比例
- 审计完整率：每个 run 的 TraceChainVerifier 通过率与缺失事件率
