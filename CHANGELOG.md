# Changelog

All notable changes to ActionGraph are recorded here.

This project follows semantic versioning once 1.0 is released. Before 1.0, compatibility is best effort and public API changes must still be documented.

## Unreleased

### Added

- Apache 2.0 license, security policy, contribution guide, changelog, and stable contract documentation.
- SLF4J API debug logging for planner, executor, compensation, trace flush, and suspend/resume decisions without binding a logging implementation.
- Public contract Javadocs for core Action, Policy, Human Review, Suspension, Blackboard, and Trace APIs, including thread-safety, null, resume-claim, compensation, and audit-order semantics.
- Typed ActionGraph exception hierarchy for input, configuration, conflict, not-found, and integration failures, plus a separate Java 8 control-plane API exception root.
- Provider-neutral runtime observability SPI with Spring Boot wiring and optional Micrometer-backed observation sink.
- Checked-in public API snapshot and Gradle verification task for compatibility-sensitive published module surfaces.
- JSpecify null-safety contracts for published source packages, with `@NullMarked` defaults and explicit `@Nullable` markers on optional public API boundaries.
- ArchUnit-style architecture guard tests for published module dependencies, core import boundaries, Spring isolation, and sample-code dependency bans.
- API stability annotations with `@Experimental` and `@Internal` markers for evolving or framework-internal public surfaces.
- GitHub Pages API reference publishing that generates per-module Javadocs under `docs/api/javadoc/*` during site deployment.
- Core and JDBC operational failures now use the `ActionGraphException` hierarchy, with a guard test preventing bare `IllegalStateException` from returning to those modules.
- japicmp-backed binary compatibility verification, with CI wiring, configurable release baselines, and explicit exclusion of `@Experimental` / `@Internal` surfaces from the 1.0 freeze.
- Experimental external event ingress: `ActionResult.waiting(...)`, `SUSPENDED_WAITING_EVENT`, `WAITING_EVENT` snapshots, `ExternalEventGateway`, `EventApplier`, timeout sweeping, JDBC event claims, and optional Spring HTTP callbacks.
- Experimental annotated Goal metadata: `@ActionGraphGoal`, `@GoalParameter`, automatic schema inference, Spring Boot auto-registration, and non-Spring `AnnotatedGoalFactory` support.
- Experimental annotated Goal seeding: `@ActionGraphGoalSeeder`, `@FromGoalParam`, built-in value conversion, custom `GoalValueConverter` hooks, `SeedResult` multi-write support, and Spring Boot auto-registration.
- Experimental Goal seeder DX refinements: automatic schema seeding from `@ActionGraphGoal(schema=...)`, `@GoalSchema(atLeastOne=...)`, `@BindGoalParams` record binding for explicit seeders, `TypedGoalValueConverter` type-level conversion, inherited Goal `seedConditions`, and default seed-condition seeders for no-parameter goals.
- Experimental root `ActionGraph` facade plus `ChatResult`, providing the Golden Path `start`, `chat`, and `resume` entry point for application code.
- Golden Path documentation (`golden-path.md`, `learning-path.md`, and L0-L4 quick start) plus a guard test that keeps L0 free of advanced SPI concepts.
- Experimental configuration-defined Goals with typed parameter seeding, startup validation, duplicate-source diagnostics, Goal and Action graph fingerprints in `RUN_STARTED` trace, and fingerprint-checked bundle import.
- Experimental Goal Studio for non-production drafting: LLM-generated Goal declarations only, validation auto-repair, framework-computed risk profiles, token-protected Spring endpoints, production-profile hard fail, and approved YAML bundle export.
- Optional Action descriptions on `Action` and `@ActionGraphAction`, surfaced in graph export and Goal Studio prompts.
- Experimental run identity model with `RunPrincipal`, explicit `ActionGraph.start/chat/resume` principal overloads, principal-aware `ExecutionContext`, policy evaluation, human-review tasks, suspend/resume snapshots, external event actor tracing, and JDBC persistence.
- Experimental Spring endpoint security bridge with `RunPrincipalResolver`, OAuth2 resource-server mode, endpoint scope checks, JWT claim-to-principal mapping, shared-secret development-mode fallback, and configuration-based any-of action role gates.

### Changed

- Repository governance now treats legal, security, contribution, changelog, and compatibility policy files as required release artifacts.
- `ActionGraphRuntimeApiService` is now documented and wired as a control-plane adapter over the root facade.
- `PermissionPolicy` and `ActionGraphRuntimeOperations` gained principal-aware default methods so existing two-argument implementations remain compatible.
- The renewal sample now dogfoods annotated Goal metadata/seeders and the root `ActionGraph` facade.

## 0.1.0 - 2026-06-11

### Added

- Typed GOAP runtime with deterministic planning, runtime guards, compensation, trace events, suspend/resume, and human review.
- Annotation-based Action adapter for ordinary Java methods.
- Goal interpretation contracts, GoalCatalog metadata, Blackboard seeders, and runtime operations entrypoints.
- Batch goal interpretation SPI and Java 8 runtime gateway interface.
- DeepSeek-compatible LLM provider module with provider-neutral prompt rendering and structured output parsing.
- JDBC persistence for trace, suspended runs, memory, human-review tasks, and read models.
- Spring Boot starter for runtime defaults, annotation scanning, governance, persistence, memory, human review, and opt-in HTTP endpoints.
- Control-plane API with Java 8 compatible component catalog and HTTP clients.
- Console query/export library and static website documentation.
- Renewal quote, order cancellation, and claims precheck sample domains.
