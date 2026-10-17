# T-38 — 16-page scan upload OOM on prep screen

## Symptom (v711, 2026-10-12, owner's Motorola Edge 50 Neo / Android 16)
Scan 16 pages → press Upload → crash **as soon as the upload screen is visible**.
No upload ever starts. logcat:
`OutOfMemoryError: Failed to allocate a 153678192 byte allocation,
with 100663296 free bytes and 106MB until OOM, target footprint
257179760 (245 MiB), growth limit 268435456 (256 MiB)`,
`Process: de.astubenbord.paperless_mobile, PID: 27922`.

## Root cause (proven)
1. `153,678,192` = exactly `16 × 9,604,887` (16 scan pages) and = `115,258,644 × 4/3`
   (base64 of a 115,258,644-byte PDF = the assembled 16-page PDF).
2. Dex decompilation (androguard, minified names): `r2/x.c(ByteBuffer)` =
   `new String(bytes, offset, length, UTF-8)` over the **entire**
   MethodChannel payload — this is the Flutter engine's `JSONMethodCodec`
   string expansion path.
3. The only app code that puts the full assembled PDF on a MethodChannel is
   `FileThumbnail._convertPdfToPng` → `Printing.raster(pdfBytes, pages:[0])`
   (printing 5.14.2, channel `net.nfet.printing`: `byte[] document =
   call.argument("doc")`). The prep screen's header thumbnail triggers it on
   build; the upload (dio/dart:io HTTP) itself never starts — consistent with
   "crash when the screen appears".
4. v711 had T-23's `pages` fast-path, but the trigger was
   `UploadPagesChannel.consume()` returning `null` (consume-once; a route
   rebuild re-pushes the prep page without pages) → fell into the raster path.
   v712 (`46b4fb1d`) removed `pages` entirely → **100% repro of the crash on
   every multi-page upload**.

## Fix (commit 6c335e28, v713)
- `UploadPreviewChannel` (one-shot, scanner_route.dart): scanner passes only
  the **first** page JPEG (~9 MB → ~12 MB channel string, safe margin) with
  the upload bytes.
- `DocumentUploadPreparationPage` → `FileThumbnail(previewBytes:)` renders it
  via `Image.memory` — pure Dart, no platform channel, no base64.
- Hard guard: `_convertPdfToPng` returns null for PDFs `> 8 MB` without a
  preview (placeholder icon). Bounded worst case ~22 MB native expansion vs
  the 256 MB heap — share-flow and discard-dialog thumbnails are now safe too.
- `AssembledFile.previewPage` set by both single-file and multi-page assembly.

## Verification plan
- CI: flutter analyze + build (run on 6c335e28).
- Owner (A-13): uninstall v711 → install v713 → scan 16 pages → Upload must
  not crash; header shows page 1; document uploads to paperless-ngx.
