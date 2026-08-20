<!-- Pull Request: Android Storage & Security Hardening -->

## Summary of Changes

This PR resolves all critical and important security/compatibility shortcomings identified in the Android layer code review. It addresses Android 10+ scoped storage limitations, missing biometric fallback paths, permission handling on API 33+, and initialization safeguards.

### 🔥 Critical Fixes
- **[Task 1]** Eliminated hardcoded `/storage/emulated/0/Download` path. Now leverages `getExternalStorageDirectories()` exclusively to ensure full compatibility with Android 10–14 scoped storage rules.
- **[Task 2]** Implemented biometric authentication fallback (device PIN / pattern) when fingerprints fail or sensors are unavailable, preventing user lockouts on modern Android devices.
- **[Task 3]** Added explicit `READ_EXTERNAL_STORAGE` permissions and runtime permission request logic for API 10+ (storage) and API 33+ (`POST_NOTIFICATIONS`).

### 🟡 Medium & Minor Enhancements
- Implemented graceful error logging / crash guards during directory initialization.
- Reduced APK bloat risk by simplifying intent filters in `AndroidManifest.xml`.

### ✍️ Developer Notes for Reviewers
- See `docs/android-code-review-findings.md` and `docs/improvement-tasks.md` for the full list of prior issues and their acceptance criteria.
- Manual QA is required on a real Android device (API 29, 30, 31, 33+) to validate scoped storage behavior during app share / import flows.

## Checklist
- [x] Resolves all 🔴 Critical findings from the review.
- [ ] Unit / Integration tests written (pending QA verification on a physical Android device).
- [ ] Updated APK size validated post-consolidation of `<intent-filter>` blocks.
