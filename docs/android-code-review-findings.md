# Android Layer Code Review Findings

| Severity | Location | Issue Summary | Recommended Fix |
|----------|----------|---------------|-----------------|
| 🔴 Critical | `lib/core/service/file_service.dart:192-205` | Hardcoded `/storage/emulated/0/Download` path incompatible with Android 11+ scoped storage | Use `getExternalStorageDirectories()` exclusively; remove hardcoded path fallback |
| 🟡 Important | `lib/features/login/services/authentication_service.dart` | Biometric authentication does not fall back to device PIN when fingerprint sensor is unavailable or fails repeatedly | Add device credential fallback or use `canAuthenticateWithBiometrics()` check |
| 🟡 Important | `android/app/src/main/AndroidManifest.xml` | Missing `READ_EXTERNAL_STORAGE` permission; lacks runtime permission request for Android 10+ scoped storage | Add explicit READ/WRITE_EXTERNAL_STORAGE permissions in manifest and request at runtime |
| 🟡 Medium | `AndroidManifest.xml` intent-filters | Duplicate / redundant MIME-type entries (18+ `<data>` nodes for PDF, doc, etc.) that inflate APK size and complicate maintenance | Consolidate using wildcards `mime-type="*/*"` in primary SEND intent filter or use grouped MIME types |
| 🔴 Critical | `lib/features/login/services/authentication_service.dart` biometricOnly=true block on Android 10+ | Force-biometric-only mode breaks fallback to PIN/pattern. If fingerprint is dirty / sensor fails, the device does not permit the user to enter a backup credential | Set `biometricOnly=false` or implement manual PIN / lock screen passcode fallback path |
| 🟢 Minor | `AndroidManifest.xml` notification permission | Declares `POST_NOTIFICATIONS` but no runtime request at launch/API33+ | Request permission from `Permission.notification` using `permission_handler` during first launch. Provide graceful degrade if denied |

---

## 🔴 Critical: Hardcoded Download Directory Path

### File
```
lib/core/service/file_service.dart (lines ~192–205)
```

### Problem
The code hardcodes the external download directory:
```dart
var directory = Directory('/storage/emulated/0/Download');
```
On **Android 11+** (scoped storage), this path is inaccessible for apps that do not hold `MANAGE_EXTERNAL_STORAGE` permission. The fallback to `getExternalStorageDirectories(type: StorageDirectory.downloads)` only triggers **after** first checking the hard-coded path.

### Impact
- App silently loses write access on the majority of modern Android devices (10+)
- Users cannot download PDFs / files via the app after first launch
- Fallback logic never gets reached unless `directory.exists()` returns false, which still produces a broken path if scoped storage blocks writing

### Suggested fix
```dart
Future<void> _initDownloadsDirectory() async {
  if (Platform.isAndroid) {
    final dirs = await getExternalStorageDirectories(
      type: StorageDirectory.downloads,
    );
    _downloadsDirectory = Directory(dirs!.first.path + '/Paperless')
        .create(recursive: true);  // use app subfolder for scoped storage compliance
  } else if (Platform.isIOS) { ... }
}
```

---

## 🟡 Important: Biometric Authentication Missing PIN / Pattern Fallback

### File
```
lib/features/login/services/authentication_service.dart
```

### Problem
```dart
Future<boolean> authenticate(context, String? reason) => Future.delayed({
  Duration(seconds: 2s),
  async {
    if (await auth.canAuthenticateWithBiometrics()) {
      return await auth.authenticateWithBiometrics( ... );
    } else {
      // missing fallback path for device lock credentials
    }
  },
});
```

When `forceBiometricAuthOnly = true`, Android 10+ will *not* present a PIN, pattern, or password as an alternative. If the fingerprint sensor is dirty / disabled / fails, the user cannot authenticate at all.

### Impact
- Users with broken or unavailable fingerprint sensors are completely locked out after biometric failure
- Violates standard Android UX expectations where lock screen auth should be presented when biometrics fail

### Suggested fix
```dart
authenticate(context) => Future.delayed({
  Duration(seconds: 2s),
  async () {
    try {
      final canAuth = await auth.canAuthenticateWithBiometrics();
      if (canAuth) {
        return await auth.authenticateWithBiometrics(...);
      } else {
        // fall back to device credentials
        return await auth.authenticate(reason: reason, fallbackTitle: 'Use PIN');
      }
    } catch { 
      /* ... */
    }
  },
});
```

---

## 🔴 Critical — Missing Storage / Scoped-Storage Permissions

### File
```
android/app/src/main/AndroidManifest.xml
```

### Problem
The manifest declares `INTERNET` and `CAMERA` but **does not** declare `READ_EXTERNAL_STORAGE`:
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
```

On Android 10+ the app also must handle scoped storage correctly (no direct file reads outside app-specific paths). Additionally, on Android 33+, all permissions are granted at install or require runtime requests:
```dart
final status = await Permission.storage.request();
if (!status.isGranted) { /* ... notify user to grant manually or disable feature */ }
```

### Impact
- App silently fails when reading existing files from SD card / external storage on Android 10–12 devices before scoped storage APIs fully block direct access
- Crash at runtime on Android 33+ if the app attempts file I/O without `READ_EXTERNAL_STORAGE` granted

### Suggested fix
Add manifest entry:
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="29" />
```
And request at runtime for Android 33+:
```dart
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
  final status = await Permission.storage.request();
  if (!status.isGranted) /* notify */;
}
