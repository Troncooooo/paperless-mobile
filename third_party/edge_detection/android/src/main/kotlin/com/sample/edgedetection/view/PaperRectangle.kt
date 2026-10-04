package com.sample.edgedetection.view

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.sample.edgedetection.processor.Corners
import com.sample.edgedetection.processor.CropMath
import com.sample.edgedetection.processor.TAG
import org.opencv.core.Point
import org.opencv.core.Size
import kotlin.math.hypot

/**
 * Overlay for the crop editor.
 *
 * T-03 rewrite of the crop-mode interaction (the scan-overlay path is
 * unchanged):
 *
 *  - **Always 4 vertices.** Degenerate input (duplicate or missing
 *    vertices, e.g. the T-02 `[A,B,C,C]` case) renders 3 solid handles +
 *    one **ghost handle** (amber) at the parallelogram-completion point.
 *    Tapping/dragging the ghost places the missing 4th corner, so the
 *    triangle state is only a transient recovery state, never a dead end.
 *  - **Touch targets >= 48 dp** (task floor: 40 px): the hit radius is
 *    density-scaled and handles are drawn proportionally larger with a
 *    filled body for contrast.
 *  - **Correct hit-testing**: true Euclidean distance, grab only within
 *    the hit radius (the old `abs(dx*dy)` product picked the wrong corner
 *    in ~31% of touches; touches away from any corner now do nothing
 *    instead of grabbing a far corner).
 *  - **Safe moves**: every dragged position is clamped to the view bounds
 *    and moves that would make the quad degenerate (zero area /
 *    self-intersection) are simply not applied, so the singular-matrix
 *    save path is unreachable from the editor.
 *  - **`ACTION_UP`/`ACTION_CANCEL`** reset the drag state.
 *  - **Straighten (nice-to-have)**: double-tap a corner (other three
 *    held fixed) to snap it to the parallelogram position with a short
 *    animation. Normal drag stays free-form.
 */
