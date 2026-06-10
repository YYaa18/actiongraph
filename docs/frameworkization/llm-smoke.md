# Real LLM Smoke Test

The normal CI workflow does not call DeepSeek. It runs without `DEEPSEEK_API_KEY`, so `DeepSeekRealLlmSmokeTest` remains gated and skipped.

Use the manual `DeepSeek Smoke` GitHub Actions workflow when you need to prove the real LLM path is working.

## Repository Setup

Configure this repository secret:

- `DEEPSEEK_API_KEY`: DeepSeek-compatible API key

Optional workflow inputs:

- `model`: overrides `DEEPSEEK_MODEL`
- `base-url`: overrides `DEEPSEEK_BASE_URL`

Do not commit API keys or paste them into workflow files. Keep them in GitHub Secrets or a local secret manager.

## What It Runs

```bash
./gradlew :actiongraph-samples:test \
  --tests com.actiongraph.llm.DeepSeekRealLlmSmokeTest \
  --no-daemon \
  --stacktrace
```

The workflow fails fast when `DEEPSEEK_API_KEY` is not configured, instead of letting the JUnit environment gate skip the test and produce a false green.

## Expected Evidence

The smoke test asks the renewal interpreter to understand a Chinese renewal request and checks that the LLM-backed interpretation is ready, extracts `customerId=C001`, and maps to `prepareRenewalQuote`.

Passing this workflow proves only the external LLM interpretation path is reachable. It does not prove business execution, planner safety, compensation, or JDBC persistence; those stay covered by the normal CI build.
