---
type: Security Architecture
title: TLS validation & custom CA loader (ISS-1)
description: Strict TLS validation with optional in-app custom CA trust anchor and system-truststore fallback.
tags: [security, tls, tls-certificates, self-hosted]
generated: { by: piagent/okf, at: 2026-09-13T14:05:00Z }
sources:
  - id: sec-arch
    resource: ../docs/internal/SECURITY-ARCHITECTURE.md
    title: Security Architecture & Custom CA Implementation
    last_modified: 2026-08-20
  - id: session-impl
    resource: ../lib/core/security/session_manager_impl.dart
    title: SessionManagerImpl (Dio HTTP client setup)
    last_modified: 2026-09-13
  - id: ca-lover
    resource: ../lib/core/security/custom_ca_loader.dart
    title: CustomCaLoader (+ SecureStorageCaStore)
    last_modified: 2026-09-13
  - id: ca-store
    resource: ../lib/core/security/i_ca_store.dart
    title: ICustomCaStore abstraction
    last_modified: 2026-08-20
  - id: ca-test
    resource: ../test/core/security/custom_ca_loader_test.dart
    title: CustomCaLoader unit tests (mock store injection)
    last_modified: 2026-09-13
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

**Loading contract (2026-09-13).** `CustomCaLoader` keeps a synchronous
`currentContext` cache so `SessionManagerImpl` can create its `Dio` without
async storage reads. `main()` calls `await CustomCaLoader.ensureLoaded()`
**before** the app builds (one secure-storage read at startup); the static
façade (`saveCustomCa` / `removeCustomCa`) re-activates the cache on change.
Accepted CA input is **base64** or PEM-decodable text, activated via
`SecurityContext.setTrustedCertificates` on a private temp file; malformed
CA fails back to the system trust store. `CustomCaLoader` accepts an
injected `ICustomCaStore`, defaulting to `SecureStorageCaStore`
(`flutter_secure_storage`).

There is no `badCertificateCallback` in the init path.

# Components

| File (relative to app root) | Role |
|---|---|
| `lib/core/security/session_manager_impl.dart` | Dio client init, CA injection, strict fallback. |
| `lib/core/security/custom_ca_loader.dart` | Loads/saves/removes the custom CA (base64 or PEM); `currentContext` cache + injectable store (`SecureStorageCaStore` default). |
| `lib/core/security/i_ca_store.dart` | `ICustomCaStore` interface (`readCA` / `saveCA` / `deleteCA`) so storage is mockable in tests. |
| `lib/features/settings/presentation/ui/custom_ca_picker_tile.dart` | Settings screen entry point for CA upload. |

Secure storage is `flutter_secure_storage` (`^9.2.4` in `pubspec.yaml`).[^pubspec]

# Testing

- `test/core/security/custom_ca_loader_test.dart` — mock
  `ICustomCaStore` injection via `CustomCaLoader(store)`; save/delete round
  trips; `getCustomContext` returns null for missing/malformed CA (fallback
  to system truststore) and reads exactly once for a stored CA.[^ca-test]
- `test/core/security/session_security_init_test.dart` — simulated
  "no CA → default context" and "stored CA → custom context injected"
  flows for the init path (ISS-1).[^ca-test]

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
[^ca-test]: Core security unit tests
[^cov-report]: Test Coverage Report (core security)
[^pubspec]: pubspec.yaml
