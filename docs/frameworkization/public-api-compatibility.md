# Public API Compatibility Check

ActionGraph keeps a checked-in public API snapshot at
`docs/api/public-api.snapshot`.

The root `check` task runs `verifyPublicApiSnapshot`. The task compiles all
published library modules, reflects their public/protected class members, and
compares the generated surface with the checked-in snapshot.

```bash
./gradlew verifyPublicApiSnapshot
```

When a public API change is intentional, update the snapshot explicitly:

```bash
./gradlew verifyPublicApiSnapshot -PupdatePublicApiSnapshot=true
```

Then review the diff, update `CHANGELOG.md`, and describe compatibility impact
when the change is not purely additive.

## Scope

The snapshot covers published library modules listed in the root Gradle
`libraryModuleDescriptions` map:

- `actiongraph-core`
- `actiongraph-control-plane-api`
- `actiongraph-human-review`
- `actiongraph-llm-deepseek`
- `actiongraph-persistence-jdbc`
- `actiongraph-spring-boot-starter`
- `actiongraph-governance`
- `actiongraph-console`

`actiongraph-samples` is executable documentation and is intentionally excluded.
`actiongraph-bom` has no runtime classes.

## What This Catches

- removed public/protected classes, methods, constructors, or fields;
- renamed public/protected APIs;
- changed erased method signatures;
- changed thrown checked exceptions;
- accidental exposure of new public/protected types.

The snapshot is not a replacement for semantic review. It does not prove that a
method still behaves the same, and it does not compare generic type parameters
beyond erased JVM signatures.

## 1.0 Rule

Before 1.0, public API changes are allowed but must be deliberate. After 1.0,
changes that remove or break snapshot entries require a major version unless a
security fix makes the break unavoidable.
