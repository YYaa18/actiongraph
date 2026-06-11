# Security Policy

ActionGraph is intended for governed business automation. Security reports must be handled privately and with enough evidence for maintainers to reproduce the issue.

## Supported Versions

| Version | Supported |
|---|---|
| `main` | Security fixes are accepted while the project is pre-1.0. |
| Released `0.x` artifacts | Best-effort fixes until the 1.0 compatibility policy is finalized. |
| `1.x` and later | Supported according to `STABLE_CONTRACT.md` once 1.0 is released. |

## Reporting a Vulnerability

Do not open a public issue for suspected vulnerabilities.

Use GitHub private vulnerability reporting when it is available for the repository. If it is not available, contact the repository owner through a private channel and include:

- affected module and version or commit SHA;
- reproduction steps or a minimal failing test;
- impact assessment, including data exposure or privilege escalation risk;
- whether credentials, PII, or customer data are involved;
- any proposed fix or mitigation.

Maintainers should acknowledge a valid private report within 5 business days, triage severity, and coordinate disclosure after a fix or mitigation is available.

## Scope

Security-sensitive areas include:

- runtime guards and permission policies;
- human-review authorization and callback token handling;
- suspended-run serialization and Blackboard type allowlists;
- JDBC persistence and trace export;
- data masking and audit-chain verification;
- Java 8 control-plane HTTP clients and shared-secret token verification.

## Secrets and Test Data

Do not commit API keys, production endpoints, source credentials, raw PII, or customer data. Use sanitized fixtures and environment variables. Real LLM smoke tests must be opt-in and use repository secrets.
