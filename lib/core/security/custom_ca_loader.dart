import 'dart:io';
import 'package:flutter/services.dart' show ByteData;
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:path_provider/path_provider.dart';

/// Handles secure storage and loading of custom CA certificates for the app.
class CustomCaLoader {
  static const _secureStorage = FlutterSecureStorage();
  static const String _caCertKey = '_paperless_custom_ca_pem';

  /// Loads a certificate from a local file path into an [X509Certificate].
  static Future<X509Certificate> loadCaFromPath(String filePath) async {
    return X509Certificate.fromFile(filePath);
  }

  /// Reads the custom CA and creates a [SecurityContext] configured for mutual TLS or strict SSL.
  static Future<SecurityContext?> getCustomContext() async {
    final pem = await _secureStorage.read(key: _caCertKey);
    if (pem == null || pem.isEmpty) return null;

    try {
      // Create a temporary file to pass to SecurityContext.setTrustedCertificatesBytes
      final dir = await getTemporaryDirectory();
      final tempFile = await File('${dir.path}/loaded_custom_ca.pem').create();
      await tempFile.writeAsString(pem);
      
      final context = SecurityContext()
        ..setTrustedCertificatesBytes(await tempFile.readAsBytes());
        
      await tempFile.delete(); // Clean up temporary file
      return context;
    } catch (e) {
      // Return null so the app falls back to standard OS trust store gracefully.
      return null;
    }
  }

  /// Stores a base64-encoded certificate (from Flutter's file picker or byte buffer).
  static Future<void> saveCustomCa(String b64Cert) async {
    await _secureStorage.write(key: _caCertKey, value: b64Cert);
  }

  /// Removes the custom CA and falls back to system defaults.
  static Future<void> removeCustomCa() async {
    await _secureStorage.delete(key: _caCertKey);
  }

  /// Clears all secure data stored by the security subsystem.
  static Future<void> clearAllSecurityData() async {
    await _secureStorage.deleteAll();
  }
}
