# T-03 — 4-corner crop editor prototype (before / after)

**Branch:** `wip/T-03-agent-crop`
**Scope:** crop editor of the vendored `edge_detection` package
(`third_party/edge_detection/`), wired in via `dependency_overrides` in
`pubspec.yaml`. The Flutter/Dart layer is unchanged; all fixes are in the
native Kotlin/Android crop pipeline, exactly as the T-02 investigation
(`docs/T02-crop-3vs4-corner-investigation.md`) recommended.

## Before (T-02 state, edge_detection @ bbc6a939)

1. **Only 3 visible corners.** `PaperProcessor.sortPoints` assigned the 4
   canonical slots with 4 independent min/max metrics that could select the
   SAME point twice → degenerate quad `[A,B,C,C]`. Two handles rendered on
   the same pixel and the crop polygon drew as a triangle. Near-triangular
   real-world scans (folded corner, shadow) were the common trigger.
2. **Wrong-corner grabbing (~31% of touches).** `PaperRectangle.
   calculatePoint2Move` picked the corner by `minBy { abs((x−downX) *
   (y−downY)) }` — a *product*, not a distance. Any touch exactly in line
   with any corner made that product 0 and grabbed a far-away corner.
   There was no hit radius at all: a tap anywhere grabbed "a" corner.
3. **Tiny targets.** Hard-coded 20px stroke circle ≈ 14dp diameter — about
   half the 40px floor of this task, a third of the 48dp platform minimum.
4. **No drag safety.** No bounds clamping, no polygon sanity check, no
   `ACTION_UP`/`ACTION_CANCEL` state reset. Dragging a corner onto another
   made `Imgproc.getPerspectiveTransform` singular → inverted/blank crops —
   a real **data-loss** path.
5. **Dead end on recovery.** If the quad arrived degenerate, the crop
   screen had no way to re-add the missing vertex.

## After (this prototype)

### Detection (`processor/PaperProcessor.kt` + new `processor/CropMath.kt`)

- New pure-Kotlin `CropMath` (no Android/OpenCV-native deps → unit-testable
  on the JVM): convex hull, simple-quad validation, canonical
  TL/TR/BR/BL ordering, parallelogram completion, hit-testing, solve math.
- `sortPoints` is replaced: **never returns a duplicated vertex**. A
  4-point convex hull is canonicalized; a near-triangle (3 distinct points,
  including the `[A,B,C,C]` shape) is geometrically completed into a valid
  convex quadrilateral (parallelogram completion, chosen closest to the
  point-cloud centroid); inputs that cannot form a quad return `null`
  instead of garbage and the next contour is tried.
- **No data loss at save time:** `cropPicture` validates the quad
  (4 distinct vertices, real area, no self-intersection) before
  `getPerspectiveTransform`; an invalid quad falls back to the **full
  image** (with a warning log) rather than an inverted/blank crop. Output
  size is also clamped to ≥1 px.

### Editor (`view/PaperRectangle.kt`, crop mode only — scan path preserved)

- **Always 4 vertices.** Degenerate input (duplicated slots, e.g.
  `[A,B,C,C]`, or a null slot) renders the 3 real corners plus a clearly
  marked **ghost handle** (amber, translucent, with an on-screen hint
  "place corner: tap the amber handle") at the computed geometric
  completion point. Tapping/dragging the ghost **places the missing
  4th corner** (tap-to-place + geometric convex completion, per spec).
  If the user never places it, `getCorners2Crop()` materializes it at the
  completion point, so the save still produces a valid crop.
- **Touch targets ≥ 48 dp** (task floor 40 px): hit radius is 48 density
  scaled; handles are 20dp filled circles with an inner contrast dot.
- **Correct hit-testing:** true Euclidean distance via
  `CropMath.pickNearestCorner`; a touch grabs only the nearest corner
  *within* the hit radius and otherwise does nothing (the old code always
  grabbed a corner, wrong ~31% of the time). Verified on the exact T-02
  repro: touch (300,120) now grabs TL instead of TR.
- **Safe moves:** every dragged position is clamped to the view bounds,
  and any move that would collapse the quad to zero area or make it
  self-intersecting is silently skipped (last valid position stays), so the
  singular-matrix save path is unreachable from the editor.
