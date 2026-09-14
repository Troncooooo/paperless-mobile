import 'package:flutter/foundation.dart';
import 'package:local_auth/local_auth.dart';
import 'dart:async';

class LocalAuthenticationService {
  final LocalAuthentication localAuthentication;

  LocalAuthenticationService(this.localAuthentication);

  /// Attempts local authentication with biometric or device credential fallback.
  Future<bool> authenticateLocalUser(String localizedReason) async {
    // Check if device hardware supports any form of secure authentication
    final isDeviceSupported = await localAuthentication.isDeviceSupported();
    
    if (!isDeviceSupported) {
      debugPrint('Biometric / secure auth hardware not supported on this device');
      return false;
    }

    // Check for enrolled biometrics. If none are available, fall back immediately
    final availableBiometrics = await localAuthentication.getAvailableBiometrics();
    final hasBiometrics = availableBiometrics.isNotEmpty;

    if (hasBiometrics) {
      try {
        // Attempt biometric authentication first
        return await localAuthentication.authenticate(
          localizedReason: localizedReason,
          options: const AuthenticationOptions(
            stickyAuth: true,
            biometricOnly: false,  // Allows fallbaack to PIN/Pattern on Android 10+
            useErrorDialogs: true,
          ),
        );
      } catch (e) {
        debugPrint('Biometric auth failed. Falling back to device credentials.');
      }
    }

    // Fallback: Attempt device lock screen credential (PIN / Pattern)
    if (hasBiometrics || !await _isDeviceSecureEnough()) {
      return await _authenticateWithDeviceCredentials(localizedReason);
    }

    return false;
  }

  /// Attempts authentication using only the device lock screen credential.
  Future<bool> _authenticateWithDeviceCredentials(String localizedReason) async {
    try {
      return await localAuthentication.authenticate(
        localizedReason: localizedReason,
        options: const AuthenticationOptions(
          stickyAuth: true,
          biometricOnly: false,
          useErrorDialogs: true,
        ),
      );
    } catch (e) {
      debugPrint('Device credential fallback also failed.');
      return false;
    }
  }

  /// Checks if the device credentials are strong enough for authentication.
  Future<bool> _isDeviceSecureEnough() async {
    // local_auth 2.x: `canAuthenticate()` was removed; device support plus
    // the platform's own credential fallback (biometricOnly: false) is enough
    // to decide whether to offer the fallback at all.
    return await localAuthentication.isDeviceSupported();
  }
}
