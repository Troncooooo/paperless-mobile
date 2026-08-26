---
type: Security Architecture
title: TLS validation & custom CA loader (ISS-1)
description: Strict TLS validation with optional in-app custom CA trust anchor and system-truststore fallback.
tags: [security, tls, tls-certificates, self-hosted]
generated: { by: piagent/okf, at: 2026-08-25T21:41:57Z }
sources:
  - id: sec-arch
    resource: ../docs/internal/SECURITY-ARCHITECTURE.md
    title: Security Architecture & Custom CA Implementation
    last_modified: 2026-08-20
  - id: session-impl
    resource: ../lib/core/security/session_manager_impl.dart
    title: SessionManagerImpl (Dio HTTP client setup)
    last_modified: 2026-08-20
  - id: ca-store
    resource: ../lib/core/security/i_ca_store.dart
    title: ICustomCaStore abstraction
    last_modified: 2026-08-20
  - id: cov-report
    resource: ../docs/internal/test-coverage-report.md
    title: Test Coverage Report (core security)
    last_modified: 2026-08-20
  - id: pubspec
    resource: ../pubspec.yaml
    title: pubspec.yaml
    last_modified: 2026-04-16
---

# Design

The app previously disabled TLS validation with an unconditional
`badCertificateCallback` (`(cert, host, port) => true`), an MITM hole flagged
as ISS-1.[^sec-arch] That bypass is removed. `SessionManagerImpl` now:

1. Checks whether the user stored a custom CA (`CustomCaLoader`).
2. If yes: builds `HttpClient(context: SecurityContext)` from the stored CA
   and wraps it in `IOHttpClientAdapter`.
3. If no, or if loading the stored CA fails: uses a plain `HttpClient()`
   — i.e. the OS default trust store — and logs the fallback.

There is no `badCertificateCallback` in the init path.

# Components

| File (relative to app root) | Role |
|---|---|
| `lib/core/security/session_manager_impl.dart` | Dio client init, CA injection, strict fallback. |
| `lib/core/security/custom_ca_loader.dart` | Loads/saves/removes the custom CA PEM. |
| `lib/core/security/i_ca_store.dart` | `ICustomCaStore` interface (`readCA` / `saveCA` / `deleteCA`) so storage is mockable in tests. |
| `lib/features/settings/presentation/ui/custom_ca_picker_tile.dart` | Settings screen entry point for CA upload. |

Secure storage is `flutter_secure_storage` (`^9.2.4` in `pubspec.yaml`).[^pubspec]

# Testing

- `test/core/security/custom_ca_loader_test.dart` — valid and malformed PEM
  handling; malformed input fails back to the system truststore rather than
  crashing or bypassing SSL.[^cov-report]
- `test/core/security/session_security_init_test.dart` — verified that with no
  custom CA present, the client uses the default OS trust store (ISS-1
  compliance), and that a stored CA is injected into the active Dio session.[^cov-report]

# Using it (self-hosted internal CAs)

Per the security docs:[^sec-arch]

1. Export the internal CA as PEM (`.pem` / `.crt` / `.cer`).
2. App: Settings → Security → **Upload Custom CA**.
3. Subsequent REST calls trust the internal chain.

See the [project overview](/architecture/overview.md) for the host
deployment this targets.

[^sec-arch]: Security Architecture & Custom CA Implementation
[^session-impl]: SessionManagerImpl (Dio HTTP client setup)
[^ca-store]: ICustomCaStore abstraction
[^cov-report]: Test Coverage Report (core security)
[^pubspec]: pubspec.yaml
