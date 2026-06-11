# Null-Safety Contracts

ActionGraph uses [JSpecify](https://jspecify.dev/) annotations on published API packages.

## Contract

- Published source packages opt into `@NullMarked`, so unannotated public API parameters and return values are non-null by default.
- Public API parameters that intentionally accept `null` are marked with `@Nullable`.
- Public accessors that may return `null`, such as optional query filters, are marked with `@Nullable`.
- Constructor parameters that tolerate `null` only to normalize stored state may remain unannotated when the resulting accessor is non-null. Javadoc documents those normalization rules.

## Dependency Shape

JSpecify is added as `compileOnlyApi` for published library modules and as a BOM constraint. It is a compile-time contract for IDEs, Kotlin interop, and static analysis; ActionGraph does not use it for runtime validation.

`actiongraph-control-plane-api` remains the Java 8 compatible client artifact. JSpecify annotations are compile-time metadata and do not introduce an ActionGraph runtime dependency for Java 8 estates.

## Compatibility

Nullability is part of the public contract:

- Adding `@Nullable` to a parameter that was already documented and implemented as optional is compatible.
- Removing `@Nullable` from a parameter, or making a previously non-null accessor nullable, is compatibility-sensitive after 1.0.
- New public packages must include a `package-info.java` with `@NullMarked`.

The component catalog guard test verifies published packages are `@NullMarked` and that key optional API boundaries keep explicit `@Nullable` markers.

## Current High-Value Boundaries

- `DataMaskingPolicy` accepts nullable input and returns non-null masked values.
- `ActionGraphRuntimeOperations` accepts optional known parameters and run metadata maps.
- Java 8 HTTP clients accept optional request headers and optional query filters.
- Console and JDBC run-query records expose nullable filter accessors.
- Human-review decision and callback DTOs mark optional reviewer, comment, and expected-stage fields.
