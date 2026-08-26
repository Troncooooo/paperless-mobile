---
okf_version: "0.2"
---

# Open Knowledge Bundle

Curated knowledge about the local paperless-mobile fork, its security
hardening, platform state, and roadmap, in the Open Knowledge Format (OKF).

## Architecture

* [Project overview](/architecture/overview.md) — Flutter client for self-hosted paperless-ngx: layout, stack, upstream fork, and local deployment context.

## Security

* [TLS validation & custom CA loader (ISS-1)](/security/tls-custom-ca.md) — strict TLS validation with optional in-app custom CA trust anchor and system-truststore fallback.
* [HTTP exception mapping & data sanitization](/security/exception-mapping.md) — client-side exception hierarchy and ServerMessageException sanitization rules (NFR-04, AC-04/05).

## Platform

* [Android layer review findings & fix status](/platform/android-findings.md) — code-review findings for the Android layer with fix status verified against code at HEAD.

## Roadmap

* [Sprint 02: mobile search & document viewer](/roadmap/sprint-02.md) — roadmap for advanced search filters, viewer overhaul, and a local OCR cache (T-05…T-07).

## Log

* [Bundle update log & questions for maintainers](/log.md)
