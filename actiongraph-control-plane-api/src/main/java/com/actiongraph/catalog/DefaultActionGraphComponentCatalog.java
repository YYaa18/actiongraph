package com.actiongraph.catalog;

import java.util.Arrays;
import java.util.List;

public final class DefaultActionGraphComponentCatalog {
    private DefaultActionGraphComponentCatalog() {
    }

    public static ActionGraphComponentCatalog create() {
        List<ActionGraphComponent> components = list(
                component("actiongraph-bom", ComponentKind.PLATFORM,
                        "Version platform for aligning ActionGraph module versions.",
                        ComponentCompatibility.NO_RUNTIME_CODE,
                        list("version-alignment"), list(), list()),
                component("actiongraph-core", ComponentKind.CORE,
                        "Core GOAP runtime with Action SPI, planner, executor, policy, trace, compensation, suspend/resume, goal interpretation contracts, runtime entry service, and structured memory contracts.",
                        ComponentCompatibility.JAVA21_PLUS,
                        list("action-spi", "planning", "execution", "policy", "trace", "suspend-resume",
                                "goal-interpretation", "goal-catalog", "blackboard-seeding",
                                "runtime-entry-service", "interpret-start-resume",
                                "runtime-request-trace-metadata", "structured-memory", "context-loading"),
                        list(), list()),
                component("actiongraph-annotations", ComponentKind.FRAMEWORK,
                        "Pure Java annotation adapter that maps ordinary methods into ActionGraph actions.",
                        ComponentCompatibility.JAVA21_PLUS,
                        list("annotation-actions", "reflection-adapter"),
                        list("actiongraph-core"), list("actiongraph-spring-boot-starter")),
                component("actiongraph-human-review", ComponentKind.FRAMEWORK,
                        "Repository-backed human review tasks, approval chains, review policy, callback handling, and task query/decision APIs.",
                        ComponentCompatibility.JAVA21_PLUS,
                        list("human-review", "approval-chain", "review-callbacks",
                                "review-task-api-service", "review-decision-service"),
                        list("actiongraph-core"),
                        list("actiongraph-persistence-jdbc", "actiongraph-spring-boot-starter")),
                component("actiongraph-llm-deepseek", ComponentKind.PROVIDER,
                        "Provider-neutral LLM goal interpretation support and DeepSeek-compatible client provider.",
                        ComponentCompatibility.JAVA21_PLUS,
                        list("llm-contracts", "prompt-rendering", "structured-output", "deepseek-client"),
                        list("actiongraph-core"), list()),
                component("actiongraph-persistence-jdbc", ComponentKind.INFRASTRUCTURE,
                        "Low-level JDBC repositories for trace, suspended runs, memory, human review, and trace read models.",
                        ComponentCompatibility.JAVA21_PLUS,
                        list("jdbc-trace", "jdbc-suspended-run", "jdbc-memory", "jdbc-human-review",
                                "jdbc-trace-read-model"),
                        list("actiongraph-core", "actiongraph-human-review"),
                        list("actiongraph-spring-boot-starter")),
                component("actiongraph-spring-boot-starter", ComponentKind.FRAMEWORK,
                        "Primary Spring Boot starter for annotation scanning, runtime defaults, JDBC repositories, structured memory, repository-backed human review, governance, and optional runtime/catalog/review/console endpoints.",
                        ComponentCompatibility.JAVA21_PLUS,
                        list("spring-runtime-autoconfiguration", "action-scanning",
                                "spring-jdbc-runtime-repositories",
                                "spring-memory-autoconfiguration", "spring-jdbc-memory-repository",
                                "spring-human-review-policy", "spring-jdbc-human-review-repository",
                                "spring-governance-autoconfiguration", "spring-review-governance",
                                "runtime-http-api", "runtime-request-trace-metadata",
                                "component-catalog-http-api",
                                "human-review-http-api", "human-review-callback-http-api",
                                "spring-console-service-autoconfiguration", "console-json-http-api",
                                "console-html-ui", "console-export-http-api", "spring-console-jdbc-repository"),
                        list("actiongraph-core", "actiongraph-annotations", "actiongraph-console",
                                "actiongraph-control-plane-api",
                                "actiongraph-governance",
                                "actiongraph-human-review", "actiongraph-persistence-jdbc"),
                        list("actiongraph-llm-deepseek")),
                component("actiongraph-governance", ComponentKind.GOVERNANCE,
                        "Reusable masking, amount-limit, rule-based permission, review-attribute, and approval-routing policies.",
                        ComponentCompatibility.JAVA21_PLUS,
                        list("masking", "amount-limits", "permission-policy", "review-attributes", "approval-routing"),
                        list("actiongraph-core", "actiongraph-human-review"),
                        list("actiongraph-spring-boot-starter")),
                component("actiongraph-console", ComponentKind.CONTROL_PLANE,
                        "Reusable read-only console query service, JDBC read-model adapter, and CSV/JSONL audit export service.",
                        ComponentCompatibility.JAVA21_PLUS,
                        list("console-query-service", "console-jdbc-read-model", "audit-export", "csv", "jsonl",
                                "read-only-run-monitoring"),
                        list("actiongraph-core", "actiongraph-persistence-jdbc"),
                        list("actiongraph-spring-boot-starter")),
                component("actiongraph-control-plane-api", ComponentKind.CONTROL_PLANE,
                        "Java 8 compatible component catalog, control-plane contracts, properties-based aggregate configuration, safe GET retries, aggregate/runtime/catalog/human-review/console HTTP clients, audit header forwarding, and shared-secret token verification.",
                        ComponentCompatibility.JAVA8_CLIENT,
                        list("component-catalog", "composition-profiles",
                                "control-plane-response-contracts", "error-response-contracts",
                                "java8-control-plane-client", "java8-control-plane-properties",
                                "java8-safe-get-retries", "java8-runtime-client",
                                "java8-component-catalog-client", "java8-human-review-client",
                                "java8-console-client", "http-audit-headers", "shared-secret-token-verification"),
                        list(), list("actiongraph-spring-boot-starter")),
                component("actiongraph-samples", ComponentKind.SAMPLE,
                        "Reference domains, demos, and batch scenarios; not intended as a library dependency.",
                        ComponentCompatibility.SAMPLE_ONLY,
                        list("samples", "demos", "benchmarks"), list(), list())
        );

        List<ActionGraphCompositionProfile> profiles = list(
                profile("core-runtime",
                        "Smallest pure Java runtime kernel.",
                        list("actiongraph-core"),
                        list("Manual action registration and manual persistence wiring.")),
                profile("annotation-runtime",
                        "Pure Java runtime with low-intrusion annotated business methods.",
                        list("actiongraph-core", "actiongraph-annotations"),
                        list("No Spring requirement; business classes do not implement Action.")),
                profile("spring-business-runtime",
                        "Spring Boot business runtime with runtime, persistence, memory, human-review, governance, and endpoint auto-configuration available behind properties.",
                        list("actiongraph-spring-boot-starter"),
                        list("Use this inside business services that expose their own API surface.",
                                "HTTP endpoints remain disabled unless their actiongraph.*.enabled properties are set.")),
                profile("durable-spring-runtime",
                        "Spring Boot runtime with durable trace, suspended runs, memory, and review task storage.",
                        list("actiongraph-spring-boot-starter"),
                        list("Requires application DataSource and actiongraph.persistence.jdbc.enabled=true.")),
                profile("runtime-entry-api",
                        "Stable interpret/start/resume service surface without forcing a console UI.",
                        list("actiongraph-spring-boot-starter"),
                        list("Enable actiongraph.runtime.api.enabled=true when the built-in Spring MVC endpoint is needed.")),
                profile("human-review-control-plane",
                        "Approval task inbox, decision API, and external decision callback surface.",
                        list("actiongraph-human-review", "actiongraph-spring-boot-starter"),
                        list("Enable actiongraph.human-review.api.enabled=true and/or actiongraph.human-review.callback-endpoint.enabled=true.",
                                "Use actiongraph.persistence.jdbc.enabled=true for durable approval inboxes.")),
                profile("console-control-plane",
                        "Read-only operational console and audit export surface.",
                        list("actiongraph-console", "actiongraph-spring-boot-starter"),
                        list("The main Spring starter exposes Console API, UI, export endpoints, and optional JDBC read-model repository wiring behind actiongraph.console.* gates.")),
                profile("full-control-plane",
                        "Built-in runtime, review, callback, component catalog, console, and export endpoints.",
                        list("actiongraph-spring-boot-starter"),
                        list("Endpoint beans still require their own enablement properties and backing beans.",
                                "Control-plane APIs are configured through properties, not through separate endpoint artifacts.")),
                profile("ecosystem-introspection",
                        "Read-only component catalog for gateways, consoles, and deployment checks.",
                        list("actiongraph-control-plane-api", "actiongraph-spring-boot-starter"),
                        list("The Java 8 catalog and HTTP client artifact can be used without Spring.",
                                "The Spring catalog endpoint is enabled with actiongraph.component-catalog.enabled=true.")),
                profile("control-plane-response-contracts",
                        "Java 8 compatible response DTOs, properties-based aggregate configuration, safe GET retries, aggregate/runtime/catalog/human-review/console HTTP clients, and token verification for legacy adapters.",
                        list("actiongraph-control-plane-api"),
                        list("Spring endpoint controllers use this module to keep error response JSON and shared-secret semantics consistent.",
                                "Java 8 applications can use ActionGraphControlPlaneHttpClient to configure all deployed control-plane endpoints from one /actiongraph base URL, or only the split surfaces they enable.",
                                "Java 8 applications can use ActionGraphControlPlaneHttpClientProperties to build the same aggregate client from .properties files, configuration centers, or gateway tables.",
                                "Java 8 applications can opt into GET-only retries for transient catalog, review-task query, and Console read failures without automatically retrying side-effecting POST calls.",
                                "Java 8 applications can use ActionGraphRuntimeHttpClient to call a deployed runtime API without Spring or third-party dependencies.",
                                "Java 8 applications can use ActionGraphComponentCatalogHttpClient to inspect deployed component catalog metadata without Spring or third-party dependencies.",
                                "Java 8 applications can use ActionGraphHumanReviewHttpClient to query and decide deployed human-review tasks without Spring or third-party dependencies.",
                                "Java 8 applications can use ActionGraphConsoleHttpClient to query read-only run status, trace details, and audit exports without Spring or third-party dependencies.")),
                profile("java8-legacy-client",
                        "Java 8 client-side integration for legacy applications and gateways.",
                        list("actiongraph-control-plane-api"),
                        list("Use actiongraph-control-plane-api for local compatibility discovery, composition metadata, and deployed ActionGraph runtime/component-catalog/human-review/console HTTP calls.",
                                "Systems that cannot load Java 8 client artifacts should call through HTTP, an enterprise gateway, ESB, or a Java 8+ sidecar instead of embedding ActionGraph jars.",
                                "The embeddable runtime kernel remains java21-plus until a dedicated compatibility refactor removes modern language and library features.")),
                profile("full-pilot-service",
                        "Pilot-oriented Spring business service with durable runtime, governance, human review, console, and catalog endpoints.",
                        list("actiongraph-spring-boot-starter", "actiongraph-llm-deepseek"),
                        list("Endpoint exposure is property-gated; enable only the surfaces the deployment needs.",
                                "DeepSeek remains an explicit provider dependency."))
        );

        return new ActionGraphComponentCatalog(components, profiles);
    }

    private static ActionGraphComponent component(
            String module,
            ComponentKind kind,
            String description,
            ComponentCompatibility compatibility,
            List<String> capabilities,
            List<String> requires,
            List<String> optionalWith
    ) {
        return new ActionGraphComponent(module, kind, description, capabilities, requires, optionalWith,
                compatibility.label());
    }

    private static ActionGraphCompositionProfile profile(
            String name,
            String description,
            List<String> modules,
            List<String> notes
    ) {
        return new ActionGraphCompositionProfile(name, description, modules, notes);
    }

    @SafeVarargs
    private static <T> List<T> list(T... values) {
        return Arrays.asList(values);
    }
}
