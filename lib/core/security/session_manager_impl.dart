import 'dart:io';

import 'package:dio/dio.dart';
import 'package:dio/io.dart';
import 'package:flutter/material.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:paperless_mobile/api/paperless_api.dart';
import 'package:paperless_mobile/constants.dart';
import 'package:paperless_mobile/core/interceptor/api_version_header.interceptor.dart';
import 'package:paperless_mobile/core/interceptor/dio_offline_interceptor.dart';
import 'package:paperless_mobile/core/interceptor/dio_unauthorized_interceptor.dart';
import 'package:paperless_mobile/core/interceptor/persistent_logging_interceptor.dart';
import 'package:paperless_mobile/core/interceptor/retry_on_connection_change_interceptor.dart';
import 'package:paperless_mobile/core/security/session_manager.dart';
import 'package:paperless_mobile/features/logging/data/logger.dart';
import 'package:paperless_mobile/features/login/model/client_certificate.dart';
import 'package:paperless_mobile/features/login/server_connection/model/header_entry.dart';

// TASK-02: User-installed custom CA certificates for self-signed servers (ISS-1).
import 'custom_ca_loader.dart';

/// Manages the security context, authentication and base request URL for
/// an underlying [Dio] client which is injected into all services
/// requiring authenticated access to the Paperless REST API.
class SessionManagerImpl extends ValueNotifier<Dio> implements SessionManager {
  static int? _apiVersion;
  @override
  Dio get client => value;

  SessionManagerImpl([List<Interceptor> interceptors = const []])
    : super(_initDio(interceptors));

  static Dio _initDio(List<Interceptor> interceptors) {
    //en- and decoded by utf8 by default
    final Dio dio = Dio(
      BaseOptions(
        contentType: Headers.jsonContentType,
        followRedirects: true,
        maxRedirects: 10,
      ),
    );
    dio.options
      ..receiveTimeout = const Duration(seconds: 10)
      ..sendTimeout = const Duration(seconds: 10)
      ..responseType = ResponseType.json;

    // TASK-02 / ISS-1: TLS is validated strictly against the CA chain.
    // If the user installed a custom CA it is loaded into the SecurityContext
    // via [CustomCaLoader]; otherwise the system trust store is used.
    // `badCertificateCallback` is intentionally NOT used.
    (dio.httpClientAdapter as IOHttpClientAdapter).createHttpClient = () =>
        HttpClient(context: CustomCaLoader.currentContext)
          ..idleTimeout = 15.seconds;

    dio.interceptors.addAll([
      ...interceptors,
      ApiVersionHeaderInterceptor(() => _apiVersion),
      DioUnauthorizedInterceptor(),
      DioHttpErrorInterceptor(),
      DioOfflineInterceptor(),
      RetryOnConnectionChangeInterceptor(dio: dio),
      PersistentLoggingInterceptor(),
    ]);
    return dio;
  }

  @override
  void updateSettings({
    String? baseUrl,
    String? authToken,
    int? apiVersion,
    ClientCertificate? clientCertificate,
    List<HeaderEntry>? additionalHeaders,
    bool broadcast = true,
  }) {
    if (clientCertificate != null) {
      final context = SecurityContext()
        ..usePrivateKeyBytes(
          clientCertificate.bytes,
          password: clientCertificate.passphrase,
        )
        ..useCertificateChainBytes(
          clientCertificate.bytes,
          password: clientCertificate.passphrase,
        )
        ..setTrustedCertificatesBytes(
          clientCertificate.bytes,
          password: clientCertificate.passphrase,
        );
      // ISS-1: strict verification; the client certificate's chain is added to
      // the trusted set above instead of disabling verification.
      final adapter = IOHttpClientAdapter()..createHttpClient = () => HttpClient(context: context);
      client.httpClientAdapter = adapter;
    }

    if (baseUrl != null) {
      client.options.baseUrl = baseUrl;
    }

    if (additionalHeaders != null && additionalHeaders.isNotEmpty) {
      client.options.headers.addEntries(
        additionalHeaders
            .where((header) => header.enabled)
            .map((header) => MapEntry(header.key.trim(), header.value.trim())),
      );
    }

    if (authToken != null) {
      client.options.headers.addEntries([
        MapEntry(HttpHeaders.authorizationHeader, 'Token $authToken'),
      ]);
    }

    SessionManagerImpl._apiVersion = apiVersion;

    if (broadcast) {
      notifyListeners();
    }
  }

  @override
  Future<int> getApiVersion() async {
    if (SessionManagerImpl._apiVersion != null) {
      return SessionManagerImpl._apiVersion!;
    }
    try {
      final response = await client.head(
        "/api/schema/",
        options: Options(receiveTimeout: 5.seconds, sendTimeout: 5.seconds),
      );
      final apiVersionHeader = response.headers.value('x-api-version');
      if (apiVersionHeader != null) {
        return int.parse(apiVersionHeader);
      } else {
        logger.fw(
          'API version header not found in response. Defaulting to minimum supported version.',
          className: runtimeType.toString(),
          methodName: 'getApiVersion',
        );
        return minSupportedApiVersion;
      }
    } catch (e, stackTrace) {
      logger.fe(
        'Failed to retrieve API version from server. Defaulting to minimum supported version.',
        error: e,
        stackTrace: stackTrace,
        className: runtimeType.toString(),
        methodName: 'getApiVersion',
      );
      return minSupportedApiVersion;
    }
  }

  @override
  void resetSettings({bool broadcast = true}) {
    // Back to custom-CA (or system) truststore, without a client certificate.
    client.httpClientAdapter =
        IOHttpClientAdapter()..createHttpClient = () => HttpClient(context: CustomCaLoader.currentContext);
    client.options.baseUrl = '';
    client.options.headers.clear();
    notifyListeners();
  }
}