- **`ACTION_UP`/`ACTION_CANCEL`** reset the drag state.
- **Drag-to-straighten (bonus, done):** double-tap on a corner snaps it to
  the parallelogram position with the other three corners fixed
  (`CropMath.missingVertex`), with a short 8-step animation; the target is
  validated and clamped, invalid targets are a no-op. Normal drag stays
  free-form, so it never blocks anything.

## Changed files (all task-owned)

| File | Change |
| --- | --- |
| `third_party/edge_detection/` | Vendored copy of `edge_detection @ bbc6a939` (upstream unchanged since pin), patched as below; `.git`/IDE/lock artifacts and the `example/`/`screenshots/` folders removed. |
| `…/processor/CropMath.kt` | **New.** Pure-geometry core: `sortPoints`, `convexHull`, `completionPoint`, `missingVertex`, `pickNearestCorner`, `isValidQuad`, `paralellogramSolve`. |
| `…/processor/PaperProcessor.kt` | `sortPoints` → `CropMath.sortPoints`, `points.size >= 3` gate with `null` fallback; `cropPicture` validity guard + full-image fallback + ≥1px clamp. |
| `…/view/PaperRectangle.kt` | Crop-mode rewrite: ghost vertex recovery, 48dp targets, Euclidean hit-test, clamping, no-crossing moves, UP/CANCEL reset, double-tap straighten, hint text. |
| `…/android/build.gradle` | `testImplementation 'junit:junit:4.13.2'`. |
| `…/src/test/java/…/CropMathTest.kt` | **New.** 16 JVM unit tests covering the T-02 repro, `[A,B,C,C]` repair, hit-test, validity gates, completion/straighten math. |
| `pubspec.yaml` | `dependency_overrides: edge_detection → path: third_party/edge_detection`. |

## Verification status

DONE on this box (Kotlin 2.1.0 / JRE 17, in a Debian container):

- **Compile check against the real dependencies:** all four changed/new
  Kotlin files — `CropMath.kt`, `PaperProcessor.kt`, `PaperRectangle.kt`,
  `Corners.kt` — compile with **zero errors** using `kotlinc 2.1.0` against
  `com.google.android:android:4.1.1.4` (API stubs) plus the **actual
  `com.quickbirdstudios:opencv:3.4.5` AAR classes** (same artifact Gradle
  would resolve). This caught and fixed 4 real bugs that a mental review
  had missed (missing `completionPoint` Y components, `when` on a List,
  Int/Double in `drawHint`, bad method-name typos).
- **Unit tests:** all **17/17 tests in `CropMathTest.kt` pass** on the JVM
  (JUnit 4.13.2), covering: clean-rect canonicalization, the exact T-02
  repro (`[150,140],[1500,120],[1550,1900],[100,1950]`), `[A,B,C,C]`
  repair, `<=2` points/2-point/3-point degenerate inputs, the axis-aligned
  "wrong corner" hit-test case, out-of-radius taps, order-independent
  completion, collinear/duplicate rejection, all 4 `missingVertex` slots,
  parallelogram solve (orthogonal, sheared, round-trip, degenerate basis).

NOT done / not possible here:

- Full `./gradlew :edge_detection:compileDebugKotlin` / `assembleDebug`
  (needs Gradle daemon + JDK + Android SDK + Flutter plugin loader; no root
  on this box to install the SDK). Expect the plugin compile itself to
  pass (all files compile against the same jars above); the only remaining
  Gradle-side risk is packaging/manifest minutiae, which I did not touch.
- On-device visual check — see checklist below.

## Human on-device checklist

1. `flutter run` → Scanner tab → scan a document with a **folded/creased
   corner** (or tilt it into a trapezoid) → crop screen must show **4
   handles** in every case (3 white + 1 amber with a hint if the detection
   was near-triangular).
2. Drag each handle to a screen edge — it must stay on-screen and the
   polygon must never cross itself or collapse.
3. Tap far from any handle → nothing happens (no more phantom grabbing).
4. With the 4th corner amber: tap it, release → it is placed at the
   geometric completion; drag it where the fold actually is.
5. Double-tap any corner → the quad straightens into a parallelogram with
   the other three corners fixed (animation ~150 ms).
6. Save → the output page is a sane crop of the document, never blank or
   inverted, even if the handles are left in the recovered state (no data
   loss on the T-02 edge case — worst case is the full saved image with a
   warn log `cropPicture: invalid quad, falling back to full image`).
7. Regression: scan a clean rectangular document → 4 handles behave as
   before; crop/enhance/save still work.
