package com.sample.edgedetection.processor

import org.opencv.core.Point
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Pure geometry helpers for the 4-corner crop editor (T-03).
 *
 * Everything here is free of Android/OpenCV-native calls so it can be unit
 * tested on the JVM. Points are plain (x, y) pairs in *any* consistent
 * coordinate space (view pixels or image pixels).
 *
 * Fixes the T-02 root cause: `sortPoints` used to assign the 4 canonical
 * slots with 4 independent min/max metrics that could pick the SAME physical
 * point twice, yielding a degenerate quad like [A, B, C, C] (a "3-corner"
 * crop). The version here never returns a duplicate vertex.
 */
@Suppress("MagicNumber")
object CropMath {

    const val EPS = 1e-3

    // ------------------------------------------------------------------
    // Deduplication / convex hull
    // ------------------------------------------------------------------

    /** Removes points that are (nearly) identical, keeping the first seen. */
    fun dedupe(points: List<Point>, eps: Double = EPS): List<Point> {
        val out = ArrayList<Point>(points.size)
        for (p in points) {
            if (out.none { near(it, p, eps) }) out.add(Point(p.x, p.y))
        }
        return out
    }

    fun near(a: Point, b: Point, eps: Double = EPS): Boolean =
        abs(a.x - b.x) <= eps && abs(a.y - b.y) <= eps

    fun distance(a: Point, b: Point): Double =
        sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

    /**
     * Andrew's monotone chain convex hull, returned counter-clockwise,
     * without collinear points.
     */
    fun convexHull(points: List<Point>): List<Point> {
        val pts = dedupe(points).sortedWith(compareBy({ it.y }, { it.x }))
        if (pts.size <= 2) return pts

        fun cross(o: Point, a: Point, b: Point): Double =
            (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

        val lower = ArrayList<Point>()
        for (p in pts) {
            while (lower.size >= 2 &&
                cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0.0
            ) lower.removeAt(lower.size - 1)
            lower.add(p)
        }
        val upper = ArrayList<Point>()
        for (p in pts.reversed()) {
            while (upper.size >= 2 &&
                cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0.0
            ) upper.removeAt(upper.size - 1)
            upper.add(p)
        }
        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        return lower + upper
    }

    // ------------------------------------------------------------------
    // Quad area / validity
    // ------------------------------------------------------------------

    /** Absolute shoelace area of a polygon (order-independent). */
    fun quadArea(points: List<Point>): Double {
        if (points.size < 3) return 0.0
        var twice = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            twice += a.x * b.y - b.x * a.y
        }
        return abs(twice) / 2.0
    }

