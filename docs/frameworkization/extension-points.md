# ActionGraph Extension Points

**Layer: Packaging / SPI**

DX1 adds the first development-experience layer for teams that want to package
business capability as reusable ActionGraph components instead of copying sample
code into every service.

All APIs in this document are marked `@Experimental` in `0.2.x`. They are
available for pilots, documented, and tested, but they are intentionally outside
the post-1.0 binary compatibility freeze until at least one minor release proves
the shape in real integrations.

## 1. Contribution Packaging

`ActionGraphContribution` is the packaging SPI for a business domain. A
contribution can expose:

- concrete `Action` instances;
- `GoalDefinition` entries for `GoalCatalog`;
- `GoalBlackboardSeeder` instances;
- annotated POJOs that should be adapted through `AnnotatedActionFactory`,
  `AnnotatedGoalFactory`, and `AnnotatedGoalSeederFactory`.

```java
public final class RenewalContribution implements ActionGraphContribution {
    @Override
    public List<Action> actions() {
        return RenewalActionFactory.actions(customerService, contractService, policyService, quoteService, approvalService);
    }

    @Override
    public List<GoalDefinition> goals() {
        return RenewalGoalAnnotations.goals();
    }

    @Override
    public List<GoalBlackboardSeeder> seeders() {
        return RenewalGoalAnnotations.seeders();
    }
}
```

Spring users can publish each contribution as a Bean. The main starter merges
contribution actions, goals, and seeders into the default registries. Duplicate
action IDs across contributions fail startup with both contribution class names
in the error message.

Non-Spring users can call the same methods directly and register the returned
components by hand.

## 2. Annotated Goal Metadata

Goal metadata can be declared with `@ActionGraphGoal` instead of a configuration
class that manually builds `GoalDefinition` objects. The annotation supplies the
goal type, target conditions, seed conditions, and human-readable description;
the schema is inferred from a record/class or from annotated method parameters.

Spring Boot users only publish the declaration as a Bean. The starter registers
it into `GoalCatalog` automatically.

```java
@Component
final class OrderCancellationGoals {
    @ActionGraphGoal(
            type = "order.cancel",
            description = "为指定订单发起取消申请并提交运营审批",
            name = "requestOrderCancellation",
            targetConditions = "order:OPS_APPROVAL_REQUESTED",
            seedConditions = "order:ORDER_ID_PRESENT"
    )
    void requestCancellation(
            @GoalParameter(name = "orderId", description = "要取消的订单编号", example = "O100")
            String ignored
    ) {
        // Method body is never invoked; it is metadata for GoalCatalog.
    }
}
```

For teams that prefer schema-first declarations, annotate a record. Record
components are read in declaration order, which also gives stable prompt output.

```java
@ActionGraphGoal(
        type = "claim.precheck",
        description = "对理赔案件做准入预审并输出处理建议",
        targetConditions = "claim:PRECHECKED",
        seedConditions = "claim:CLAIM_ID_PRESENT"
)
record ClaimPrecheckGoal(
        @GoalParameter(description = "理赔案件号", example = "CLM-1001")
        String claimId,
        @GoalParameter(description = "渠道来源", required = false, example = "mobile")
        String channel
) {
}
```

Non-Spring users can derive definitions directly:

```java
GoalCatalog catalog = new GoalCatalog();
for (GoalDefinition goal : AnnotatedGoalFactory.definitions(ClaimPrecheckGoal.class)) {
    catalog.register(goal);
}
```

Method parameter names require either Java's `-parameters` compiler flag or an
explicit `@GoalParameter(name = "...")`. Record schemas are recommended because
they avoid compiler-flag drift and make the LLM-facing schema obvious in code.

Spring auto-registration can be disabled when a service wants to assemble the
catalog manually:

```yaml
actiongraph:
  goals:
    auto-register-annotated: false
```

## 3. Annotated Goal Seeders

Seeder code is where most application boilerplate used to collect: reading
`GoalParameters`, converting strings into domain types, resolving references,
putting values into the Blackboard, and adding seed conditions. DX now supports
annotated seeder methods for that binding layer.

```java
@Component
final class ProductSeeders {
    @ActionGraphGoalSeeder(
            value = "product.create",
            seedConditions = "product:CREATE_REQUESTED"
    )
    Product seedCreate(
            @FromGoalParam("name") String name,
            @FromGoalParam("price") BigDecimal price,
            @FromGoalParam("stock") int stock,
            @FromGoalParam(value = "status", required = false, converter = ProductStatusConverter.class)
            ProductStatus status
    ) {
        Product product = new Product();
        product.setName(name);
        product.setPrice(price);
        product.setStock(stock);
        product.setStatus(status == null ? ProductStatus.ON_SALE : status);
        return product;
    }
}
```

The starter scans Spring Beans for `@ActionGraphGoalSeeder` and registers the
inferred `GoalBlackboardSeeder` automatically. Non-Spring users can build
seeders explicitly:

