# T-02 — Crop editor shows 3 corner points instead of 4, and they are hard to move

**Investigated:** 2026-09-29 by `worker-crop` (read-only; no app code modified)
**Owner report:** "after scanning a document, the crop workflow shows 3 corner points instead of 4, and it is also hard to change (move) them."

## Architecture / pipeline

The app's scan flow does **not** implement crop in Flutter. `ScannerPage._openDocumentScanner`
(`lib/features/document_scan/view/scanner_page.dart:237`) calls the **git-pinned third-party
package `edge_detection` v1.1.3**:

- pubspec.yaml → `edge_detection` git `https://github.com/sawankumarbundelkhandi/edge_detection`, ref `master`
- resolved commit: **`bbc6a939e29689790f1b64734df39a66334e0be9`** (see `.dart_tool/package_config.json`)
- all crop UI is **native Kotlin/Android** inside that package:
  - `…/processor/PaperProcessor.kt` — OpenCV contour → `approxPolyDP` → quad
  - `…/view/PaperRectangle.kt` — overlay `View`: path + 4 circles + drag
  - `…/crop/CropActivity.kt` / `CropPresenter.kt` — crop screen, save/rotate/enhance
  - `…/scan/ScanPresenter.kt:247-256` — sets `SourceManager.corners = processPicture(...)` then launches `CropActivity`
  - `…/processor/Corners.kt:6` — `data class Corners(val corners: List<Point?>, val size: Size)` (nullable elements, fixed 4-slot model, **never persisted** — transient static in `SourceManager`)

So T-03's fix must land by **vendoring/patching the package** (flutter `dependency_overrides`
→ path to a local fork copy), not by editing `lib/`. Upstream `master` has exactly ONE commit
after the pin (`01bf319`, null-safety cleanup only) — **no corner fix exists upstream**.

## Repro steps

1. Open app → Scanner tab → camera scan of a document that is *not perfectly rectangular* in the frame:
   e.g. one corner folded/creased, paper slightly trapezoidal, or a shadow clipping one corner (i.e. the bright region is nearly **triangular**, the common real-world case).
2. Detection succeeds (the contour yields 4 `approxPolyDP` points), crop screen opens.
3. **Observed:** three corner handles are visible (the quad path is drawn as a triangle:
   first and last points coincide) and the handles are fiddly to aim at.

The same screen with a clean rectangular document correctly shows 4 handles — i.e. it is
input-dependent, not a build difference.

## Root cause (file:line)

**`sortPoints()` in `PaperProcessor.kt:143-148` (edge_detection @ bbc6a939) assigns the 4
canonical slots by four independent min/max metrics, and the metrics can select the SAME point
twice — producing a degenerate quad like `[A, B, C, C]` where two handles render at the same
pixel, so the polygon path is a triangle and the user sees 3 corner points.**

```kotlin
// PaperProcessor.kt:143-148
val p0 = points.minByOrNull { it.x + it.y }      // "top-left"
val p1 = points.minByOrNull { it.y - it.x }      // "top-right" = max(x-y)
val p2 = points.maxByOrNull { it.x + it.y }      // "bottom-right"
val p3 = points.maxByOrNull { it.y - it.x }      // "bottom-left"
return listOf(p0, p1, p2, p3)
```

Gate that lets the degenerate quad through — `PaperProcessor.kt:132`:
`if (points.size == 4 && Imgproc.isContourConvex(convex))` — a nearly-triangular contour
(3 real vertices + 1 nearly-collinear point) passes `isContourConvex` and `points.size == 4`,
so it is accepted. (Note `convex` is the **32-bit-int** conversion of the points, `:130`.)

Where the "3 points" becomes visible — `PaperRectangle.kt`:
- `:98-112` `onCorners2Crop(...)` reads `corners.corners[0..3]` into `tl/tr/br/bl` with **no
  duplicate guard** (the duplicate guard exists only in the scan-overlay path, `:61-70`, and the
  crop screen doesn't use it),
- `:133-136` `onDraw` draws one circle per slot — with `[A, B, C, C]` two circles land on the
  same pixel → **3 visible handles**, and `:88-92`/`:168-175` the path `A→B→C→C→close` is a
  **triangle** (verified numerically, e.g. detection points
  `(1080,483),(1614,6),(28,759),(1910,1198)` → slots `(28,759),(1614,6),(1910,1198),(28,759)`).

**"Hard to move" root causes — `PaperRectangle.kt`:**
1. `:162-166` `calculatePoint2Move` picks the corner by `minBy { abs((x−downX) * (y−downY)) }` —
   the *product* of deltas, not distance. Any touch that is axis-aligned with any corner makes
   that product **exactly 0** → grabs a far-away corner. Concrete: quad
   TL(150,140) TR(1500,120) BR(1550,1900) BL(100,1950), touch (300,120) → 151 px from TL, but
   **TR (1200 px) is grabbed** (product 0). Across 20k random touches, the wrong corner was
   selected **31.3%** of the time.
2. `:140-159` `onTouchEvent`: **no hit radius** — a tap anywhere on screen grabs the (wrong/
   nearest) corner; no `ACTION_UP`/`ACTION_CANCEL` state reset; corners are **not clamped** to
   the image bounds — you can drag a corner off-screen or on top of another, which
   additionally makes `Imgproc.getPerspectiveTransform` in `PaperProcessor.kt:44-52`
   singular → corrupt/inverted crop (data-corruption risk; T-03 must guarantee no data loss).
3. `:133-136` handle radius is a hard-coded `20F` **px** stroke → diameter ≈ **14–15 dp** on a
   2.75×–3× density phone, ~1/3 of the 48dp minimum touch target; the visual ring is `4F` px
   stroke (init block `:44-52`).

## Why it happens

- Detection (`PaperProcessor.getCorners`, `:117-141`) accepts *any* 4-point convex contour,
  including near-triangles; `sortPoints`'s four one-dimension projections then collide (two slots
  = same point) whenever no single point is the unique extreme of all four metrics. Brute force:
  among random 4-point sets, **66% collapse to exactly 3 distinct points, 10% to 2**, while a
  proper quad needs all four unique extremes.
