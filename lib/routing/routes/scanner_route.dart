import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:go_router/go_router.dart';
import 'package:paperless_mobile/features/document_scan/view/scanner_page.dart';
import 'package:paperless_mobile/features/document_upload/view/document_upload_preparation_page.dart';
import 'package:paperless_mobile/routing/navigation_keys.dart';

import 'shells/authenticated_route.dart';

class ScannerBranch extends StatefulShellBranchData {
  static final GlobalKey<NavigatorState> $navigatorKey = scannerNavigatorKey;

  const ScannerBranch();
}

class ScannerRoute extends GoRouteData with $ScannerRoute {
  const ScannerRoute();

  @override
  Widget build(BuildContext context, GoRouterState state) {
    return const ScannerPage();
  }
}

/// One-shot preview channel for the upload-prep screen: the raw JPEG of the
/// FIRST scanned page only. The prep header renders it via `Image.memory`
/// instead of asking the printing plugin to raster the assembled PDF —
/// sending a multi-page PDF over the MethodChannel makes the native side
/// build a ~100 MB String and OOM the app's 256 MB Java heap (T-23 / T-38).
abstract final class UploadPreviewChannel {
  static Uint8List? _preview;

  static void set(Uint8List? preview) => _preview = preview;

  static Uint8List? consume() {
    final p = _preview;
    _preview = null;
    return p;
  }
}

class DocumentUploadRoute extends GoRouteData with $DocumentUploadRoute {
  static final GlobalKey<NavigatorState> $parentNavigatorKey =
      outerShellNavigatorKey;
  final FutureOr<Uint8List> $extra;
  final String? title;
  final String? filename;
  final String? fileExtension;
  final bool? instantUpload;

  const DocumentUploadRoute({
    required this.$extra,
    this.title,
    this.filename,
    this.fileExtension,
    this.instantUpload,
  });

  @override
  Widget build(BuildContext context, GoRouterState state) {
    return DocumentUploadPreparationPage(
      title: title,
      fileExtension: fileExtension,
      filename: filename,
      fileBytes: $extra,
      instantUpload: instantUpload ?? false,
      previewBytes: UploadPreviewChannel.consume(),
    );
  }
}
