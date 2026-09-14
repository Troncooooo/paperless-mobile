---
type: Playbook
title: Build & release pipeline — current state, pain points, and simplification plan
description: Evidence-backed record of how the Flutter APK build is done on the aarch64 Pi, what makes it fragile, and the decided direction for easier builds.
tags: [build, release, android, pipeline, qemu, flutter, ci]
status: draft
generated: { by: piagent/okf, at: 2026-09-14T03:40:00Z }
sources:
  - id: wave
    resource: ../../../dev/wave.sh
    title: wave.sh multi-wave build runner (host)
    last_modified: 2026-09-14
  - id: ci-upstream
    resource: .github/workflows/build_app.yml
    title: Upstream GitHub Actions build workflow
    last_modified: 2026-08-20
  - id: fvm
    resource: .fvm/fvm_config.json
    title: fvm pinned Flutter version
    last_modified: 2026-08-20
  - id: l10n
    resource: l10n.yaml
    title: l10n generation config
    last_modified: 2026-08-20
  - id: manifest
    resource: android/app/src/main/AndroidManifest.xml
    title: AndroidManifest.xml
    last_modified: 2026-08-20
  - id: pubspec
    resource: pubspec.yaml
    title: pubspec.yaml
    last_modified: 2026-08-20
---

# Current (working) setup — 2026-09-14

APK builds run inside an emulated `linux/amd64` Docker container on an
aarch64 Raspberry Pi 4 (4 cores, 8GB RAM, ~100GB SSD):

- **Emulation:** QEMU `x86_64-static` registered via `binfmt_misc`, wrapper sets
  `-L /home/piagent/dev/x64root` (x86_64 rootfs with dynamic loader).
  **The interpreter path must exist inside every container** — each
  `docker run` must bind-mount the QEMU binary at its host path, or all
  emulated execs fail with `no such file or directory`.
- **Toolchain in container:** `/opt/flutter` (3.41.9 — **not** the fvm-pinned
  3.35.4, see question below), `/opt/jdk17` (17.0.20), `/opt/android`
  (platform 36, build-tools 35.0.0, NDK 28.2.13676358, CMake).
- **Maven:** local HTTP repo at `127.0.0.1:8080` (host) — emulated TLS from
  the JVM is unreliable, and the host blocks direct access to
  `download.flutter.io`, `google`, `mavenCentral` for some artifacts.
  `fetch_missing.py` / `closure_fetch.py` (host) parse Gradle "missing
  artifact" messages and pre-stage them into the local repo.
- **Generated files:** `.freezed.dart` + `.g.dart` are produced by
  `dart run build_runner build` (freezed 3.2.3, json_serializable 6.11.1)
  from the app's Dart sources; **must run in the same environment as the
  build**, and **must not run with `--build-filter` after
  `--delete-conflicting-outputs`** — that combination silently deleted all
  generated files and regenerated nothing (observed 2026-09-14).
- **Codegen must run in the app's own build environment**, not a
  different-architecture one: a `build_runner build --build-filter` run
  without the app's resolved dependency graph will skip codegen and report
  0 outputs.

# Pain points (evidence, 2026-09-13/14 sessions)

| Pain | Root cause | Impact |
|---|---|---|
| `exec ...: no such file or directory` for every emulated binary | QEMU binfmt interpreter path absent in the container's view | Total build failure (hours of misdiagnosis) |
| `Could not open '/lib64/ld-linux-x86-64.so.2'` | Dynamic x86_64 binaries need the wrapper `-L x64root`, raw QEMU does not | Total build failure |
| Gradle "missing artifact" 404s persisting after files staged | Root-owned negative metadata cache (`metadata-2.107`) | Waves wasted on re-fetch loops |
| 238 generated files vanished between waves | `build_runner build --delete-conflicting-outputs --build-filter=lib/` in one wave, then a later wave skipped/wiped codegen | Rebuild of codegen step from scratch each diagnosis |
| R8/dex "not find aapt2" class of errors | aapt2 binary not in local repo (Google CDN blocked) | Blocked `:app:buildReleaseResources` |
| Two concurrent build runners racing | Two `wave.sh` instances launched back-to-back during a restart | Resource contention, lock timeout |
| Disk pressure (100GB volume, ~15GB free at peak) | Emulated Docker layers + pub cache + gradle cache + local repo | Slow I/O, OOM risk on R8 |

# Simplification plan (decided 2026-09-14)

1. **A (immediate):** finish the one required APK build (4.5.0+710) on the
   Pi via the existing wave runner, then **stop treating the Pi as a
   builder**. The Pi stays a dev/verification machine.
2. **B (permanent, recommended):** move CI builds to **GitHub Actions on a
   fork of upstream** — the upstream repo already ships
   `build_app.yml`[^ci-upstream], so the pattern exists; only need to
   align the workflow to this fork's hardening changes and add an APK
   artifact step. Native x86_64, ~10 min, no emulation.
3. **C (fallback):** any x86_64 Linux machine (laptop / hourly VM) running
   `fvm use` + `flutter build apk` natively — 2 commands, ~10 min.
4. **D (if Pi must keep building):** bake the working toolchain into one
   reused Docker image + one *named persistent* container (stable Gradle
   daemon, no repeated apt/git/maven wiring), commit or skip-regenerate
   generated files, align Flutter to the fvm-pinned 3.35.4, and run
   exactly one build at a time.

# PR plan (upstream)

Split by concern; do not mix:

1. **Security hardening (ISS-1 strict-TLS + custom CA loader)** —
   self-contained, testable, the strongest upstream candidate.
2. **Compat fixes** (local_auth 2.x API, `switch` exhaustiveness) —
   separate PR, low risk, depends on pubspec versions.
3. **Do not PR** the Pi build machinery (localrepo, wave.sh, gradle
   repo-injection) — local infra, not app code; if it has upstream value
   it's an *issue* describing the ARM-emulated build recipe.

Before filing: diff against upstream `main` (they may already have fixed
the local_auth and `switch` issues since 4.5.0), and verify against the
fvm-pinned SDK.

# Open questions (for maintainers)

* **Flutter SDK version:** `.fvm/fvm_config.json` pins 3.35.4; current build
  uses 3.41.9. Which should CI target, and does upstream's `build_app.yml`
  already pin a version we should match?[^fvm]
* **Signing:** release builds currently fall back to the debug keystore (no
  `key.properties`). Is there an intended release-signing flow to adopt
  (upstream workflow, or Play App Signing)?

[^ci-upstream]: Upstream GitHub Actions build workflow
[^fvm]: fvm pinned Flutter version
[^wave]: wave.sh multi-wave build runner (host)
[^manifest]: AndroidManifest.xml
[^pubspec]: pubspec.yaml
[^l10n]: l10n generation config
