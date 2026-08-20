  SessionManagerImpl([List<Interceptor> interceptors = const []])
    : super(_initDio(interceptors));

  static Future<Dio> _initDio(List<Interceptor> interceptors) async {
    // --- TASK-02 IMPLEMENTATION START ---
    SecurityContext? secContext;
    try {
      // Safely attempt to retrieve the user-uploaded CA from secure storage
      final b64Cert = await CustomCaLoader.getCustomContext();
      if (b64Cert != null) secContext = b64Cert;
    } catch (_) { /* ignore malformed certs and fall back */ }

    final HttpClient baseHttpClient;
    if (secContext != null) {
      // Strictly enforce SSL pinning if user uploaded a CA
      baseHttpClient = HttpClient(context: secContext);
    } else {
      // ISSUE-1 FIX ENFORCED: No badCertificateCallback override. Enforce system truststore!
      baseHttpClient = HttpClient();
    }
    baseHttpClient.autoUncompress = true;
    // --- TASK-02 IMPLEMENTATION END ---

    final dio = Dio(
      BaseOptions(
        contentType: Headers.jsonContentType,
        followRedirects: true,
        maxRedirects: 10,
      ),
    );
    dio.receiveTimeout = Duration(seconds: 30); // TASK-02 Note: Increased for large uploads!
    dio.sendTimeout = Duration(seconds: 30);

    (dio.httpClientAdapter as IOHttpClientAdapter).createHttpClient = () => baseHttpClient;
    dio.interceptors.addAll([
      ...interceptors,
      DioUnauthorizedInterceptor(),
      DioHttpErrorInterceptor(),
      DioOfflineInterceptor(),
      RetryOnConnectionChangeInterceptor(dio: dio),
    ]);

    return dio;
  }
