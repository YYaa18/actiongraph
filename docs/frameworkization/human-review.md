# Human Review Integration

v2 human review now has a repository-backed integration point for external approval systems.

## Core Flow

When a high-risk action requires review:

1. `GoapExecutor` asks `HumanReviewPolicy.review(request)`.
2. `RepositoryBackedHumanReviewPolicy` looks for an existing `HumanReviewTask`.
3. If no task exists, it stores a pending task and returns `PENDING`.
4. The executor saves a suspended run and returns `SUSPENDED_PENDING_REVIEW`.
5. An external approval system reads pending tasks and records `APPROVED` or `DENIED`.
6. Calling `resume(runId, actions, registry)` asks the same policy again.
7. The recorded decision either lets execution continue or denies the action and triggers compensation.

## In-Memory Usage

```java
HumanReviewRepository reviewRepository = new InMemoryHumanReviewRepository();
HumanReviewPolicy reviewPolicy = new RepositoryBackedHumanReviewPolicy(reviewRepository);
```

External approval code can inspect and decide tasks:

```java
List<HumanReviewTask> pending = reviewRepository.findPending();

reviewRepository.decide(
        runId,
        actionId,
        HumanReviewDecision.APPROVED,
        "ops-lead",
        "Approved in approval console"
);
```

## JDBC Usage

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
- decision
- reviewer
- message
- created/updated timestamps

## Spring Boot

The Spring Boot starter now provides these defaults:

- `HumanReviewRepository` -> `InMemoryHumanReviewRepository`
- `HumanReviewPolicy` -> `RepositoryBackedHumanReviewPolicy`

Applications can replace either bean. For durable review tasks:

```java
@Bean
HumanReviewRepository humanReviewRepository(DataSource dataSource) {
    return new JdbcHumanReviewRepository(dataSource);
}
```

## Semantics

- `PENDING` suspends without compensation.
- `APPROVED` allows the pending action to execute on resume.
- `DENIED` is terminal and triggers compensation for actions already executed in the run.
- Review decisions are keyed by `runId + actionId`.
