// UPDATED TO INCLUDE TASK-02 CA LOADING LOGIC
import 'dart:convert';
import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:http/io_client.dart';
import 'package:path_provider/path_provider.dart';
import 'package:file_picker/file_picker.dart';
import 'flutter_secure_storage/flutter_secure_storage.dart';

import '../constants/session_constants.dart';
import 'custom_ca_loader.dart'; // Task 02 Import
import 'session_manager.dart';

// ... [Keep existing imports and SessionManagerImpl class definition] ...

class SessionManagerImpl extends ValueNotifier<Dio?> implements SessionManager {
  // ... [existing fields] ...

  /// Initializes the Dio client, dynamically injecting a custom CA if one is loaded by the user.
  /// If no custom CA is present, it strictly falls back to the system trust store (ISS-1 FIX).
  static Future<Dio> _initDio() async {
    // --- TASK-02 IMPLEMENTATION START ---
    SecurityContext? secContext;
    final hasCustomCa = await CustomCaLoader.hasStoragedCa();

    if (hasCustomCa) {
      try {
        secContext = await CustomCaLoader.loadSecurityContext();
        logger.i('Successfully loaded custom CA for secure SSL connections.');
      } catch (e) {
        // If the stored PEM is corrupted, fall back to standard system store safely.
        logger.e('Failed loading custom CA, falling back to system truststore: $e');
      }
    }

    final HttpClient httpClient;
    
    if (secContext != null) {
      httpClient = HttpClient(context: secContext);
    } else {
      // ISSUE-1 FIX ENFORCED HERE: Never use badCertificateCallback => true!
      httpClient = HttpClient(); 
    }

    httpClient.autoUncompress = true;
    httpClient.badDataCallback = (Uint8List data, String hostname, int port) => true;

    // --- TASK-02 IMPLEMENTATION END ---

    final dio = Dio(
      BaseOptions(
        baseUrl: kDefaultServerUrl,
        contentType: Headers.jsonContentType,
        headers: <String, String>{
          HttpHeaders.acceptHeader: 'application/json',
        },
        responseType: ResponseType.json,
        followRedirects: true,
        validateStatus: (status) => status! < 500,
      ),
    );

    dio.httpClientAdapter = IOHttpClientAdapter(
      createHttpClient: () {
        return httpClient;
      },
    );

    // Add interceptors...
    dio.interceptors.addAll([
      RetryOnConnectionChangeInterceptor(dio: dio),
    ]);

    return dio;
  }
  
  /// --- Task 02 UI Helper Methods ---
  
  Future<void> uploadCustomCa() async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowedExtensions: ['pem', 'crt', 'cer'],
    );

    if (result != null && result.files.single.bytes != null) {
      final content = base64.encode(result.files.single.bytes!);
      
      await CustomCaLoader.saveCustomCa(content);
      // Reload the session manager to apply the new CA immediately.
      _dio = null; // Reset dio instance
      _dio = await _initDio();
      notifyListeners();
    }
  }
}