- The data model (`Corners.corners: List<Point?>`, `Corners.kt:6`) *can* even hold `null`
  elements → `?: Point()` in `sortPoints`/`onCorners2Crop` would silently place a phantom
  handle at (0,0); not the primary trigger here, but a latent landmine the fix should remove.
- The crop editor was written as a thin "draw 4 fixed circles + move nearest" overlay with no
  validation, clamping, or hit-testing — hence both symptoms in one screen.

## Hypotheses ruled out (for the record)

- ❌ "renderer deliberately drops a vertex" — `onDraw` always draws 4 circles (verified).
- ❌ "persisted quad from an earlier state" — `SourceManager.corners` is a session-only static;
  nothing is serialized/loaded.
- ❌ "detection returns 3 points" — `getCorners` gates on `points.size == 4`; 3-point contours
  yield `null` → default 0.1/0.9 rectangle (4 corners, `PaperRectangle.kt:104-107`).
- ❌ "fixed by a newer package version" — upstream master is 1 (cosmetic) commit ahead of pin.

## Proposed fix (input for T-03 prototype)

Strategy: **vendor the `edge_detection` Kotlin crop UI** (copy the `crop/`, `view/`,
`processor/` packages + resources into a local module or path-overridden fork) so T-03 can fix
without upstream dependency. `dependency_overrides: edge_detection: {path: ...}` in
`pubspec.yaml`. Minimal changes:

1. **`PaperProcessor.sortPoints` — always emit 4 distinct points.**
   - Keep TL/BR via min/max(x+y) and TR/BL via max/min(x−y), but if any two slots match,
     re-solve: for the collinear vertex case, replace the duplicated slot with the corner of the
     *minimum-area enclosing rectangle* (`Imgproc.minAreaRect`), or project the duplicated slot
     perpendicularly to the adjacent edge; guarantee `allDistinct` before returning.
   - Add a `require(corners.none{ it == null } && distinct)` and return `null` (or the full-image
     default) on failure — never a 3-point quad.
   - Change `Corners.corners` type to `List<Point>` (drop nullability, `Corners.kt:6`).
2. **`PaperRectangle` — make editing trustworthy:**
   - hit-test with **true Euclidean distance + a ≥ 48dp radius** (`minByOrNull { hypot(...) }`);
     only grab if within radius, else treat as "no handle" (→ allows tap-to-add later);
   - clamp every moved corner to `RectF(0,0,width,height)` and keep the quad simple
     (no self-intersection: reject/undo a move that flips the polygon);
   - add `ACTION_UP`/`ACTION_CANCEL` handling (deselect, recompute latestDown);
   - radius in **dp** (`24f * density`), plus a filled inner dot for contrast (`:133-136`, `:44-52`).
3. **`onCorners2Crop` — defensive init:** if incoming quad is degenerate (duplicates), fall back
   to the 0.1/0.9 default rect (`:98-112`), mirroring the existing scan-side guard (`:61-70`).
4. Keep `cropPicture` safe: validate the 4 points are distinct and counter-clockwise-consistent
   before `getPerspectiveTransform` (`PaperProcessor.kt:16-58`) → prevents the singular-matrix
   corruption path.

## UX improvement list (T-03 acceptance inputs)

- [ ] **Always exactly 4 visible, draggable vertices** — enforced by invariant #1 above; the
      triangle state must be unreachable (unit-test `sortPoints` on near-triangle inputs).
- [ ] **Touch targets ≥ 48 dp** (task floor: 40 px) with visual radius ≥ 24dp + filled center dot.
- [ ] **Tap-to-add / tap-to-activate a missing or stuck vertex**: if a tap lands > 1.5× the
      handle radius away, and the quad is degenerate, offer the nearest edge's projection as a
      restored 4th corner (or re-run detection on the current crop region).
- [ ] **Drag-to-straighten**: with a corner held, a long-press (or a toolbar "auto-align")
      snaps the quad to a best-fit perspective rect (`minAreaRect` / vanishing-point solve) and
      animates all 4 corners; plain drag stays free-form.
- [ ] Whole-shape move (tap inside the quad body) + whole-shape rotate — currently *any* tap
      moves a corner, which is the main "hard to use" feeling.
- [ ] No data loss: if a crop would be singular/illegal, block save with a hint instead of
      corrupting the JPEG (see `CropPresenter.save` at `CropPresenter.kt:130-163`).

## Verification notes for T-03

- Unit-testable (pure Kotlin): `sortPoints` on degenerate/near-triangle point sets — assert 4
  distinct points; hit-test radius selection on axis-aligned touches — assert nearest wins.
- Manual: 3 paper poses (folded corner, trapezoid, clean rect) → 4 handles in every case; drag
  each handle to screen edge; save → crop not inverted/blank.

## Security

- No secrets touched during investigation; `/home/piagent/projects/.github_pat`, `.gmail` and
  `.env` files were **not read or echoed**. Any such values encountered must be redacted.
- Working-tree pre-existing modifications in the android build files were left untouched; no app
  code was modified by this task.
