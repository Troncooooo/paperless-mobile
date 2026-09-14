class ServerMessageException implements Exception {
  final String message;
  final int? statusCode;
  final String? requestPath;
  final dynamic originalBody;

  /// Creates a [ServerMessageException].
  ///
  /// The [message] positional parameter is required and contains the error message.
  /// Optional named parameters [statusCode], [requestPath], and [originalBody]
  /// can be provided to include HTTP metadata for debugging purposes.
  /// When [originalBody] contains sensitive fields (token, authorization, password),
  /// those fields will be sanitized before storage per NFR-04.
  ServerMessageException(
    this.message, {
    this.statusCode,
    this.requestPath,
    dynamic originalBody,
  }) : originalBody = _sanitizeBody(originalBody);

  /// Sanitizes the [body] by removing sensitive fields (AC-04, NFR-04).
  static dynamic _sanitizeBody(dynamic body) {
    if (body is Map) {
      return {for (var entry in body.entries)
        if (!_sensitiveKeys.contains(entry.key))
          entry.key: entry.value
      };
    }
    if (body is List) {
      return body.map((e) => _sanitizeBody(e)).toList();
    }
    if (body is String) {
      // Limit serialization to 8KB for non-JSON bodies (OQ-04)
      final truncatedBody = body.length > 8192 ? body.substring(0, 8192) + '...' : body;
      // Check for base64-like strings which often indicate tokens
      if (truncatedBody.length > 512 && _isLikelyToken(truncatedBody)) {
        return null;
      }
      return truncatedBody;
    }
    return body;
  }

  static bool _isLikelyToken(String s) {
    // Check for base64-like strings which often indicate tokens
    final RegExp base64Pattern = RegExp(r'^[A-Za-z0-9+/]+={0,3}$');
    return base64Pattern.hasMatch(s);
  }

  static const _sensitiveKeys = {'token', 'authorization', 'password'};

  @override
  String toString() {
    // AC-04: Include HTTP metadata when available
    if (statusCode != null || requestPath != null) {
      final httpPart = statusCode != null ? '[HTTP $statusCode]' : '';
      final pathPart = requestPath != null ? requestPath! : '';
      final prefix = [httpPart, pathPart].where((p) => p.isNotEmpty).join(' — ');
      if (prefix.isNotEmpty) {
        return '[$prefix]: $message';
      }
    }
    // AC-13/AC-05: Omit HTTP section when metadata is null
    return message;
  }
}
