# Bundle Update Log

## 2026-08-25

* **Update** (repo mode): populated the previously empty bundle from repository evidence; verified code state at HEAD before writing.
  * **Creation**: [Project overview](/architecture/overview.md), [TLS & custom CA](/security/tls-custom-ca.md), [HTTP exception mapping](/security/exception-mapping.md), [Android findings & fix status](/platform/android-findings.md), [Sprint 02 roadmap](/roadmap/sprint-02.md).
  * **Update**: root [index](/index.md) rebuilt with progressive-disclosure listings under the declared `okf_version: "0.2"`.
  * Verified in tree: ISS-1 strict-TLS fix + custom CA loader and tests present; hardcoded download path replaced by scoped-storage init; `biometricOnly: false` fallback in auth service; Sprint 02 deliverables NOT yet implemented (plan only).

## Questions for maintainers

* **Question** (2026-08-25): `AndroidManifest.xml` declares `POST_NOTIFICATIONS`, but no runtime permission request at API 33+ was located during review (searched the login and settings features). Is it requested elsewhere, or is it still open?
