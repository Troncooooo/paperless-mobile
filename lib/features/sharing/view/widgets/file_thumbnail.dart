import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:mime/mime.dart' as mime;
import 'package:printing/printing.dart';
import 'package:transparent_image/transparent_image.dart';

class FileThumbnail extends StatefulWidget {
  final File? file;
  final Uint8List? bytes;
  /// The raw scan page bytes (JPEG/PNG) of an assembled multi-page PDF.
  /// When set, the header preview renders only the first page via
  /// `Image.memory` instead of rasterizing the whole PDF — rasterizing a
  /// ~10+ full-res-scan PDF OOM-crashes the app (T-23).
  final List<Uint8List>? pages;

  final BoxFit? fit;
  final double? width;
  final double? height;
  const FileThumbnail({
    super.key,
    this.file,
    this.bytes,
    this.pages,
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
    final pages = widget.pages;
    if (pages != null && pages.isNotEmpty) {
      // Assembled scan PDF with raw page bytes available: the preview is a
      // single small `Image.memory` of the first page — never reads the full
      // PDF bytes and never rasters it (T-23).
      mimeType = 'application/pdf';
      _fileBytes = Future.value(null);
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
    final pages = widget.pages;

    return switch (mimeType) {
      "application/pdf" => pages != null && pages.isNotEmpty
          ? Image.memory(
              pages.first,
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
                    if (!snapshot.hasData) {
                      return const SizedBox.shrink();
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
    final info = await Printing.info();
    if (!info.canRaster) {
      return kTransparentImage;
    }
    final raster = await Printing.raster(bytes, pages: [0], dpi: 72).first;
    return raster.toPng();
  }
}
