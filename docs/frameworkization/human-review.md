# Human Review Integration

v2 human review now has a repository-backed integration point for external approval systems. The non-Spring task/repository API, callback handler, and reusable task query/decision projections live in `actiongraph-human-review`; durable storage lives in `actiongraph-human-review-jdbc`; Spring auto-configuration for repository-backed review policy and optional JDBC storage lives in `actiongraph-human-review-spring-boot-starter`; optional MVC task and callback endpoints live in `actiongraph-human-review-api-spring-boot-starter`.

## Dependency

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-human-review")
}
```

## Core Flow

When a high-risk action requires review:

1. `GoapExecutor` asks `HumanReviewPolicy.review(request)`.
2. `RepositoryBackedHumanReviewPolicy` looks for an existing `HumanReviewTask`.
3. If no task exists, it stores a pending task and returns `PENDING`.
4. The executor saves a suspended run and returns `SUSPENDED_PENDING_REVIEW`.
5. An external approval system reads pending tasks and records `APPROVED` or `DENIED` for the current stage.
6. Calling `resume(runId, actions, registry)` asks the same policy again.
7. The recorded decision either advances to the next approval stage, lets execution continue after the final stage, or denies the action and triggers compensation.

`HumanReviewRequest.attributes` combines run metadata from the Runtime API with review-specific attributes from `ReviewAttributeContributor`. Runtime metadata commonly contains whitelisted gateway headers such as `requestHeader.X-Request-Id`, `requestHeader.X-Correlation-Id`, or `requestHeader.X-Source-System`, which is especially useful when a Java 8 legacy application calls ActionGraph over HTTP. Review-specific attributes win on key collisions, and the merged map passes through the executor's `DataMaskingPolicy` before it is stored on the review task.

## In-Memory Usage

```java
HumanReviewRepository reviewRepository = new InMemoryHumanReviewRepository();
HumanReviewPolicy reviewPolicy = new RepositoryBackedHumanReviewPolicy(reviewRepository);
```

External approval code can inspect and decide tasks:

```java
List<HumanReviewTask> pending = reviewRepository.findPending();
HumanReviewTask task = pending.getFirst();

reviewRepository.decideStage(
        task.runId(),
        task.actionId(),
        task.currentStageIndex(),
        HumanReviewDecision.APPROVED,
        "ops-lead",
        "Approved in approval console"
);
```

`decide(...)` remains available for simple single-stage integrations. `decideStage(...)` is recommended for approval callbacks because it includes the stage index the approval UI displayed; duplicate callbacks for the same stage fail with `StageAlreadyDecidedException` instead of accidentally advancing the next stage.

External callback consumers can also use the handler from `actiongraph-human-review` instead of calling the repository directly:

```java
HumanReviewCallbackHandler handler = new HumanReviewCallbackHandler(reviewRepository);

handler.handle(new HumanReviewCallback(
        "RUN-1",
        new ActionId("claim.approval.request"),
        0,
        HumanReviewDecision.APPROVED,
        "claims-checker",
        "Approved in approval console"
));
```

The claims precheck sample includes a JSONL callback replay fixture for local integration testing of shared-secret validation, duplicate-delivery idempotency, and resume continuity. See [Claims Precheck Review Callback Replay](claims-precheck-review-callbacks.md).

Approval inboxes, CLIs, and gateway adapters can use `HumanReviewApiService` from `actiongraph-human-review` when they want stable response DTOs instead of exposing `HumanReviewTask` directly:

```kotlin
implementation("com.actiongraph:actiongraph-human-review")
```

```java
HumanReviewApiService api = new HumanReviewApiService(reviewRepository);

List<HumanReviewTaskResponse> pending = api.pendingTasks();
HumanReviewTaskResponse decided = api.decide(
        "RUN-1",
        "claim.approval.request",
        pending.getFirst().currentStageIndex(),
        HumanReviewDecision.APPROVED,
        "claims-checker",
        "Approved in approval console"
);
```

## JDBC Usage

```kotlin
implementation("com.actiongraph:actiongraph-human-review-jdbc")
```

```java
HumanReviewRepository reviewRepository =
        new JdbcHumanReviewRepository(dataSource);
HumanReviewPolicy reviewPolicy =
        new RepositoryBackedHumanReviewPolicy(reviewRepository);
