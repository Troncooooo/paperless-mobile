import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:go_router/go_router.dart';
import 'package:paperless_mobile/features/document_scan/view/scanner_page.dart';
import 'package:paperless_mobile/features/document_upload/view/document_upload_preparation_page.dart';
import 'package:paperless_mobile/routing/navigation_keys.dart';

import 'shells/authenticated_route.dart';

/// One-shot navigation extras for the upload-prep screen: the raw scan page
/// bytes, so the prep screen can show the first page directly instead of
/// re-rasterizing the whole multi-page PDF (T-23).
///
/// go_router_builder v4 cannot generate `List<Uint8List>` route fields
/// (it crashes in `_locationQueryParams`), so the page bytes travel through
/// this consume-once channel instead of the route class.
abstract final class UploadPagesChannel {
  static List<Uint8List>? _pages;

  static void set(List<Uint8List>? pages) => _pages = pages;

  /// Returns the current pages and clears the channel (safe for a direct
  /// `.push` navigation with a single target).
  static List<Uint8List>? consume() {
    final p = _pages;
    _pages = null;
    return p;
  }
}

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
      pages: UploadPagesChannel.consume(),
    );
  }
}