    private fun d(a: Point, b: Point, c: Point): Double =
        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    private fun segmentsProperlyIntersect(p: Point, q: Point, r: Point, s: Point): Boolean {
        val d1 = d(p, q, r)
        val d2 = d(p, q, s)
        val d3 = d(r, s, p)
        val d4 = d(r, s, q)
        return (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
            ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0)))
    }

    private fun isSimpleQuad(quad: List<Point>): Boolean {
        if (quad.size != 4) return false
        if (dedupe(quad).size != 4) return false
        if (quadArea(quad) < EPS) return false
        // opposite edges (0,1)x(2,3) and (1,2)x(3,0) must not cross
        return !segmentsProperlyIntersect(quad[0], quad[1], quad[2], quad[3]) &&
            !segmentsProperlyIntersect(quad[1], quad[2], quad[3], quad[0])
    }

    /**
     * True when [quad] is a usable crop quad: 4 distinct vertices, real area,
     * no self-intersection. Guards the singular-matrix save path of
     * `cropPicture` (the "corrupt/inverted crop" risk from T-02).
     */
    fun isValidQuad(quad: List<Point>): Boolean = isSimpleQuad(quad)

    /**
     * Minimum absolute area (in the given coord space) a quad must cover to
     * be croppable: max(EPS, fraction of the full image area).
     */
    fun minCroppableArea(spanX: Double, spanY: Double, fraction: Double = 0.0025): Double =
        maxOf(EPS, spanX * spanY * fraction)

    // ------------------------------------------------------------------
    // sortPoints — the T-02 fix
    // ------------------------------------------------------------------

    /**
     * Canonical TL / TR / BR / BL ordering of a detection result.
     *
     * Contract:
     *  - returns `null` if a proper simple quad cannot be formed
     *    (<= 2 distinct points, all collinear);
     *  - otherwise returns EXACTLY 4 distinct, non-crossing points in
     *    [TL, TR, BR, BL] order.
     *
     * For 3 distinct points (the near-triangle input that used to produce
     * [A, B, C, C]), the 4th vertex is recovered by parallelogram
     * completion (centroid-nearest candidate) — never a duplicate.
     */
    fun sortPoints(points: List<Point>): List<Point>? {
        val input = dedupe(points)
        if (input.size < 3) return null

        val hull = convexHull(input)
        return when (hull.size) {
            4 -> canonicalOrder(hull)
            3 -> bestParallelogramCompletion(hull)?.let { canonicalOrder(it) }
            else -> null
        }
    }

    /**
     * Takes a cyclically-ordered 4-vertex hull and emits it in
     * [TL, TR, BR, BL] slot order. Works for either winding direction.
     */
    private fun canonicalOrder(hull4: List<Point>): List<Point> {
        val tlIdx = hull4.indices.minByOrNull { hull4[it].x + hull4[it].y } ?: 0
        val tl = hull4[tlIdx]
        val br = hull4[(tlIdx + 2) % 4]          // opposite vertex
        val n1 = hull4[(tlIdx + 1) % 4]          // TL's two neighbours
        val n2 = hull4[(tlIdx + 3) % 4]
        // of TL's neighbours, TR is the one with the larger (x - y)
        return if ((n1.x - n1.y) >= (n2.x - n2.y)) {
            listOf(tl, n1, br, n2)
        } else {
            listOf(tl, n2, br, n1)
        }
    }

    /** Parallelogram-completion candidates for the 3 known points. */
    private fun completionCandidates(tri: List<Point>): List<Point> = listOf(
        Point(tri[0].x + tri[1].x - tri[2].x, tri[0].y + tri[1].y - tri[2].y),
        Point(tri[0].x + tri[2].x - tri[1].x, tri[0].y + tri[2].y - tri[1].y),
        Point(tri[1].x + tri[2].x - tri[0].x, tri[1].y + tri[2].y - tri[0].y),
    )

    private fun isUsableCompletion(tri: List<Point>, d: Point): Boolean =
        tri.none { near(it, d) } &&
            isSimpleQuad(orderAroundCentroid(listOf(tri[0], tri[1], tri[2], d)))

    /**
     * Given 3 distinct points, picks the parallelogram-completion point
     * closest to the triple's centroid and returns the quad in cyclic
     * order, or null when no valid completion exists. (All 3 candidate
     * parallelograms have the same area, so area cannot discriminate; the
     * centroid-nearest choice keeps the completed quad near the observed
     * point cloud instead of swinging it away, e.g. off the top edge.)
     */
    private fun bestParallelogramCompletion(tri: List<Point>): List<Point>? {
        val centroid = Point(tri.sumOf { it.x } / 3.0, tri.sumOf { it.y } / 3.0)
        val best = completionCandidates(tri)
            .filter { isUsableCompletion(tri, it) }
            .minByOrNull { distance(it, centroid) } ?: return null
        return orderAroundCentroid(listOf(tri[0], tri[1], tri[2], best))
    }

    /**
     * Parallelogram-completion point for the 3 known corners of a
     * near-triangle (the T-02 `[A,B,C,C]` case). Returns the 4th vertex
     * such that the 4 points form a valid simple quadrilateral, or null
     * when the 3 points are degenerate (collinear / duplicated).
     *
     * Order of [known] does not matter. Used both by `sortPoints` and by
     * the crop view's ghost-handle (tap-to-place) recovery.
     */
    fun completionPoint(known: List<Point>): Point? {
        if (known.size != 3) return null
        return bestParallelogramCompletion(known)
            ?.firstOrNull { new -> known.none { k -> near(k, new) } }
    }

    /** Cyclic ordering of arbitrary points around their centroid (CCW). */
    private fun orderAroundCentroid(points: List<Point>): List<Point> {
        val cx = points.sumOf { it.x } / points.size
        val cy = points.sumOf { it.y } / points.size
        return points.sortedBy { p -> atan2(p.y - cy, p.x - cx) }
    }

    // ------------------------------------------------------------------
    // Hit-testing (fixes the abs(dx*dy) "wrong corner ~31% of touches" bug)
    // ------------------------------------------------------------------

    /**
     * Index of the corner within [radiusPx] of (downX, downY) that has the
     * smallest EUCLIDEAN distance, or null when no corner is in reach.
     */
    fun pickNearestCorner(
        corners: List<Point>,
        downX: Double,
        downY: Double,
        radiusPx: Double,
    ): Int? {
        var bestIdx: Int? = null
        var bestDist = radiusPx + EPS
        corners.forEachIndexed { i, c ->
            val dist = distance(c, Point(downX, downY))
            if (dist <= bestDist) {
                bestDist = dist
                bestIdx = i
            }
        }
        return bestIdx
    }

    // ------------------------------------------------------------------
    // Straighten (shear) math — "drag to straighten"
    // ------------------------------------------------------------------

    /**
     * Least-squares projection of pointer target [t] onto the parallelogram
     * spanned by [u] and [v] anchored at [origin]: returns (a, b) such that
     * `origin + a*u + b*v` is the straightened corner position.
     *
     * Null when the basis is degenerate (u and v collinear) — the caller
     * should then let the corner move freely.
     */
    fun parallelogramSolve(origin: Point, u: Point, v: Point, t: Point): Pair<Double, Double>? {
        val uu = u.x * u.x + u.y * u.y
        val vv = v.x * v.x + v.y * v.y
        val uv = u.x * v.x + u.y * v.y
        val det = uu * vv - uv * uv
        if (det < EPS) return null
        val lx = t.x - origin.x
        val ly = t.y - origin.y
        val lu = lx * u.x + ly * u.y
        val lv = lx * v.x + ly * v.y
        val a = (lu * vv - lv * uv) / det
        val b = (lv * uu - lu * uv) / det
        return a to b
    }

    fun applyParallelogram(origin: Point, u: Point, v: Point, ab: Pair<Double, Double>): Point =
        Point(
            origin.x + ab.first * u.x + ab.second * v.x,
            origin.y + ab.first * u.y + ab.second * v.y,
        )

    // ------------------------------------------------------------------
    // Missing-vertex recovery ("tap to re-add")
    // ------------------------------------------------------------------

    /**
     * With 3 corners known (exactly one null) and slot order TL,TR,BR,BL,
     * the parallelogram position of the missing vertex (opposite slots
     * satisfy A + C = B + D). Used both for the ghost-handle proposal and
     * for the double-tap "straighten" snap.
     */
    fun missingVertex(
        tl: Point?, tr: Point?, br: Point?, bl: Point?,
    ): Point? {
        return when {
            br == null && tl != null && tr != null && bl != null ->
                Point(tr.x + bl.x - tl.x, tr.y + bl.y - tl.y)
            bl == null && tl != null && tr != null && br != null ->
                Point(tl.x + br.x - tr.x, tl.y + br.y - tr.y)
            tr == null && tl != null && br != null && bl != null ->
                Point(tl.x + br.x - bl.x, tl.y + br.y - bl.y)
            tl == null && tr != null && br != null && bl != null ->
                Point(tr.x + bl.x - br.x, tr.y + bl.y - br.y)
            else -> null
        }
    }
}
