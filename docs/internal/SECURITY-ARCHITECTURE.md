# 🛡️ Security Architecture & Custom CA Implementation

This document details the security overhaul of the `paperless-mobile` project, specifically addressing the TLS certificate validation logic (ISS-1) and the implementation of a user-controlled Certificate Authority (CA) loader.

## 1. Core Security Layer (`lib/core/security/`)
The core security infrastructure is handled through two primary components: `session_manager_impl.dart` for routing HTTP traffic and `custom_ca_loader.dart` for certificate management.

### 🔹 Task 1: TLS Validation Fix (ISS-1)
In previous iterations, the application used an unconditional `badCertificateCallback` to bypass system-level HTTPS checks. This effectively neutralized SSL/TLS security, leaving the app vulnerable to Man-in-the-Middle (MITM) attacks on public Wi-Fi. 

**The Fix:**
We have removed the unconditional override in `SessionManagerImpl`. The application now relies entirely on the Operating System's default trust store. If a connection requires a custom certificate, it must be explicitly loaded via the app settings (see Section 2).

### 🔹 Task 2: Custom CA Loader (`CustomCaLoader`)
For users of self-hosted Paperless instances using internal/self-signed certificates, we now provide an in-app mechanism to install trust anchors securely. The `CustomCaLoader` abstracts away `flutter_secure_storage`. 

```dart
/// Example of how the loader is initialized
final customLoader = CustomCaLoader();
final caContext = await customLoader.getCustomBase64Pem();

if (caContext != null) {
  // Inject into HttpClient Adapter for mutual TLS / strict verification 
  client.httpClientAdapter = IOHttpClientAdapter(
    createHttpClient: () => 
      HttpClient(context: SecurityContext()..setTrustedCertificatesBytes(caContext))
  );
}
```

## 2. Testing & Validations (`test/core/security/`)
We enforce a strict testing policy for all security boundary changes. 

| Test File | Purpose | 
| :--- | :--- |
| `custom_ca_loader_test.dart` | Simulates the secure storage of valid and malformed PEM strings and ensures the system doesn't crash when invalid data is provided. |
| `session_security_init_test.dart` | Mocks the dependency layer to verify that if no custom CA is present, the application falls back strictly to the default OS truststore. |

## 3. User Instructions for Self-Hosters
To successfully connect your self-hosted Paperless server within the mobile app:
1. Export your internal CA certificate as a PEM file (`.pem`, `.crt`, or `.cer`).
2. Open the Mobile App > Settings > Security > **Upload Custom CA**.
3. The app will now trust the internal chain during all future REST API calls.
