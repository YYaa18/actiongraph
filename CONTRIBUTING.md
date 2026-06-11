# Contributing to ActionGraph

ActionGraph is a framework ecosystem. Contributions should strengthen stable contracts and keep business-specific behavior in samples, adapters, or user code.

## Ground Rules

- Keep `actiongraph-core` free of Spring, JDBC, LLM provider, servlet, and logging implementation dependencies.
- Prefer interfaces and optional adapters over hard-coded production entrypoints.
- Do not add a new Gradle module without updating `docs/frameworkization/module-governance.md` and the component catalog tests.
- Do not change public API compatibility casually. Follow `STABLE_CONTRACT.md`.
- Do not commit secrets, raw PII, customer data, or production credentials.
- Keep sample-domain changes focused on defects, real or near-real pilot adaptation, and evidence collection.

## Local Verification

Run the full build before proposing a change:

```bash
./gradlew build --rerun-tasks
```

For Java 8 client compatibility, the build also verifies `actiongraph-control-plane-api` bytecode and a real Java 8 Maven consumer in CI.

When touching docs, module names, catalog metadata, or public composition profiles, run:

```bash
./gradlew :actiongraph-control-plane-api:test --rerun-tasks
```

When touching runtime execution, suspend/resume, compensation, policy, or trace behavior, include focused regression tests in the affected module and keep the full build green.

## Public API Work

For new or changed public APIs:

- add Javadoc that explains lifecycle, thread-safety, null handling, and failure semantics;
- prefer additive changes;
- add tests that use the API through its interface rather than only through the default implementation;
- update README or `docs/frameworkization/` when the user-facing contract changes;
- update `CHANGELOG.md` under `Unreleased`.

## Commit and Review Discipline

Use small commits with concrete messages. A useful change description should state:

- what contract or behavior changed;
- which tests were run;
- any compatibility impact;
- any migration guidance.

Code review should prioritize regressions, public API risk, security-sensitive behavior, missing tests, and documentation drift.
