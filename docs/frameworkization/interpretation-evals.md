# Interpretation Evaluation Flywheel

ActionGraph treats goal interpretation quality as a quality-engineering problem,
not as audit trace data. The interpreter output is structured, so evaluations
use exact field assertions rather than LLM-as-judge scoring.

## Golden Sets

Each domain owns its golden set beside domain code. The framework provides the
JSONL format and runner; the domain team owns the truth data.

```jsonl
{"input":"Prepare renewal quote for C001","expect":{"goalType":"prepareRenewalQuote","parameters":{"customerId":"C001"}}}
{"input":"Prepare renewal quote","expect":{"goalType":"prepareRenewalQuote","clarification":true,"missingFields":["customerId"]}}
{"input":"查一下今天的天气","expect":{"unknownGoal":true,"clarification":true,"missingFields":["supportedGoal"]}}
```

Rules of thumb:

- deterministic interpreters should use `Thresholds.exact()`;
- LLM interpreters should use env-gated tests and explicit thresholds;
- ready, clarification, unknown-goal, and local language variants should all be
  represented;
- production samples promoted into the golden set must be human-labeled first.

## Running Evaluations

```java
GoldenSetAssertions.assertMeets(
        interpreter,
        Path.of("src/test/resources/golden/renewal-golden.jsonl"),
        Thresholds.exact()
);
```

The runner writes a markdown report to:

```text
build/reports/actiongraph/evals/<golden-set-file>.md
```

That file is suitable for CI artifacts or DHK evidence capture:

```bash
./gradlew :actiongraph-samples:test --tests '*RenewalGoldenSetEvalTest'
```

## Production Sampling

Production sampling is opt-in:

```properties
actiongraph.interpretation.metrics=true
actiongraph.interpretation.sampling.rate=0.05
```

Metrics go through `ObservationSink`, so Micrometer and OpenTelemetry exporters
from the observability SPI can consume the same events. Interpretation spans use
`gen_ai.operation.name=agent.interpretation`.

Samples go to `InterpretationSampleRepository` instead of trace. Inputs are
masked with `DataMaskingPolicy` before storage. If the interpretation starts a
run, the sample stores the run id for later join; the audit trace remains clean.

## Review Loop

1. Enable a low sampling rate in a pilot environment.
2. Review recent samples from the repository.
3. Mark reviewed rows with `markLabeled`.
4. Promote representative labeled samples into the domain golden set.
5. Tighten thresholds only after the golden set reflects real traffic.

The loop keeps quality evidence close to the domain while preserving the runtime
trace as execution audit, not model-quality bookkeeping.
