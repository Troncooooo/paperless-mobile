---
type: Reference
title: HTTP exception mapping & data sanitization
description: Client-side exception hierarchy and ServerMessageException sanitization rules (NFR-04, AC-04/05).
tags: [api, exceptions, security, sanitization]
generated: { by: piagent/okf, at: 2026-08-25T21:41:57Z }
sources:
  - id: sm-ex
    resource: ../lib/core/exception/server_message_exception.dart
    title: ServerMessageException with sanitization
    last_modified: 2026-08-21
  - id: spec-02
    resource: ../../docs/specifications/02-http-exception-mapping/04-specification.md
    title: HTTP exception mapping specification
    last_modified: 2026-08-20
  - id: api-ex
    resource: ../lib/api/models/exception/paperless_server_message_exception.dart
    title: PaperlessServerMessageException (API model)
---

# Exception hierarchy

Exception types live in `lib/api/models/exception/`:

- `PaperlessApiException` (`implements Exception`) — base.
- `PaperlessFormValidationException`
- `PaperlessServerMessageException`
- `PaperlessUnauthorizedException`
- `exception.dart` barrel export

# ServerMessageException semantics

Defined in `lib/core/exception/server_message_exception.dart` (implemented
by commit `30c08e69`, 2026-08-21), per the specification in the parent
repo.[^spec-02]

**Fields.** `message` (required), `statusCode`, `requestPath`,
`originalBody` — HTTP metadata kept for the debugging path.

**Sanitization on storage** (`_sanitizeBody`, referenced by AC-04 /
NFR-04 in the type's dartdoc):

- Maps: any entry whose key is in `{token, authorization, password}` is
  dropped.
- Lists: sanitized element-wise, recursively.
- Strings: truncated to 8192 chars (plus `...`) (OQ-04); a base64-like
  string longer than 512 chars (likely a token) is replaced with `null`.

**`toString()` formatting** — AC-04 / AC-05 / AC-13 per the dartdoc:
when `statusCode` or `requestPath` is present it renders as
`[HTTP <code> — <path>]: <message>` (null parts omitted); when both are
null the bare message is returned.

Relationships: exceptions surface during API calls configured by the
[TLS & custom CA layer](/security/tls-custom-ca.md); the
[project overview](/architecture/overview.md) maps the surrounding layout.

[^sm-ex]: ServerMessageException with sanitization
[^spec-02]: HTTP exception mapping specification
[^api-ex]: PaperlessServerMessageException (API model)
