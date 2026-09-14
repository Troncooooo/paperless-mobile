# Bundle Update Log

## 2026-09-14

* **Creation**: [Build & release pipeline](/platform/build-pipeline.md) — evidence-backed playbook capturing the ARM-Pi emulated build setup, the session-observed pain points, the A→B simplification plan (finish on Pi, then GitHub Actions on a fork), and the upstream PR scoping (security hardening + compat, not build machinery). Marked `draft` with two open SDK/signing questions for maintainers.

## 2026-09-13

* **Update** (repo mode): re-verified claims against current code; refreshed two concepts.
  * [TLS & custom CA](/security/tls-custom-ca.md): recorded the 2026-09-13 `CustomCaLoader` rework — `currentContext` cache, `main()` preload (`ensureLoaded`), base64/PEM input via temp-file `setTrustedCertificates`, and injectable `ICustomCaStore` (default `SecureStorageCaStore`);
    corrected the testing section to the mock-injection design the tests actually use.
  * [Android findings](/platform/android-findings.md): biometrics row now cites local_auth 2.x `isDeviceSupported()` probe at current line numbers (re-verified `biometricOnly: false` still present).
  * Evidence: `custom_ca_loader.dart`, `custom_ca_loader_test.dart`, `session_security_init_test.dart`, `authentication_service.dart` at working tree 2026-09-13.

## 2026-08-25

* **Update** (repo mode): populated the previously empty bundle from repository evidence; verified code state at HEAD before writing.
  * **Creation**: [Project overview](/architecture/overview.md), [TLS & custom CA](/security/tls-custom-ca.md), [HTTP exception mapping](/security/exception-mapping.md), [Android findings & fix status](/platform/android-findings.md), [Sprint 02 roadmap](/roadmap/sprint-02.md).
  * **Update**: root [index](/index.md) rebuilt with progressive-disclosure listings under the declared `okf_version: "0.2"`.
  * Verified in tree: ISS-1 strict-TLS fix + custom CA loader and tests present; hardcoded download path replaced by scoped-storage init; `biometricOnly: false` fallback in auth service; Sprint 02 deliverables NOT yet implemented (plan only).

## Questions for maintainers

* **Question** (2026-08-25): `AndroidManifest.xml` declares `POST_NOTIFICATIONS`, but no runtime permission request at API 33+ was located during review (searched the login and settings features). Is it requested elsewhere, or is it still open?
