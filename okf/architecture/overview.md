---
type: Reference
title: Project overview
description: Flutter client for self-hosted paperless-ngx — layout, stack, upstream fork, and local deployment context.
tags: [architecture, flutter, paperless-ngx]
generated: { by: piagent/okf, at: 2026-08-25T21:41:57Z }
sources:
  - id: readme
    resource: ../README.md
    title: paperless-mobile README
    last_modified: 2026-08-20
  - id: host-compose
    resource: ../../docker-compose.yml
    title: Host paperless-ngx docker-compose
  - id: host-agents
    resource: ../../AGENTS.md
    title: Host sandbox & agent guidelines
---

# What this is

A Flutter mobile client for paperless-ngx document management. The local
copy is a fork of the upstream `paperless-ngx/paperless-mobile` repository
(remote `upstream`: `https://github.com/paperless-ng/paperless-mobile.git`;
`origin`: `https://github.com/Troncooooo/paperless-mobile.git`), extended
with security hardening (see [TLS & custom CA](/security/tls-custom-ca.md)
and [exception mapping](/security/exception-mapping.md)) and local roadmap
work (see [Sprint 02](/roadmap/sprint-02.md)).

# Codebase layout (verified at HEAD)

| Area | Path | Notes |
|---|---|---|
| Security core | `lib/core/security/` | Session manager + custom CA loader (ISS-1 fix). |
| Feature screens | `lib/features/` | BLoC/Cubit per feature: `documents`, `document_search`, `document_upload`, `document_scan`, `labels`, `saved_view`, `tasks`, `login`, `settings`, and more. |
| API client | `lib/api/` | Generated models (`lib/api/models/`) including the exception hierarchy. |
| Exceptions | `lib/core/exception/` | `ServerMessageException` with sensitive-data sanitization. |
| Files/downloads | `lib/core/service/file_service.dart` | Scoped-storage compliant directory init. |
| Security tests | `test/core/security/` | CA loader and session-init tests. |

# Integration context

- Self-hosted backend: the parent repository runs paperless-ngx in Docker
  (`broker`/`db`/`webserver` services, web on port 8000, OCR `eng`,
  timezone `Europe/Amsterdam`).[^host-compose]
- Development loop per the README: `flutter analyze && flutter test --coverage`,
  with CI additionally running coverage checks and `bandit` on the Django side.[^readme]
- The host environment is a sandboxed Raspberry Pi; agents operate under the
  permission rules in the parent `AGENTS.md` (no `sudo`, no credential reads,
  network limited to `localhost`/`llm.home`).[^host-agents]

[^readme]: paperless-mobile README
[^host-compose]: Host paperless-ngx docker-compose
[^host-agents]: Host sandbox & agent guidelines
