# T-05 — CI-built APK evidence

- APK: `/home/piagent/projects/archive_letters/paperless-mobile/dist/app-release-arm64.apk`
- Size: 135814271 bytes
- SHA256: `86f3ed1cfa1d89d9b986a773d6706cfe99b92a0c462dce54d6e5e71095342461`
- Built from: branch `wip/T-03-agent-crop` @ `cdb8e9a1` (includes T-03 crop prototype: vendored `edge_detection`, `CropMath.kt`, `PaperRectangle.kt`)
- CI: https://github.com/Troncooooo/paperless-mobile/actions/runs/36719256082 (Build Release APK, pull_request, completed/success)
- Artifact: `paperless-mobile-release` (id 11099145566), APK was `build/app/outputs/flutter-apk/app-release.apk`
- Signing: release keystore provisioned from committed `signing/ci-release.keystore` (stable CI identity); APK Sig Scheme v2+ block verified present
- Trigger: fork PR #1 (wip/T-03-agent-crop → main) since workflow fires on push/PR to main or manual dispatch
- Date: 2026-09-30
