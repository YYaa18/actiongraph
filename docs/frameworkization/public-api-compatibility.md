# Public API Compatibility Check

ActionGraph uses two complementary gates:

- a checked-in public API snapshot at `docs/api/public-api.snapshot`;
- a japicmp binary compatibility task that compares current jars with a
  configured release baseline.

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

## Binary Compatibility With japicmp

Run the binary gate directly:

```bash
./gradlew verifyBinaryCompatibility
```

Before 1.0, this task passes without a baseline and logs that japicmp will run
when a baseline is configured. At `1.0.0` and later, the task fails unless
`ACTIONGRAPH_BASELINE_VERSION` or `-PactionGraphBaselineVersion` is supplied.

For a released baseline in Maven Central or the configured enterprise
repository:

```bash
ACTIONGRAPH_BASELINE_VERSION=1.0.0 ./gradlew verifyBinaryCompatibility
```

For a private repository:

```bash
ACTIONGRAPH_BASELINE_REPOSITORY_URL=https://repo.example.com/releases \
ACTIONGRAPH_BASELINE_VERSION=1.0.0 \
./gradlew verifyBinaryCompatibility
```

For a local release rehearsal:

```bash
./gradlew publishToMavenLocal
./gradlew verifyBinaryCompatibility \
  -PactionGraphBaselineVersion=1.0.0 \
  -PactionGraphUseMavenLocalBaseline=true
```

japicmp reports are written to `build/reports/binary-compatibility/` and fail
the build on binary-incompatible changes. The gate excludes APIs marked
`@Experimental` or `@Internal`; today that keeps `runtime/api/batch`,
structured memory, and LLM provider contracts outside the stable freeze.

## 1.0 Rule

Before 1.0, public API changes are allowed but must be deliberate. After 1.0,
changes that remove or break snapshot entries require a major version unless a
security fix makes the break unavoidable. After 1.0, binary-incompatible
japicmp results are release blockers unless the version is bumped to the next
major line or the affected API is explicitly outside the stable contract.
