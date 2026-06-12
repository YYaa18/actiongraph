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

### Changed

- Repository governance now treats legal, security, contribution, changelog, and compatibility policy files as required release artifacts.

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
