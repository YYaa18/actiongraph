# Goal Studio

Status: Operations, Experimental in `0.2.x`.

Goal Studio is a test-environment drafting surface for configuration-defined
Goals. It helps teams turn a business capability request into a validated Goal
bundle, then move that bundle through normal release controls.

It does not let the LLM generate a Plan. The LLM can only draft a Goal
declaration: type, description, target conditions, seed conditions, and
parameters. Planning, policy, review, compensation, and trace remain runtime
responsibilities.

## Enablement

Goal Studio is disabled by default.

```yaml
actiongraph:
  studio:
    enabled: true
    shared-secret: "${ACTIONGRAPH_STUDIO_SECRET}"
    source-env: test
    bundle-directory: build/actiongraph-studio-bundles
```

If any active Spring profile is `prod` or `production`, startup fails while
Studio is enabled. The default endpoint is:

```text
POST /actiongraph/studio/sessions
POST /actiongraph/studio/sessions/{id}/refine
POST /actiongraph/studio/sessions/{id}/approve
GET  /actiongraph/studio/sessions/{id}
```

All requests must include the configured token header. The default header is
`X-ActionGraph-Studio-Token`.

## Draft flow

1. The service sends the LLM a catalog of registered Actions: id, description,
   preconditions, effects, risk level, and human-review requirement.
2. The LLM returns a JSON Goal declaration.
3. ActionGraph validates reachability with the same graph validator used at
   startup.
4. If validation fails, diagnostics are fed back to the LLM for a bounded repair
   loop.
5. A valid draft returns a preview plan and risk profile.
6. Approval writes a bundle file with metadata and fingerprints.

Risk profile is computed by the framework, not the LLM. It lists each previewed
Action, its risk level, human-review flag, and whether the Action has no
description.

## Promotion discipline

The approved bundle should be reviewed like code:

- check that the Goal type matches naming conventions;
- check that target and seed conditions belong to the intended domain namespace;
- check that parameter names and types match the production API contract;
- keep the bundle under configuration management;
- deploy by setting `actiongraph.goals.bundle.locations`.

When the production Action graph changed after drafting, the default import
mode fails fast. Use `fingerprint-mismatch: WARN` only when the drift has been
reviewed and startup reachability validation still passes.
