# ActionGraph Extension Points

DX1 adds the first development-experience layer for teams that want to package
business capability as reusable ActionGraph components instead of copying sample
code into every service.

All APIs in this document are marked `@Experimental` in `0.1.x`. They are
available for pilots, documented, and tested, but they are intentionally outside
the post-1.0 binary compatibility freeze until at least one minor release proves
the shape in real integrations.

## 1. Contribution Packaging

`ActionGraphContribution` is the packaging SPI for a business domain. A
contribution can expose:

- concrete `Action` instances;
- `GoalDefinition` entries for `GoalCatalog`;
- `GoalBlackboardSeeder` instances;
- annotated POJOs that should be adapted through `AnnotatedActionFactory`.

```java
public final class RenewalContribution implements ActionGraphContribution {
    @Override
    public List<Action> actions() {
        return RenewalActionFactory.actions(customerService, contractService, policyService, quoteService, approvalService);
    }

    @Override
    public List<GoalDefinition> goals() {
        return RenewalGoalCatalog.create().all().stream().toList();
    }

    @Override
    public List<GoalBlackboardSeeder> seeders() {
        return List.of(new RenewalQuoteBlackboardSeeder());
    }
}
```

Spring users can publish each contribution as a Bean. The main starter merges
contribution actions, goals, and seeders into the default registries. Duplicate
action IDs across contributions fail startup with both contribution class names
in the error message.

Non-Spring users can call the same methods directly and register the returned
components by hand.

## 2. Goal Validation

`GoalDefinition` now supports `seedConditions`, the static facts a seeder is
expected to place on the initial Blackboard. The planner still trusts runtime
state; `seedConditions` are development metadata for startup validation,
previews, graph export, and tests.

```java
new GoalDefinition(
        RenewalGoalTypes.PREPARE_RENEWAL_QUOTE,
        "Prepare a renewal quote and request approval.",
        RenewalGoals.prepareRenewalQuote(),
        List.of(GoalParameterDefinition.required("customerId", "Customer identifier.", "C001")),
        Set.of(RenewalConditions.CUSTOMER_ID_PRESENT)
);
```

`ActionGraphValidator` checks whether every catalog goal is reachable from its
declared seed conditions and registered actions.

```java
ValidationReport report = new ActionGraphValidator().validate(goalCatalog, actionRegistry.all());
if (!report.valid()) {
    throw new ActionGraphConfigurationException(report.formatText());
}
```

The diagnostics name missing conditions, dangling actions, and closest effect
spellings. This is intentionally richer than a boolean because most integration
errors are namespace or spelling mismatches.

Spring Boot starter behavior:

```yaml
actiongraph:
  validation:
    mode: FAIL # FAIL | WARN | OFF; default FAIL
```

`FAIL` stops startup, `WARN` logs through SLF4J, and `OFF` skips validation.

Seeder drift can be tested explicitly:

```java
SeederConformance.assertSeedsDeclaredConditions(seeder, sampleParameters, goalDefinition);
```

## 3. Graph Export

`ActionGraphExporter` renders the registered action graph for development
review and security/architecture walkthroughs. Mermaid output can be pasted into
Markdown, internal wiki pages, or review decks.

```java
String mermaid = new ActionGraphExporter().toMermaid(
        actionRegistry.all(),
        RenewalGoals.prepareRenewalQuote(),
        Set.of(RenewalConditions.CUSTOMER_ID_PRESENT)
);
```

Graph semantics:

- condition nodes are rounded;
- action nodes are boxes with risk level and human-review hints;
- edges are `condition -> action -> condition`;
- target conditions are highlighted;
- when seed conditions are supplied, unreachable actions and conditions are
  styled as unreachable.

The exporter reuses the same closure algorithm as validation, so the graph view
and startup diagnostics stay aligned.

## 4. LLM Provider Extension

The model boundary remains `LlmClient`: models produce goals and parameters,
never plans or executable actions.

DX1 adds `AbstractHttpChatClient` and `OpenAiCompatibleChatClient` in the
existing LLM module. The abstract base owns HTTP invocation, non-empty response
validation, and `LlmClientException` mapping. The OpenAI-compatible client owns
the common JSON body shape and response extraction from
`choices[0].message.content`.

