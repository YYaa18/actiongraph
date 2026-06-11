# ActionGraph Stable Contract

This document defines the compatibility policy ActionGraph is moving toward for 1.0.

## Status

ActionGraph is currently pre-1.0. The project may still change public APIs while the framework is being hardened, but every public contract change must be intentional, tested, and recorded in `CHANGELOG.md`.

At 1.0, the public API surface below becomes compatibility-protected.

## Versioning

ActionGraph uses semantic versioning after 1.0:

- Patch releases fix defects and must remain source and binary compatible.
- Minor releases may add public APIs, modules, configuration properties, capabilities, and adapters, but must remain source and binary compatible.
- Major releases may remove deprecated APIs and make incompatible changes.

## Public API Surface

The following are public API after 1.0 unless documented otherwise:

- published Maven artifacts listed in `README.md` and the component catalog;
- public and protected types in `com.actiongraph.action`, `com.actiongraph.planning`, `com.actiongraph.runtime`, `com.actiongraph.runtime.api`, `com.actiongraph.runtime.api.batch`, `com.actiongraph.policy`, `com.actiongraph.trace`, `com.actiongraph.interpretation`, and `com.actiongraph.memory`;
- Java 8 control-plane API and HTTP gateway types in `actiongraph-control-plane-api`;
- Spring Boot configuration properties and documented `actiongraph.*` property names;
- SQL schema expectations documented for JDBC persistence;
- JSON request and response shapes documented for built-in HTTP endpoints.

Sample packages under `com.actiongraph.samples` are executable documentation and are not stable APIs.

## Compatibility Rules

After 1.0, compatible changes include:

- adding default methods to interfaces when existing implementations remain source and binary compatible;
- adding overloads, constructors, records, enum-adjacent helper methods, capabilities, or optional adapters;
- adding configuration properties with safe defaults;
- adding trace event attributes without removing existing keys.

After 1.0, incompatible changes require a major version unless a security fix makes the break unavoidable:

- removing or renaming public types, methods, constructors, record components, modules, configuration properties, or documented endpoint fields;
- changing method semantics in a way that invalidates existing implementations;
- adding abstract interface methods without defaults;
- changing Java compatibility labels or raising the minimum Java level of a public artifact;
- removing documented trace fields, response fields, or persisted snapshot compatibility without a migration path.

## Deprecation Policy

Removal requires a deprecation period:

- mark the API with `@Deprecated`;
- document the replacement in Javadoc and `CHANGELOG.md`;
- keep the deprecated API for at least one minor release after deprecation;
- remove only in the next major release unless a security issue requires faster action.

## Java Compatibility

`actiongraph-control-plane-api` is the Java 8 compatible client artifact. Runtime, Spring, JDBC, governance, LLM, console, and sample modules currently require Java 21 or newer and should be deployed on the ActionGraph service side for Java 8 estates.

Any change to compatibility labels must be reflected in:

- `DefaultActionGraphComponentCatalog`;
- `build.gradle.kts` Java 8 compatibility gates;
- Java 8 Maven consumer examples;
- README and frameworkization docs.

## Evidence

Compatibility-sensitive changes should include at least one of:

- source-level tests using the public interface;
- Java 8 compilation tests for Java 8 artifacts;
- component catalog or documentation guard tests;
- migration notes in `CHANGELOG.md`;
- explicit compatibility notes in `docs/frameworkization/`.
