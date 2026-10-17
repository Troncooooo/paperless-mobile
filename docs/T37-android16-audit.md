# T-37 — Android 16 install-requirement audit (req list + issue "missing targetSdkVersion")

Date: 2026-10-13 · source: owner's 6-item req list + a draft upstream issue claiming
the APK's AndroidManifest.xml is *missing* `targetSdkVersion`.
Method: every claim checked against the **actual shipped release asset**, not the issue text.

## Artifact audited (fetched fresh, hash-verified)
- asset: `paperless-mobile-troncooooo-release-arm64.apk`
- size: 47,231,855 B · sha256: `1621d5fa0f0ebf3951ca7f51e296f4055555abf0cb63c70f26e51d7ae59a4da8`
- parsed with `pyaxmlparser` (AXML decoder), not eyeballed.

### Real manifest values (ground truth)
| field | value |
|---|---|
| package | `de.astubenbord.paperless_mobile` |
| versionCode / versionName | 711 / 4.5.1 |
| compileSdkVersion (manifest header) | **36** ("16") |
| platformBuildVersionCode/Name | **36** / "16" |
| `uses-sdk` minSdkVersion | **24** |
| `uses-sdk` targetSdkVersion | **36** |
| maxSdkVersion | (none) |

→ **The issue's core claim is FALSE.** The manifest is *not* missing `targetsdkversion`;
it is `36`. The issue's "defaults back to API level 1 → Android 16 rejects" is also not
real Android behaviour (an absent targetSdk doesn't map to API 1). The draft reads as LLM
output and contradicts the delivered bytes.

## Requirement-by-requirement checklist
| # | Requirement | Verdict | Evidence |
|---|---|---|---|
| 1 | compileSdk=36, targetSdk=36, minSdk≥24 | ✅ **MET** | header=36; uses-sdk target=36, min=24. Nothing to change. |
| 2 | 64-bit only (arm64-v8a / x86_64) | ✅ **MET** for the 47 MB asset | its only ABI = `arm64-v8a`. ⚠️ Do NOT sideload the 84 MB `arm64-armv7` variant on a 64-bit device (it bundles `armeabi-v7a`; use the 47 MB one). |
| 3 | Predictive back / `enableOnBackInvokedCallback="true"` | ⚠️ **GAP (non-blocking)** | attr is **absent** in manifest. Not an install blocker — the Flutter engine handles back via OnBackPressedDispatcher, so it installs & runs. One-line manifest add + rebuild is the clean fix (cosmetic/robustness, required for *new* targetSdk≥34 apps to opt in). |
| 4 | Edge-to-edge + WindowInsets | ✅ **Not an install blocker** | targetSdk 35/36 enforce edge-to-edge at *runtime*; Flutter applies insets in `FlutterView`. Any layout overlap is a Dart-UI tweak, not a sideload failure. |
| 5 | Photo Picker / storage perms | ✅ **MET** | `READ/WRITE_EXTERNAL_STORAGE` both carry `android:maxSdkVersion="32"` → auto-inert on API 33+. Full perm list (INTERNET, POST_NOTIFICATIONS, USE_BIOMETRIC/FINGERPRINT, CAMERA, FLASHLIGHT, ACCESS_NETWORK_STATE, VIBRATE) — no legacy-only storage path remains. |
| 6 | Signing v3/v4, restricted settings | ✅ **v2 present → installs fine** | scheme = **v2** (no v1, no v3). Android 16 *sideload* accepts v2 (v1 is the one rejected; we have none). v3/v4 are mandatory for Play targeting 30+, not for sideload. Restrictive-perms rule doesn't apply (no Accessibility/NotificationListener perms). |

## Conclusion
- **Shipped APK is correctly Android-16-targeted (target=36, min=24, arm64-only, v2-signed,
  storage perms maxSdk-gated).** It is installable on a 64-bit Android 16 device.
- **No rebuild is required to make it install.**
- The draft upstream issue's stated root cause (missing targetSdkVersion) is **incorrect** for
  this artifact — do not post it as-is.

### Most likely real causes of the owner's install failure (ranked)
1. **Previously-failed install not removed** → signature `f00c2f55` clash → "App not installed". Uninstall first.
2. **Corrupt/incomplete download** (same class of problem as T-33) → re-pull + verify sha256 `1621d5fa…`.
3. **Installed the 84 MB `arm64-armv7` variant** by mistake → use the 47 MB `arm64` asset.

## Only genuine (optional) improvement if a rebuild is done
Add to `<application …>` in `android/app/src/main/AndroidManifest.xml`:
```xml
android:enableOnBackInvokedCallback="true"
```
(then rebuild — this is a runtime opt-in, not an install fix, and the current APK runs fine without it.)

## Rebuild feasibility on this box
- No local `flutter`/`dart`/`gradle`; build path is `dev/build-apk.sh` + `dev/Dockerfile.buildenv`
  (expects `/opt/flutter`, `/opt/jdk17` in a container). No buildenv docker image currently present.
- A rebuild is therefore a heavier task, and is **not needed** to unblock the install.