```

The JDBC repository stores:

- run id
- action id
- risk level
- plan preview
- current condition state
- blackboard preview
- approval stages
- current stage index
- stage decisions
- decision
- reviewer
- message
- created/updated timestamps

## Spring Boot

The base Spring Boot runtime starter only provides a safe pending `HumanReviewPolicy`; it does not create review task storage. Add the optional human-review starter when a Spring service wants repository-backed review tasks:

```kotlin
implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
implementation("com.actiongraph:actiongraph-human-review-spring-boot-starter")
```

The human-review starter provides these defaults:

- `HumanReviewRepository` -> `InMemoryHumanReviewRepository`
- `HumanReviewPolicy` -> `RepositoryBackedHumanReviewPolicy`
- `ApprovalChainResolver` -> `SingleStageApprovalChainResolver`

Applications can replace these beans. For durable review tasks in Spring Boot services, enable the shared JDBC switch with a `DataSource`; the starter then creates a JDBC-backed `HumanReviewRepository` and still backs off if the application provides its own repository. Non-Spring services can use `actiongraph-human-review-jdbc` directly:

```java
@Bean
HumanReviewRepository humanReviewRepository(DataSource dataSource) {
    return new JdbcHumanReviewRepository(dataSource);
}
```

Set `actiongraph.human-review.risk-based-approval-chain=true` with `actiongraph-governance-human-review-spring-boot-starter` to use `RiskBasedChainResolver`: HIGH risk actions require checker review and authorization; other actions remain single-stage unless request attributes ask for amount escalation. Review attributes are copied onto `HumanReviewTask`, so external approval systems can show why a task was escalated without recomputing business amounts.

Spring MVC applications can enable the optional task API without writing a controller:

```kotlin
implementation("com.actiongraph:actiongraph-human-review-spring-boot-starter")
implementation("com.actiongraph:actiongraph-human-review-api-spring-boot-starter")
```

```yaml
actiongraph:
  human-review:
    api:
      enabled: true
      path: /actiongraph/human-review/tasks
      token-header: X-ActionGraph-Review-Token
      shared-secret: ${ACTIONGRAPH_REVIEW_API_SECRET}
```

Endpoints:

```text
GET  /actiongraph/human-review/tasks/pending
GET  /actiongraph/human-review/tasks/runs/{runId}
GET  /actiongraph/human-review/tasks/runs/{runId}/actions/{actionId}
POST /actiongraph/human-review/tasks/runs/{runId}/actions/{actionId}/decision
```

The task API is disabled by default and is only created in Servlet MVC applications that have a `HumanReviewRepository` bean. It does not execute, resume, or compensate runs.

Spring MVC applications can enable the optional callback endpoint without writing a controller:

```kotlin
implementation("com.actiongraph:actiongraph-human-review-spring-boot-starter")
implementation("com.actiongraph:actiongraph-human-review-api-spring-boot-starter")
```

```yaml
actiongraph:
  human-review:
    callback-endpoint:
      enabled: true
      path: /actiongraph/human-review/callbacks
      token-header: X-ActionGraph-Review-Token
      shared-secret: ${ACTIONGRAPH_REVIEW_CALLBACK_SECRET}
```

The endpoint accepts one stage decision per request:

```json
{
  "runId": "RUN-1",
  "actionId": "claim.approval.request",
  "expectedStageIndex": 0,
  "decision": "APPROVED",
  "reviewer": "claims-checker",
  "comment": "Approved in approval console"
}
```

It returns the updated `HumanReviewTask` projection, including `currentStageIndex` and `stageDecisionCount`. The endpoint is disabled by default and is only created in Servlet MVC applications that have a `HumanReviewRepository` bean. This means the callback receiver can be the same business service that runs ActionGraph, or a separate approval integration service wired only to the review repository.

If `shared-secret` is configured, requests must include the configured token header. The token check runs before mutating the `HumanReviewRepository`, so rejected callbacks do not accidentally decide review tasks.

Callback failures use explicit HTTP semantics:

- `401 UNAUTHORIZED`: callback token is missing or invalid
- `400 BAD_REQUEST`: malformed callback payload or `PENDING` sent as a final decision
- `404 NOT_FOUND`: no pending review task exists for `runId + actionId`
- `409 CONFLICT`: the displayed stage index has already been decided, usually from duplicate callbacks or stale approval pages

## Semantics

- `PENDING` suspends without compensation.
- `APPROVED` advances the current stage; only approval of the final stage allows the pending action to execute on resume.
- `DENIED` is terminal and triggers compensation for actions already executed in the run.
- Review decisions are keyed by `runId + actionId`.
