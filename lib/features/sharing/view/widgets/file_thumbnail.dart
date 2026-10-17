import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:mime/mime.dart' as mime;
import 'package:printing/printing.dart';
import 'package:transparent_image/transparent_image.dart';

class FileThumbnail extends StatefulWidget {
  final File? file;
  final Uint8List? bytes;
  /// A pre-rendered first page (JPEG) of an assembled multi-page PDF. When
  /// set, the header renders this single page via `Image.memory` and never
  /// asks the printing plugin to raster the assembled PDF — sending a
  /// 100+ MB PDF over the MethodChannel makes the native side build an
  /// equally large String and OOM the 256 MB Java heap (T-38).
  final Uint8List? previewBytes;

  final BoxFit? fit;
  final double? width;
  final double? height;
  const FileThumbnail({
    super.key,
    this.file,
    this.bytes,
    this.previewBytes,
    this.fit,
    this.width,
    this.height,
  }) : assert((bytes != null) != (file != null));

  @override
  State<FileThumbnail> createState() => _FileThumbnailState();
}

class _FileThumbnailState extends State<FileThumbnail> {
  late String? mimeType;
  late final Future<Uint8List?> _fileBytes;
  @override
  void initState() {
    super.initState();
    final preview = widget.previewBytes;
    if (preview != null) {
      // Assembled scan PDF with a first-page preview: render that small JPEG
      // via `Image.memory` — never reads the full PDF bytes and never
      // rasters it (T-38).
      mimeType = 'application/pdf';
      _fileBytes = Future.value(preview);
    } else {
      mimeType = widget.file != null
          ? mime.lookupMimeType(widget.file!.path)
          : mime.lookupMimeType('', headerBytes: widget.bytes);
      _fileBytes =
          widget.file?.readAsBytes().then(_convertPdfToPng) ??
          _convertPdfToPng(widget.bytes!);
    }
  }

  @override
  Widget build(BuildContext context) {
    final preview = widget.previewBytes;

    return switch (mimeType) {
      "application/pdf" => preview != null
          ? Image.memory(
              preview,
              fit: widget.fit,
              width: widget.width,
              height: widget.height,
              cacheWidth: 1600,
              cacheHeight: 2133,
              frameBuilder: (context, child, frame, wasSynchronouslyLoaded) =>
                  wasSynchronouslyLoaded || frame != null
                      ? child
                      : const SizedBox.shrink(),
              errorBuilder: (context, error, stack) =>
                  const Icon(Icons.description_outlined),
            )
          : SizedBox(
              width: widget.width,
              height: widget.height,
              child: Center(
                child: FutureBuilder<Uint8List?>(
                  future: _fileBytes,
                  builder: (context, snapshot) {
                    if (!snapshot.hasData || snapshot.data == null) {
                      return const Icon(Icons.picture_as_pdf_outlined);
                    }
                    return ColoredBox(
                      color: Colors.white,
                      child: Image.memory(
                        snapshot.data!,
                        alignment: Alignment.topCenter,
                        fit: widget.fit,
                        width: widget.width,
                        height: widget.height,
                      ),
                    );
                  },
                ),
              ),
            ),
      "image/png" ||
      "image/jpeg" ||
      "image/tiff" ||
      "image/gif" ||
      "image/webp" =>
        widget.file != null
            ? Image.file(
                widget.file!,
                fit: widget.fit,
                width: widget.width,
                height: widget.height,
              )
            : Image.memory(
                widget.bytes!,
                fit: widget.fit,
                width: widget.width,
                height: widget.height,
              ),
      "text/plain" => const Center(
          child: Text(".txt"),
        ),
      _ => const Icon(Icons.file_present_outlined),
    };
  }

  // send pdfFile as params
  Future<Uint8List?> _convertPdfToPng(Uint8List bytes) async {
    // Hard guard (T-38): Printing.raster ships the whole document over the
    // MethodChannel; at tens of MB the native base64/UTF-8 expansion OOMs
    // the 256 MB Java heap (a 115 MB PDF died at a 153 MB channel string).
    // Large PDFs without a first-page preview show a placeholder instead.
    if (bytes.length > _maxRasterBytes) {
      return null;
    }
    final info = await Printing.info();
    if (!info.canRaster) {
      return kTransparentImage;
    }
    final raster = await Printing.raster(bytes, pages: [0], dpi: 72).first;
    return raster.toPng();
  }

  /// Above this size `Printing.raster(bytes)` OOMs the native 256 MB heap
  /// (T-38: a 115 MB assembled PDF died at a 153 MB channel string). 8 MB
  /// keeps the base64/UTF-8 expansion around 22 MB — safely inside heap.
  static const int _maxRasterBytes = 8 * 1024 * 1024;
}
