# Dependency Composition

ActionGraph modules are intentionally small and independently usable. Import the BOM once, then select only the components a service actually needs.

## Always Start With The BOM

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
}
```

The BOM aligns versions for every published ActionGraph module. It has no runtime classes and does not pull any module by itself.

## Pure Java Runtime

Use this for a non-Spring service or library that wires actions and repositories manually.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-core")
}
```

## Spring Business Runtime

Use this for a business service that executes ActionGraph runs and registers ordinary Spring bean methods as actions.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
}
```

This does not expose HTTP control-plane endpoints.

## Durable Production Runtime

Add JDBC repositories when traces, suspended runs, review tasks, and memory must survive process restarts.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-jdbc-spring-boot-starter")
}
```

Spring Boot applications still provide their own database driver and `DataSource`, then enable the repositories with:

```yaml
actiongraph:
  persistence:
    jdbc:
      enabled: true
      suspended-run-claim-timeout: 15m
      blackboard:
        allowed-packages:
          - com.example.business
```

Non-Spring services, or applications that want complete manual control, can depend on `actiongraph-persistence-jdbc` directly and instantiate the repositories themselves.

## Natural-Language Entry

Add the DeepSeek-compatible adapter when the service needs LLM goal interpretation.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-llm-deepseek")
}
```

The LLM interpreter produces goals and parameters only; it does not generate plans or execute actions.

## Approval Callback Receiver

Use this in the same business service, or in a separate approval integration service, to receive external review decisions.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-human-review-spring-boot-starter")
}
```

The callback starter requires a `HumanReviewRepository` bean. A business runtime service can get the in-memory default from `actiongraph-spring-boot-starter`; production services normally add `actiongraph-jdbc-spring-boot-starter` so the callback handler writes durable review decisions.

## Read-Only Monitoring Service

Use this for a separate control-plane application that only queries trace data.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-console-spring-boot-starter")
}
```

The Console starter depends on the JDBC read model and requires a `DataSource`. It must remain read-only: it does not execute, resume, approve, deny, or compensate runs.

## Full Pilot Service

Use this for a single deployment that runs the business workflow, receives approval callbacks, persists state, interprets natural-language goals, and exposes the read-only Console.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-jdbc-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-llm-deepseek")
    implementation("com.actiongraph:actiongraph-human-review-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-console-spring-boot-starter")
}
```

The same modules can later be split into separate runtime, approval, and monitoring services without changing their version alignment.