```java
List<GoalBlackboardSeeder> seeders =
        AnnotatedGoalSeederFactory.seeders(new ProductSeeders());
```

Built-in conversion covers `String`, `Integer`/`int`, `Long`/`long`,
`BigDecimal`, `Double`/`double`, `Boolean`/`boolean`, and enums. Complex
conversion stays in application code through `GoalValueConverter<T>`.

```java
@Component
final class ProductReferenceToId implements GoalValueConverter<Long> {
    private final ProductService productService;

    ProductReferenceToId(ProductService productService) {
        this.productService = productService;
    }

    @Override
    public Long convert(String rawValue, GoalParameterBindingContext context) {
        return productService.findByReference(rawValue).getId();
    }
}
```

Spring resolves converter classes from the BeanFactory first, so converters can
depend on services, repositories, HTTP clients, or policy components. Modular
JPMS applications should prefer Bean-based converter registration because
reflection-based construction may be blocked by strong encapsulation. Without
Spring, converters must expose a no-arg constructor or be supplied through a
custom `GoalValueConverterResolver`.

Simple seeder methods return one object and ActionGraph writes it to the
Blackboard. Use `@BlackboardValue` on the method to write a keyed value:

```java
@ActionGraphGoalSeeder(value = "product.delete", seedConditions = "product:DELETE_REQUESTED")
@BlackboardValue("productId")
Long seedProductId(
        @FromGoalParam(value = "productRef", converter = ProductReferenceToId.class)
        Long productId
) {
    return productId;
}
```

When a seeder must write multiple values, return `SeedResult`:

```java
return SeedResult.builder()
        .put(Long.class, "productId", productId)
        .put(product)
        .addCondition("product:REFERENCE_RESOLVED")
        .build();
```

Direct `GoalParameters` and `Blackboard` parameters are also supported as escape
hatches. Auto-registration can be disabled for applications that assemble
registries manually:

```yaml
actiongraph:
  seeders:
    auto-register-annotated: false
```

When both `@ActionGraphGoal.seedConditions` and
`@ActionGraphGoalSeeder.seedConditions` are available for the same goal type,
the Spring starter validates at startup that the seeder-declared conditions cover
the goal-declared seed conditions. A mismatch fails startup before any runtime
request can enter the planner.

## 4. Goal Validation

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

## 5. Graph Export

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

## 6. LLM Provider Extension

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

## 7. Retry And Timeout

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

## 8. Durability And Cross-Service Actions

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

## 7. External Event Ingress

MS2 adds an optional event-wait boundary for long-running external systems:
ticket callbacks, batch result notifications, payment confirmations, or any
process where an Action submits work and later receives a correlated event.

An Action can return `ActionResult.waiting(...)`:

```java
public ActionResult execute(ExecutionContext context) {
    ApprovalCommand command = buildCommand(context.blackboard());
    approvalClient.submit(command);

    return ActionResult.waiting(
            "approval.completed",
            command.approvalId(),
            Duration.ofHours(6),
            "waiting for approval callback"
    );
}
```

The waiting Action is treated as completed for compensation purposes. If the
event times out, its compensation method is called because the submission may
already have reached the external system.

The event fold-in is application code:

```java
public final class ApprovalCompletedApplier implements EventApplier {
    public String eventType() {
        return "approval.completed";
    }

    public void apply(EventPayload payload, Blackboard blackboard) {
        ApprovalCallback callback = parse(payload.body());
        blackboard.put(callback);
        blackboard.addCondition(Condition.of("approval:COMPLETED"));
    }
}
```

Delivery semantics:

1. `ExternalEventGateway` atomically claims a `WAITING_EVENT` snapshot by
   `eventType + correlationId`;
2. the registered `EventApplier` mutates the Blackboard;
3. the gateway writes `EVENT_DELIVERED` trace and saves a `RUNNING` checkpoint;
4. the executor continues the same run id, trace sequence, and compensation
   stack.

If the process crashes after step 3, MS1 recovery can continue from the
checkpoint without re-delivering the event. If application parsing fails before
the checkpoint, the waiting snapshot is restored and the transport can retry.

Spring Boot creates `ExternalEventGateway` and `EventWaitSweeper` beans when the
main starter is present. The HTTP callback endpoint is optional and disabled by
default:

```yaml
actiongraph:
  events:
    default-timeout: 24h
    sweep-period: 60s        # 0 disables the built-in sweeper
    callback-endpoint:
      enabled: true
      path: /actiongraph/events
      token-header: X-ActionGraph-Event-Token
      shared-secret: ${ACTIONGRAPH_EVENT_CALLBACK_SECRET}
```

Endpoint shape:

```text
POST /actiongraph/events/{eventType}/{correlationId}
X-ActionGraph-Event-Token: <shared secret>
Content-Type: application/json

{"decision":"APPROVED"}
```

Production systems can skip the HTTP controller entirely and call
`ExternalEventGateway.deliver(...)` from a message listener or proprietary
callback adapter.
