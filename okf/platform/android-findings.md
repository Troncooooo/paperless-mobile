---
type: Reference
title: Android layer review findings & fix status
description: Code-review findings for the Android layer with fix status verified against code at HEAD.
tags: [android, platform, storage, biometrics, permissions]
generated: { by: piagent/okf, at: 2026-09-13T14:08:00Z }
sources:
  - id: findings
    resource: ../docs/android-code-review-findings.md
    title: Android Layer Code Review Findings
    last_modified: 2026-08-20
  - id: tasks
    resource: ../docs/improvement-tasks.md
    title: Improvement Tasks (Android layer)
    last_modified: 2026-08-20
  - id: manifest
    resource: ../android/app/src/main/AndroidManifest.xml
    title: AndroidManifest.xml
    last_modified: 2026-08-20
  - id: auth-svc
    resource: ../lib/features/login/services/authentication_service.dart
    title: authentication_service.dart
    last_modified: 2026-08-20
  - id: file-svc
    resource: ../lib/core/service/file_service.dart
    title: file_service.dart
    last_modified: 2026-08-20
---

Findings from the Android code review,[^findings] with status verified in
the working tree on 2026-08-25:

| Finding (severity) | Status at HEAD | Evidence |
|---|---|---|
| Hardcoded `/storage/emulated/0/Download` path (critical) | **Fixed** — `_initDownloadsDirectory` uses `getExternalStorageDirectories(type: StorageDirectory.downloads)`; no hardcoded literal remains in `file_service.dart`. | `file_service.dart:199–253`.[^file-svc] |
| Biometric login has no PIN/pattern fallback (critical) | **Fixed** — `authentication_service.dart` sets `biometricOnly: false` in both auth paths (lines 30, 54–67), and login capability is probed with local_auth 2.x's `isDeviceSupported()` (replacing the removed `canAuthenticate()` API, 2026-09-13). | `authentication_service.dart:13,31,55,70`.[^auth-svc] |
| Missing storage permissions / `POST_NOTIFICATIONS` not requested at runtime (important) | **Partially addressed** — manifest declares `READ_EXTERNAL_STORAGE` and `WRITE_EXTERNAL_STORAGE` (both `maxSdkVersion="32"`), `POST_NOTIFICATIONS`, `INTERNET`, `USE_BIOMETRIC`, `USE_FINGERPRINT`. Whether `POST_NOTIFICATIONS` is requested at runtime on API 33+ is not verified in this pass — see the question in the bundle log. | `AndroidManifest.xml:5–16`.[^manifest] |
| Duplicate intent-filter / MIME-type entries inflate the APK (medium) | **Not verified** — not inspected in this pass. | — |

The companion task list with acceptance criteria is
`docs/improvement-tasks.md` at the app root.[^tasks]

[^findings]: Android Layer Code Review Findings
[^tasks]: Improvement Tasks (Android layer)
[^manifest]: AndroidManifest.xml
[^auth-svc]: authentication_service.dart
[^file-svc]: file_service.dart