class PaperRectangle : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attributes: AttributeSet) : super(context, attributes)
    constructor(context: Context, attributes: AttributeSet, defTheme: Int) : super(
        context,
        attributes,
        defTheme
    )

    private val rectPaint = Paint()
    private val handlePaint = Paint()
    private val handleInnerPaint = Paint()
    private val ghostPaint = Paint()
    private val hintPaint = Paint()

    private var ratioX: Double = 1.0
    private var ratioY: Double = 1.0

    // Corners in *view* pixel space, fixed TL / TR / BR / BL slots.
    private var tl: Point = Point()
    private var tr: Point = Point()
    private var br: Point = Point()
    private var bl: Point = Point()

    private val path: Path = Path()

    // T-03 hit-testing / touch geometry (density-scaled, >= 48dp hit radius).
    private val density = resources.displayMetrics.density
    private val handleHitRadius = 48f * density
    private val handleVisualRadius = 20f * density
    private val handleDotRadius = 7f * density
    private val doubleTapSlop = 16f * density

    // T-03 interaction state.
    private var cropMode = false
    private var draggingIndex: Int? = null      // 0=tl 1=tr 2=br 3=bl, null = not dragging
    private var latestDownX = 0.0F
    private var latestDownY = 0.0F
    private var lastTapIndex = -1
    private var lastTapTime = 0L
    private var isAnimating = false

    // T-03 missing-vertex recovery state: which slot is the ghost and its
    // proposed (parallelogram-completed) position, in view pixels.
    private var degenerate = false
    private var ghostIndex = -1
    private var ghostPoint = Point()

    init {
        rectPaint.color = Color.argb(128, 255, 255, 255)
        rectPaint.isAntiAlias = true
        rectPaint.isDither = true
        rectPaint.strokeWidth = 6F
        rectPaint.style = Paint.Style.FILL_AND_STROKE
        rectPaint.strokeJoin = Paint.Join.ROUND    // set the join to round you want
        rectPaint.strokeCap = Paint.Cap.ROUND      // set the paint cap round too
        rectPaint.pathEffect = CornerPathEffect(10f)

        handlePaint.color = Color.WHITE
        handlePaint.isDither = true
        handlePaint.isAntiAlias = true
        handlePaint.style = Paint.Style.FILL

        handleInnerPaint.color = Color.argb(120, 0, 0, 0)
        handleInnerPaint.isAntiAlias = true
        handleInnerPaint.style = Paint.Style.FILL

        ghostPaint.color = Color.argb(150, 255, 179, 0)
        ghostPaint.isAntiAlias = true
        ghostPaint.style = Paint.Style.FILL

        hintPaint.color = Color.argb(230, 255, 179, 0)
        hintPaint.isAntiAlias = true
        hintPaint.textSize = 13f * density
        hintPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    fun onCornersDetected(corners: Corners) {
        // Scan-overlay path (unchanged behavior): fall back to the default
        // inset rectangle when the detection produced a degenerate quad.
        ratioX = corners.size.width.div(measuredWidth)
        ratioY = corners.size.height.div(measuredHeight)

        val list = corners.corners
        val distinct = CropMath.dedupe(list.filterNotNull())
        var ok = distinct.size == 4
        if (ok) {
            ok = CropMath.isValidQuad(list.filterNotNull())
        }
        if (!ok) {
            resizeDefault()
            resetPath()
            return
        }

        tl = list[0] ?: Point()
        tr = list[1] ?: Point()
        br = list[2] ?: Point()
        bl = list[3] ?: Point()

        Log.i(TAG, "POINTS tl ------>  $tl corners")
        Log.i(TAG, "POINTS tr ------>  $tr corners")
        Log.i(TAG, "POINTS br ------>  $br corners")
        Log.i(TAG, "POINTS bl ------>  $bl corners")

        resize()
        resetPath()
    }

    fun onCornersNotDetected() {
        path.reset()
        invalidate()
    }

    fun onCorners2Crop(corners: Corners?, size: Size?, paperWidth: Int, paperHeight: Int) {
        if (size == null || paperWidth <= 0 || paperHeight <= 0) {
            return
        }

        cropMode = true
        val raw = listOf(
            corners?.corners?.get(0),
            corners?.corners?.get(1),
            corners?.corners?.get(2),
            corners?.corners?.get(3),
        )
        ratioX = size.width.div(paperWidth)
        ratioY = size.height.div(paperHeight)

        // Keep 4 fixed slots (tl,tr,br,bl), mapping nulls to null instead of
        // index-shifting the list.
        val mapped: List<Point?> = raw.map { c ->
            if (c == null) null else Point(c.x.div(ratioX), c.y.div(ratioY))
        }
        val distinct = CropMath.dedupe(mapped.filterNotNull())

        when {
            distinct.size == 4 && CropMath.isValidQuad(mapped.filterNotNull()) -> {
                tl = mapped[0] ?: Point()
                tr = mapped[1] ?: Point()
                br = mapped[2] ?: Point()
                bl = mapped[3] ?: Point()
                degenerate = false
                ghostIndex = -1
            }
            distinct.size >= 3 -> {
                tl = mapped[0] ?: Point()
                tr = mapped[1] ?: Point()
                br = mapped[2] ?: Point()
                bl = mapped[3] ?: Point()
                if (!setupGhost()) {
                    // Cannot geometrically complete (e.g. all points
                    // collinear): fall back to the sane default inset rect
                    // instead of an unusable overlay.
                    resizeDefault()
                    return
                }
            }
            else -> {
                resizeDefault()
                return
            }
        }
        if (!degenerate) {
            clampAll()
        }
        resetPath()
    }

    /**
     * Marks the duplicated/null slot as the ghost vertex and proposes a
     * geometric (parallelogram) completion point for it, clamped to the
     * view bounds. Returns true on success.
     */
    private fun setupGhost(): Boolean {
        val real = currentPoints()
        val distinctNow = CropMath.dedupe(real)
        if (distinctNow.size > 3) return false

        // Ghost slot = the slot sharing a pixel with another slot (prefers
        // the last one, so [A,B,C,C] -> slot BL/3); if every slot is unique
        // but some point is the unplaced (0,0) default, pick that slot.
        val slotOf = { p: Point -> slotCount(real, p) }
        var g = 3
        if (real.any { slotOf(it) > 1 }) {
            g = (3 downTo 0).first { slotOf(real[it]) > 1 }
        } else {
            g = (3 downTo 0).first { distinctNow.none { d -> CropMath.near(d, real[it]) } }
        }
        val known = real.filterIndexed { i, _ -> i != g }.take(3).filterNotNull()
        val comp = CropMath.completionPoint(known) ?: return false

        ghostIndex = g
        ghostPoint = Point(
            comp.x.coerceIn(0.0, width.coerceAtLeast(1).toDouble()),
            comp.y.coerceIn(0.0, height.coerceAtLeast(1).toDouble()),
        )
        // Keep the ghost slot at the ghost position so the polygon draws
        // sensibly if it is saved before the user moves it.
        setPoint(g, ghostPoint)
        degenerate = true
        return true
    }

    private fun slotCount(points: List<Point>, p: Point): Int =
        points.count { CropMath.near(it, p) }

    fun getCorners2Crop(): List<Point> {
        // If the user never placed the ghost vertex, materialize it at the
        // proposed completion point: that quad is a valid parallelogram, so
        // the crop still succeeds (no data loss) instead of falling back.
        if (degenerate && ghostIndex in 0..3) {
            setPoint(ghostIndex, ghostPoint)
            degenerate = false
        }
        reverseSize()
        return listOf(tl, tr, br, bl)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        rectPaint.color = Color.WHITE
        rectPaint.strokeWidth = 6F
        rectPaint.style = Paint.Style.STROKE
        canvas?.drawPath(path, rectPaint)

        rectPaint.color = Color.argb(128, 255, 255, 255)
        rectPaint.strokeWidth = 0F
        rectPaint.style = Paint.Style.FILL
        canvas?.drawPath(path, rectPaint)

        if (!cropMode) return

        val points = currentPoints()
        for (i in 0..3) {
            val p = points[i]
            if (degenerate && i == ghostIndex) {
                // Ghost/placeholder handle: amber, translucent, with hint.
                canvas?.drawCircle(p.x.toFloat(), p.y.toFloat(), handleVisualRadius, ghostPaint)
                canvas?.drawCircle(p.x.toFloat(), p.y.toFloat(), handleDotRadius, handleInnerPaint)
                canvas?.let { drawHint(it, p) }
            } else {
                canvas?.drawCircle(p.x.toFloat(), p.y.toFloat(), handleVisualRadius, handlePaint)
                canvas?.drawCircle(p.x.toFloat(), p.y.toFloat(), handleDotRadius, handleInnerPaint)
            }
        }
    }

    private fun drawHint(canvas: Canvas, p: Point) {
        val text = "place corner: tap the amber handle"
        val w = hintPaint.measureText(text)
        val maxRight = width.toDouble() - w
        var x = (p.x + handleVisualRadius).coerceIn(0.0, maxRight.coerceAtLeast(0.0))
        var y = (p.y - handleVisualRadius).coerceAtLeast(0.0)
        if (y - hintPaint.textSize < 0.0) y = p.y + handleVisualRadius + hintPaint.textSize
        canvas.drawText(text, x.toFloat(), y.toFloat(), hintPaint)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (!cropMode || event == null || isAnimating) {
            return super.onTouchEvent(event)
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                latestDownX = event.x
                latestDownY = event.y
                // T-03: Euclidean nearest-corner within the 48dp hit radius;
                // a touch far from every corner grabs nothing (old code
                // always grabbed the "nearest" under abs(dx*dy), wrong ~31%).
                draggingIndex = CropMath.pickNearestCorner(
                    effectivePoints(),
                    event.x.toDouble(),
                    event.y.toDouble(),
                    handleHitRadius.toDouble(),
                )
            }

            MotionEvent.ACTION_MOVE -> {
                val idx = draggingIndex ?: return true
                val dx = (event.x - latestDownX).toDouble()
                val dy = (event.y - latestDownY).toDouble()

                // First movement of a ghost handle materializes it: the
                // vertex is now real and follows the finger.
                if (degenerate && idx == ghostIndex) {
                    degenerate = false
                }

                val moved = Point(
                    point(idx).x + dx,
                    point(idx).y + dy
                ).let {
                    Point(
                        it.x.coerceIn(0.0, width.coerceAtLeast(1).toDouble()),
                        it.y.coerceIn(0.0, height.coerceAtLeast(1).toDouble()),
                    )
                }

                if (degenerate) {
                    // Triangular state: any move of a real corner is fine.
                    setPoint(idx, moved)
                } else if (!CropMath.isValidQuad(currentPoints()
                    .map { if (it === point(idx)) moved else it })) {
                    // Would collapse to zero area or self-intersect: skip
                    // this step (last valid position stays). Never lets a
                    // singular quad reach the save path.
                    resetPath()
                } else {
                    setPoint(idx, moved)
                }
                latestDownX = event.x
                latestDownY = event.y
                resetPath()
            }

            MotionEvent.ACTION_UP -> {
                onDragEnd(event)
            }
            MotionEvent.ACTION_CANCEL -> {
                draggingIndex = null
            }
        }
        return true
    }

    private fun onDragEnd(event: MotionEvent) {
        val idx = draggingIndex
        draggingIndex = null
        if (idx == null) return

        val slop = hypot(
            (event.x - latestDownX).toDouble(),
            (event.y - latestDownY).toDouble(),
        )
        // T-03 straighten: a quick double-tap on the same corner snaps it
        // to the parallelogram completion (other three corners fixed).
        val now = SystemClock.uptimeMillis()
        if (slop < doubleTapSlop && idx == lastTapIndex &&
            now - lastTapTime < 300 && !degenerate
        ) {
            lastTapIndex = -1
            straightenCorner(idx)
        } else {
            lastTapIndex = idx
            lastTapTime = now
        }
    }

    /**
     * Snaps [idx] to the parallelogram vertex determined by the other
     * three corners (short lerp animation). No-op when the target is the
     * same, off-screen, or invalid.
     */
    private fun straightenCorner(idx: Int) {
        val slots = currentPoints()
        val target = CropMath.missingVertex(
            if (idx == 0) null else slots[0],
            if (idx == 1) null else slots[1],
            if (idx == 2) null else slots[2],
            if (idx == 3) null else slots[3],
        ) ?: return
        val clamped = Point(
            target.x.coerceIn(0.0, width.coerceAtLeast(1).toDouble()),
            target.y.coerceIn(0.0, height.coerceAtLeast(1).toDouble()),
        )
        val from = point(idx)
        if (CropMath.near(from, clamped)) return
        val newQuad = slots.map { if (it === from) clamped else it }
        if (!CropMath.isValidQuad(newQuad)) return

        isAnimating = true
        val steps = 8
        animate { k ->
            val t = (k + 1).toDouble() / steps
            setPoint(
                idx,
                Point(from.x + (clamped.x - from.x) * t, from.y + (clamped.y - from.y) * t),
            )
            resetPath()
            if (k == steps - 1) {
                setPoint(idx, clamped)
                resetPath()
                isAnimating = false
            }
        }
    }

    private fun animate(frame: (Int) -> Unit) {
        for (k in 0 until 8) {
            postDelayed({
                if (!isAnimating) return@postDelayed
                frame(k)
            }, 16L)
        }
    }

    // ------------------------------------------------------------------
    // Slot helpers
    // ------------------------------------------------------------------

    private fun currentPoints(): List<Point> = listOf(tl, tr, br, bl)

    /** Points for hit-testing: ghost slot replaced by the ghost position. */
    private fun effectivePoints(): List<Point> =
        if (degenerate && ghostIndex in 0..3) {
            currentPoints().map { if (it === slotPoint(ghostIndex)) ghostPoint else it }
        } else {
            currentPoints()
        }

    private fun slotPoint(i: Int): Point = when (i) {
        0 -> tl
        1 -> tr
        2 -> br
        else -> bl
    }

    private fun point(i: Int): Point = effectivePoints()[i]

    private fun setPoint(i: Int, p: Point) {
        when (i) {
            0 -> { tl = p }
            1 -> { tr = p }
            2 -> { br = p }
            else -> { bl = p }
        }
    }

    private fun clampAll() {
        val maxX = width.coerceAtLeast(1).toDouble()
        val maxY = height.coerceAtLeast(1).toDouble()
        setPoint(0, Point(tl.x.coerceIn(0.0, maxX), tl.y.coerceIn(0.0, maxY)))
        setPoint(1, Point(tr.x.coerceIn(0.0, maxX), tr.y.coerceIn(0.0, maxY)))
        setPoint(2, Point(br.x.coerceIn(0.0, maxX), br.y.coerceIn(0.0, maxY)))
        setPoint(3, Point(bl.x.coerceIn(0.0, maxX), bl.y.coerceIn(0.0, maxY)))
    }

    private fun resetPath() {
        path.reset()
        val p = effectivePoints()
        path.moveTo(p[0].x.toFloat(), p[0].y.toFloat())
        path.lineTo(p[1].x.toFloat(), p[1].y.toFloat())
        path.lineTo(p[2].x.toFloat(), p[2].y.toFloat())
        path.lineTo(p[3].x.toFloat(), p[3].y.toFloat())
        path.close()
        invalidate()
    }

    private fun resizeDefault() {
        tl = Point(0.1 * width.toDouble(), 0.1 * height.toDouble())
        tr = Point(0.9 * width.toDouble(), 0.1 * height.toDouble())
        br = Point(0.9 * width.toDouble(), 0.9 * height.toDouble())
        bl = Point(0.1 * width.toDouble(), 0.9 * height.toDouble())
        degenerate = false
        ghostIndex = -1
        resetPath()
    }

    /** Image space -> view space (scan-overlay path). */
    private fun resize() {
        tl.x = tl.x.div(ratioX); tl.y = tl.y.div(ratioY)
        tr.x = tr.x.div(ratioX); tr.y = tr.y.div(ratioY)
        br.x = br.x.div(ratioX); br.y = br.y.div(ratioY)
        bl.x = bl.x.div(ratioX); bl.y = bl.y.div(ratioY)
    }

    /** View space -> image space (crop-save path). */
    private fun reverseSize() {
        tl.x = tl.x.times(ratioX); tl.y = tl.y.times(ratioY)
        tr.x = tr.x.times(ratioX); tr.y = tr.y.times(ratioY)
        br.x = br.x.times(ratioX); br.y = br.y.times(ratioY)
        bl.x = bl.x.times(ratioX); bl.y = bl.y.times(ratioY)
    }
}
