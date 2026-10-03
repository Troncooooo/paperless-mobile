import 'dart:io';

import 'package:flutter/material.dart';
import 'package:paperless_mobile/generated/l10n/app_localizations.dart';

import 'package:photo_view/photo_view.dart';

typedef DeleteCallback = void Function();
typedef OnImageOperation = void Function(File);

class ScannedImageItem extends StatefulWidget {
  final File file;
  final DeleteCallback onDelete;
  //final OnImageOperation onImageOperation;

  final int index;
  final int totalNumberOfFiles;

  const ScannedImageItem({
    super.key,
    required this.file,
    required this.onDelete,
    required this.index,
    required this.totalNumberOfFiles,
    //required this.onImageOperation,
  });

  @override
  State<ScannedImageItem> createState() => _ScannedImageItemState();
}

class _ScannedImageItemState extends State<ScannedImageItem> {
  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: () => _showImage(context),
      child: _buildImageItem(context),
    );
  }

  Widget _buildImageItem(BuildContext context) {
    final borderRadius = BorderRadius.circular(12);
    return ClipRRect(
      child: Card(
        shape: RoundedRectangleBorder(
          borderRadius: borderRadius,
        ),
        clipBehavior: Clip.antiAliasWithSaveLayer,
        child: Stack(
          clipBehavior: Clip.antiAliasWithSaveLayer,
          children: [
            Align(
              alignment: Alignment.topCenter,
              child: ClipRRect(
                borderRadius: borderRadius,
                child: SizedBox(
                  height: 100,
                  child: Stack(
                    children: [
                      _ScanTileThumbnail(file: widget.file),
                      Positioned(
                        top: 0,
                        right: 0,
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 8.0,
                            vertical: 4.0,
                          ),
                          decoration: BoxDecoration(
                            color: Theme.of(context).colorScheme.surface,
                            borderRadius: const BorderRadius.only(
                              bottomLeft: Radius.circular(12),
                            ),
                          ),
                          child: Text(
                            "${widget.index + 1}/${widget.totalNumberOfFiles}",
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            Align(
              alignment: Alignment.bottomCenter,
              child: TextButton(
                onPressed: widget.onDelete,
                child: const Text("Remove"),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _showImage(BuildContext context) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (context) => Scaffold(
          appBar: AppBar(
            title: Text(
                "${S.of(context)!.scan} ${widget.index + 1}/${widget.totalNumberOfFiles}"),
          ),
          body: PhotoView(imageProvider: FileImage(widget.file)),
        ),
      ),
    );
  }
}

/// Small image for the grid tile: downsampled to ~300 px once and held in the
/// shared ImageCache, so a multi-scan buffer can't stall scrolling/first paint
/// the way full-resolution `Image.file` decodes did.
class _ScanTileThumbnail extends StatelessWidget {
  final File file;

  const _ScanTileThumbnail({required this.file});

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: Image.file(
        file,
        fit: BoxFit.cover,
        width: double.infinity,
        height: 100,
        cacheWidth: 300,
        cacheHeight: 300,
        frameBuilder: (context, child, frame, wasSynchronouslyLoaded) {
          if (wasSynchronouslyLoaded || frame != null) {
            return child;
          }
          return ColoredBox(color: Theme.of(context).colorScheme.surfaceVariant);
        },
      ),
    );
  }
}