```java
LlmClient client = new OpenAiCompatibleChatClient(
        "https://llm-gateway.bank.internal/v1/chat/completions",
        "qwen-max",
        System.getenv("BANK_LLM_KEY"),
        Map.of("X-Gateway-AppId", "actiongraph"),
        Duration.ofSeconds(20)
);
```

Spring Boot configuration is opt-in:

```yaml
actiongraph:
  llm:
    provider: openai-compatible # openai-compatible | deepseek | none
    base-url: https://llm-gateway.bank.internal/v1/chat/completions
    model: qwen-max
    api-key-env: BANK_LLM_KEY
    timeout: 20s
    headers:
      X-Gateway-AppId: actiongraph
```

Plaintext `actiongraph.llm.api-key` is rejected during property binding. Store
the actual key in an environment variable or enterprise secret manager and only
put the variable name in application configuration.

## 5. Retry And Timeout

`Action.executionPolicy()` controls runtime retry and per-attempt timeout. The
default is legacy behavior: one attempt, no backoff, no timeout.

```java
@ActionGraphAction(
        id = "payment.reserve",
        preconditions = "payment:READY",
        effects = "payment:RESERVED",
        maxAttempts = 3,
        backoffMillis = 200,
        timeoutMillis = 3000
)
public Reservation reserve(PaymentCommand command) {
    return paymentService.reserve(command);
}
```

Important contract:

- `maxAttempts > 1` is an explicit application promise that `execute` is
  idempotent for the same Blackboard state.
- Retries happen only when declared; existing actions remain single-attempt.
- Timeout means **unknown outcome**, not failure. The business system may have
  completed the operation after the caller timed out.
- A timed-out action is pushed onto the compensation stack before the run enters
  failure handling, so its `compensate` method is invoked.
- Compensation must tolerate both states: the forward operation may have
  completed, or it may never have happened.
- Timed-out attempts are not retried in DX1 because safe retry after unknown
  outcome requires provider-specific idempotency keys.

Spring Boot can override policies without changing business code:

```yaml
actiongraph:
  execution:
    policies:
      - action-id: payment.reserve
        max-attempts: 3
        backoff: 200ms
        timeout: 3s
```

Runtime Trace adds two event types:

- `ACTION_RETRIED`
- `ACTION_TIMED_OUT`

These events are experimental in `0.1.x` and should be treated as audit-preview
signals until the retry/idempotency convention has been validated in pilots.

## 6. Durability And Cross-Service Actions

MS1 adds optional step-level durability for services that orchestrate remote
Actions. It is off by default:

```yaml
actiongraph:
  durability:
    enabled: true
    recovery: CONTINUE      # CONTINUE | COMPENSATE
    heartbeat-interval: 30s
    stale-after: 5m
    recoverer-period: 60s   # 0 disables the built-in scheduler
```

Durability promotes the existing suspended-run snapshot into a general run
snapshot. Human review stores `SUSPENDED`; crash recovery stores `RUNNING`.
No new `RunStatus` values are introduced.

Runtime sequence for each Action:

1. before an attempt, the executor records `inFlightActionId`;
2. if the attempt succeeds, trace is flushed and a `RUNNING` checkpoint is
   saved with the updated Blackboard, executed action list, and compensation
   stack;
3. while the run is active, a heartbeat refreshes `heartbeatAt`;
4. a recoverer can atomically claim stale `RUNNING` checkpoints.

Recovery treats an in-flight Action as **unknown outcome**. The recoverer first
calls that Action's compensation method, then either replans from the checkpoint
(`CONTINUE`) or compensates the existing stack (`COMPENSATE`).

Cross-service Actions should pass a stable idempotency key to downstream
systems:

```java
IdempotencyKey key = new IdempotencyKey(
        context.runId(),
        "payment.reserve",
        context.attempt()
);

request.header(IdempotencyKey.HEADER_NAME, key.asHeaderValue());
```

Feign-style interceptor:

```java
public final class ActionGraphIdempotencyInterceptor implements RequestInterceptor {
    private final ExecutionContext context;
    private final String actionId;

    public void apply(RequestTemplate template) {
        IdempotencyKey key = new IdempotencyKey(context.runId(), actionId, context.attempt());
        template.header(IdempotencyKey.HEADER_NAME, key.asHeaderValue());
    }
}
```

The framework does not force this header because enterprise gateways and core
systems often have their own idempotency fields. The important contract is the
shape: run id + action id + attempt.
