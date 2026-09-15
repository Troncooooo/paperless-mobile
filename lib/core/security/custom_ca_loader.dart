import 'dart:convert';
import 'dart:io' show Directory, File, SecurityContext;

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'i_ca_store.dart';

/// Loads an optional user-supplied custom CA (base64 **or** PEM) from
/// [ICustomCaStore] into a [SecurityContext] — via a temp file and
/// [SecurityContext.setTrustedCertificates] (ISS-1 compliant, no
/// [SecurityContext.badCertificateCallback]).
///
/// **Instance API** (injectable store, used by tests):
///   - [saveCA] / [removeCA] / [getSecurityContext]
///
/// **Static production façade** (used by [main], the session manager and the
/// settings tiles):
///   - [ensureLoaded] — call once during app startup;
///   - [currentContext] — the synchronously-resolved context (or null);
///   - [saveCustomCa] / [removeCustomCa] / [clearAllSecurityData].
class CustomCaLoader {
  CustomCaLoader([ICustomCaStore? store]) : _store = store ?? SecureStorageCaStore();

  final ICustomCaStore _store;

  static final CustomCaLoader _shared = CustomCaLoader();

  static SecurityContext? _activeContext;
  static bool _loaded = false;

  // ───────────────────────── static production façade ─────────────────────

  /// Synchronously accessible resolved [SecurityContext] (null when no custom
  /// CA is configured).
  static SecurityContext? get currentContext => _activeContext;

  /// Whether a custom CA is currently trusted in [currentContext].
  static bool get hasCustomCa => _activeContext != null;

  /// Reads the stored CA (base64 **or** PEM) from the default store and
  /// resolves the production [SecurityContext] once.
  static Future<void> ensureLoaded() async {
    if (!_loaded) {
      final b64OrPem = await _shared._store.readCA();
      _activeContext = (b64OrPem == null || b64OrPem.trim().isEmpty)
          ? null
          : _buildContext(b64OrPem);
      _loaded = true;
    }
  }

  /// Persists a new custom CA (base64 or PEM) and immediately re-trusts it.
  static Future<void> saveCustomCa(String b64Cert) async {
    await _shared.saveCA(b64Cert);
    await _refreshActive(b64Cert);
  }

  /// Removes the stored custom CA and restores system trust.
  static Future<void> removeCustomCa() async {
    await _shared.removeCA();
    _activeContext = null;
  }

  @visibleForTesting
  static Future<void> clearAllSecurityData() => removeCustomCa();

  static Future<void> _refreshActive(String b64OrPem) async {
    final trimmed = (b64OrPem ?? '').trim();
    if (trimmed.isEmpty) {
      _activeContext = null;
      return;
    }
    _activeContext = _buildContext(trimmed);
  }

  // ───────────────────────── instance API (testing) ───────────────────────

  /// Persists base64 (or PEM) CA material via [storage] (or the instance
  /// default store).
  Future<void> saveCA(String b64Cert, {ICustomCaStore? storage}) async {
    await (storage ?? _store).saveCA(b64Cert);
  }

  /// Deletes stored CA material via [storage] (or the instance default store).
  Future<void> removeCA({ICustomCaStore? storage}) async {
    await (storage ?? _store).deleteCA();
  }

  /// Reads CA material via [store] (or the instance default store) and builds
  /// a fresh [SecurityContext] trusting it. Returns null when nothing is
  /// stored.
  Future<SecurityContext?> getSecurityContext([ICustomCaStore? store]) async {
    final b64OrPem = await (store ?? _store).readCA();
    if (b64OrPem == null || b64OrPem.trim().isEmpty) {
      return null;
    }
    return _buildContext(b64OrPem);
  }

  /// Builds a [SecurityContext] trusting the supplied CA material.
  ///
  /// Accepts either base64 **or** PEM (auto-detected). Dart's
  /// [SecurityContext] has no in-memory `setTrustedCertificates(List<int>)`,
  /// so this writes the PEM to a temp file and uses
  /// [SecurityContext.setTrustedCertificates].
  static SecurityContext _buildContext(String b64OrPem) {
    String pem = b64OrPem.trim();
    // Auto-detect: if it is not already PEM, decode base64 → PEM.
    if (!pem.startsWith('-----BEGIN')) {
      try {
        final decoded = utf8.decode(base64Decode(pem));
        if (decoded.contains('-----BEGIN')) {
          pem = decoded;
        }
      } catch (e) {
        debugPrint('CustomCaLoader: failed to decode base64 CA: $e');
      }
    }
    if (!pem.contains('-----BEGIN')) {
      throw const FormatException(
        'CA material is neither base64 nor PEM encoded',
      );
    }

    final ctx = SecurityContext();
    final dir = Directory.systemTemp.createTempSync('paperless_ca_');
    final file = '${dir.path}/custom_ca.cer';
    try {
      File(file).writeAsStringSync(pem);
      ctx.setTrustedCertificates(file);
    } finally {
      try {
        dir.deleteSync(recursive: true);
      } catch (_) {}
    }
    return ctx;
  }
}

/// [ICustomCaStore] backed by [FlutterSecureStorage] (no platform auth
/// restrictions — the CA material is app-scoped private data).
class SecureStorageCaStore implements ICustomCaStore {
  SecureStorageCaStore([FlutterSecureStorage? storage])
      : _secureStorage = storage ?? const FlutterSecureStorage();

  static const _caKey = 'custom_ca_cert';

  final FlutterSecureStorage _secureStorage;

  @override
  Future<String?> readCA() => _secureStorage.read(key: _caKey);

  @override
  Future<void> saveCA(String ca) => _secureStorage.write(key: _caKey, value: ca);

  @override
  Future<void> deleteCA() => _secureStorage.delete(key: _caKey);
}
