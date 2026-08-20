# 🛠️ Improvement Tasks — Android Layer Fixes & Enhancements

Below is a prioritized task list addressing the critical shortcomings identified in the previous code review. Each task includes acceptance criteria and exact code locations to ensure zero ambiguity for implementers.

---

## 1. 🔴 Replace Hardcoded Download Directory Path with Scoped Storage Compliance
**Priority:** Critical | **Effort:** Small | **Affected File:** `lib/core/service/file_service.dart` (line ~192)
### Task Description
Replace the hardcoded `/storage/emulated/0/Download` path with a Scoped Storage compliant approach using standard Flutter APIs. Ensure fallback behavior is logged if external storage cannot be accessed.
### Acceptance Criteria
- [ ] Remove literal string `"/storage/emulated/0/Download"` entirely
- [ ] Rely on `getExternalStorageDirectories(type: StorageDirectory.downloads)` as the sole Android source for downloads
- [ ] Create an app-specific subdirectory (e.g., `/Paperless/`) to avoid permission denials on Android 13+
- [ ] Add `debugPrint` / error-level logging if external storage directories return null or fail creation

---

## 2. 🔴 Implement Biometric PIN / Device Credential Fallback
**Priority:** Critical | **Effort:** Medium | **Affected File:** `lib/features/login/services/authentication_service.dart`
### Task Description
Modify the biometric authentication flow so that when `forceBiometricAuthOnly` is true but fails, it gracefully falls back to a device lock screen credential (PIN/Pattern) or a custom password prompt within the app.
### Acceptance Criteria
- [ ] Add an explicit fallback flag `fallbackToDeviceCredentials = true` in the `AuthenticationOptions`
- [ ] If biometric sensor returns `AuthError.biometricNotAvailable`, present a secondary "Enter PIN" UI state
- [ ] Ensure standard Android lock screen auth sheet is triggered when fingerprint dies / dirty

---

## 3. 🔴 Request Runtime Storage Permissions for Android 10+ (API 29+) & Android 13+ (API 33+)
**Priority:** Critical | **Effort:** Medium | **Affected File:** `android/app/src/main/AndroidManifest.xml` + `lib/features/login/services/authentication_service.dart` or app entry point
### Task Description
The manifest currently lacks external storage permissions and handles Android version gating incorrectly. It also declares `POST_NOTIFICATIONS` but never requests it at runtime on API 33+. 
### Acceptance Criteria
- [ ] Add `<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />` and limit `<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29" />` to manifest
- [ ] Add `POST_NOTIFICATIONS` runtime request in app entry point (`lib/main.dart`) if API >= 33
- [ ] Implement graceful degradation (disable file sharing / notification features) if user denies storage permission twice

---

## 4. 🟡 Consolidate Redundant Intent Filters in AndroidManifest.xml
**Priority:** Minor | **Effor:** Small | **Affected File:** `android/app/src/main/AndroidManifest.xml`
### Task Description
There are 18+ duplicate intent filter blocks and redundant MIME type data entries (e.g., PDF, docx, ppt). This inflates APK size and complicates future updates. Merge the filters into a single consolidated block or use wildcards where appropriate.
### Acceptance Criteria
- [ ] Combine all `SEND` / `VIEW` intent filters with multiple `<data>` nodes into 2 consolidated blocks (one for document types, one for image types)
- [ ] Replace `android:mimeType="*"/*"` wildcard entries only if required for general file sharing
- [ ] Verify APK build size decreases or stays identical while all share intents work as expected on an actual Android device

---

## 5. 🟢 Add Error Logging & Safe Initialization Guards for Directory Initialization
**Priority:** Minor | **Effor:** Small | **Affected File:** `lib/core/service/file_service.dart` (line ~40)
### Task Description
If any internal directory initialization fails silently, the app continues with uninitialized paths, leading to runtime crashes. Add safe guards and explicit error logging to ensure every folder exists before the app processes documents.
### Acceptance Criteria
- [ ] Change `debugPrint("Could not initialize directories.")` to use an actual logger (`logger.f`) or fatal crash if critical dirs fail
- [ ] Wrap each `_init...()` call with an individual try/catch and throw a descriptive platform-specific exception on failure
- [ ] Provide a safe fallback directory path (e.g., `getApplicationDocumentsDirectory()`) when external specific paths are denied by OS

---

## 6. ⬜ Add Unit / Integration Tests for Scoped Storage & Share Intents
**Priority:** Medium | **Effort:** Medium | **Affected File:** `test/` directory (New file: `test/services/storage_service_test.dart`)
### Task Description
Write comprehensive unit tests mocking Android platform storage APIs and verify that the app handles denied external storage permissions, empty directories, and invalid share intents without crashing. Add integration test covering the image sharing intent flow through the new scoped storage logic.

---

### 📌 Developer Notes Before Implementation
1. Always check API level using `if (Platform.isAndroid && Platform.version.contains('Android 12'))` or equivalent SDK_INT checks when targeting specific behaviors.
2. Scoped Storage compliance requires **zero direct access to the global shared folders**. All files MUST live under app-specific paths (`getExternalStorageDirectory`) or use `DocumentFile` / `StorageAccessFramework`.
3. Ensure `permission_handler` is fully wired into the app lifecycle (e.g., inside a `Cubit` or `Bloc` during first app launch).

Would you like me to draft the actual code patches for any specific task from this list?

--- End of Improvement Tasks --- 