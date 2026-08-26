---
type: Project Plan
title: "Sprint 02: mobile search & document viewer"
description: Roadmap for advanced search filters, viewer overhaul, and a local OCR cache (T-05…T-07).
tags: [roadmap, search, document-viewer, ocr]
status: draft
generated: { by: piagent/okf, at: 2026-08-25T21:41:57Z }
sources:
  - id: sprint
    resource: ../docs/sprints/SPRINT-02-MOBILE.md
    title: Sprint 02 — Mobile Search & Document Viewer Overhaul
    last_modified: 2026-08-20
---

Sprint 02 (timeline Aug 20 – Sep 1 per the doc; lead agent "Paper-Agent")
covers finding, filtering, and reviewing archived documents.[^sprint]

| ID | Task | Planned deliverable |
|---|---|---|
| T-05 | Advanced filter architecture | `lib/features/search/domain/model/filter_criteria.dart` |
| T-06 | Enhanced viewer (pinch-to-zoom, `flutter_pdfview`) | `features/assets/presentation/document_viewer_screen.dart` |
| T-07 | Local SQLite OCR text cache | `lib/services/ocr_cache_service.dart` |

**Design decisions (from the plan):** offline-first progressive enhancement
of the viewer; aggressive disposal of rendered pages for large PDFs
(>50 MB); a local full-text index for documents scanned in the current
session.

**Implementation status (verified 2026-08-25):** not yet started in the
tree — `lib/features/search/`, `lib/services/ocr_cache_service.dart`, and
`flutter_pdfview` are absent from the repository and `pubspec.yaml`
respectively. Treat statuses above as plan, not shipped code.

Context: the app already has a `document_search` feature under
`lib/features/` — see the [project overview](/architecture/overview.md).

[^sprint]: Sprint 02 — Mobile Search & Document Viewer Overhaul
