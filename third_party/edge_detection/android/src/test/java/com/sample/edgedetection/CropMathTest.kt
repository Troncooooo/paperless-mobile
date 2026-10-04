package com.sample.edgedetection

import com.sample.edgedetection.processor.CropMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.opencv.core.Point

/**
 * T-03: pure-geometry tests for the crop editor helpers. All of these run
 * on the JVM (no Android classes), so they can be executed with
 * `:edge_detection:testDebugUnitTest`.
 */
class CropMathTest {

    private fun p(x: Double, y: Double) = Point(x, y)

    /**
     * Coordinate-wise equality: org.opencv.core.Point does not guarantee
     * [equals]/[hashCode] across builds, so we compare x/y directly.
     */
    private fun assertClose(expected: Point, actual: Point) {
        assertEquals(expected.x, actual.x, 1e-6)
        assertEquals(expected.y, actual.y, 1e-6)
    }

    // ------------------------------------------------------------------
    // sortPoints — the T-02 root cause (duplicate-vertex quads)
    // ------------------------------------------------------------------

    @Test
    fun cleanRectIsCanonicalizedRegardlessOfInputOrder() {
        val rect = listOf(
            p(150.0, 140.0),   // TL
            p(1500.0, 120.0),  // TR
            p(1550.0, 1900.0), // BR
            p(100.0, 1950.0),  // BL
        )
        for (start in 0..3) {
            // rotate the input so the caller order is arbitrary
            val shuffled = (0 until 4).map { rect[(it + start) % 4] }
            val out = CropMath.sortPoints(shuffled)
            assertNotNull("sortPoints must not return null for a clean quad", out)
            assertClose(rect[0], out!![0]) // TL
            assertClose(rect[1], out[1]) // TR
            assertClose(rect[2], out[2]) // BR
            assertClose(rect[3], out[3]) // BL
        }
    }

    @Test
    fun duplicateQuadABCCIsRepairedToFourDistinctCorners() {
        // The exact failure shape from T-02: [A, B, C, C] (two slots on the
        // same pixel, polygon rendered as a triangle).
        val a = p(100.0, 100.0)
        val b = p(900.0, 100.0)
        val c = p(900.0, 900.0)
        val out = CropMath.sortPoints(listOf(a, b, c, c))
        assertNotNull(out)
        val distinct = CropMath.dedupe(out!!)
        assertEquals("sortPoints must never emit a duplicated vertex", 4, distinct.size)
        assertTrue("repaired quad must be a valid simple quad", CropMath.isValidQuad(out))
        // Parallelogram completion of (100,100),(900,100),(900,900) is (100,900).
        assertClose(p(100.0, 900.0), out[3]) // BL restored
    }

    @Test
    fun nearTriangleFromT02ReproYieldsFourDistinctCorners() {
        // Detection points from the T-02 repro (old slotting produced C twice):
        // (1080,483),(1614,6),(28,759),(1910,1198) -> old: [C, B, D, C]
        val pts = listOf(p(1080.0, 483.0), p(1614.0, 6.0), p(28.0, 759.0), p(1910.0, 1198.0))
        val out = CropMath.sortPoints(pts)
        assertNotNull(out)
        assertEquals(4, CropMath.dedupe(out!!).size)
        assertTrue(CropMath.isValidQuad(out))
    }

    @Test
    fun twoPointsIsNotEnoughForAQuad() {
        assertNull(CropMath.sortPoints(listOf(p(0.0, 0.0), p(10.0, 10.0))))
        assertNull(CropMath.sortPoints(listOf(p(0.0, 0.0))))
        // collinear "triangle" -> no 4th vertex possible
        assertNull(CropMath.sortPoints(listOf(p(0.0, 0.0), p(5.0, 5.0), p(10.0, 10.0))))
    }

    // ------------------------------------------------------------------
    // completionPoint / missingVertex — missing-vertex recovery
    // ------------------------------------------------------------------

    @Test
    fun completionPointOfRectangleTriangle() {
        val known = listOf(p(0.0, 0.0), p(100.0, 0.0), p(100.0, 100.0))
        val c = CropMath.completionPoint(known)
        assertClose(p(0.0, 100.0), c!!)
    }

    @Test
    fun completionPointIsInputOrderIndependent() {
        val known = listOf(p(0.0, 0.0), p(100.0, 0.0), p(100.0, 100.0))
        val expected = CropMath.completionPoint(known)!!
        assertClose(expected, CropMath.completionPoint(listOf(known[2], known[0], known[1]))!!)
        assertClose(expected, CropMath.completionPoint(listOf(known[1], known[2], known[0]))!!)
    }

    @Test
    fun completionPointRejectsCollinearAndDuplicatedInput() {
        assertNull(CropMath.completionPoint(listOf(p(0.0, 0.0), p(5.0, 5.0), p(10.0, 10.0))))
        assertNull(CropMath.completionPoint(listOf(p(0.0, 0.0), p(5.0, 5.0), p(5.0, 5.0))))
        assertNull(CropMath.completionPoint(listOf(p(0.0, 0.0), p(5.0, 5.0))))
    }

    @Test
    fun missingVertexIsParalellogramForEachSlot() {
        val tr = p(100.0, 0.0)
        val br = p(100.0, 100.0)
        val bl = p(0.0, 100.0)
        assertClose(p(0.0, 0.0), CropMath.missingVertex(null, tr, br, bl)!!)  // TL
        assertClose(p(100.0, 0.0), CropMath.missingVertex(p(0.0, 0.0), null, br, bl)!!) // TR = A + C - D = (100,0)
        assertClose(p(100.0, 100.0), CropMath.missingVertex(p(0.0, 0.0), tr, null, bl)!!) // BR
        assertClose(p(0.0, 100.0), CropMath.missingVertex(p(0.0, 0.0), tr, br, null)!!)  // BL
        assertNull(CropMath.missingVertex(null, null, br, bl))
    }

    // ------------------------------------------------------------------
    // pickNearestCorner — the wrong-corner hit-test fix (~31% of touches)
    // ------------------------------------------------------------------

    @Test
    fun axisAlignedTouchGrabsNearestNotFarCorner() {
        // Repro from the T-02 investigation: touch (300,120) is ~152px from
        // TL but the old abs(dx*dy) metric grabbed TR (1200px away).
        val corners = listOf(p(150.0, 140.0), p(1500.0, 120.0), p(1550.0, 1900.0), p(100.0, 1950.0))
        val idx = CropMath.pickNearestCorner(corners, 300.0, 120.0, 200.0)
        assertEquals("axis-aligned touch must pick the nearest corner", 0, idx)
    }

    @Test
    fun touchWellAwayFromAllCornersGrabsNothing() {
        val corners = listOf(p(150.0, 140.0), p(1500.0, 120.0), p(1550.0, 1900.0), p(100.0, 1950.0))
        assertNull(CropMath.pickNearestCorner(corners, 1000.0, 1000.0, 144.0))
    }

    @Test
    fun nearestWithinRadiusWinsOverFainterNeighbors() {
        val corners = listOf(p(10.0, 10.0), p(40.0, 12.0))
        assertEquals(0, CropMath.pickNearestCorner(corners, 15.0, 11.0, 144.0))
        assertEquals(1, CropMath.pickNearestCorner(corners, 45.0, 14.0, 144.0))
    }

    // ------------------------------------------------------------------
    // isValidQuad — the no-data-loss gate before getPerspectiveTransform
    // ------------------------------------------------------------------

    @Test
    fun selfIntersectionIsRejected() {
        // bow-tie quad: opposite edges cross
        val bowtie = listOf(p(0.0, 0.0), p(100.0, 100.0), p(0.0, 100.0), p(100.0, 0.0))
        assertFalse(CropMath.isValidQuad(bowtie))
    }

    @Test
    fun zeroAreaAndDuplicatesAreRejected() {
        assertFalse(CropMath.isValidQuad(listOf(p(0.0, 0.0), p(5.0, 5.0), p(10.0, 10.0), p(3.0, 3.0))))
        // the T-02 [A,B,C,C] shape must never pass
        val a = p(100.0, 100.0)
        val b = p(900.0, 100.0)
        val c = p(900.0, 900.0)
        assertFalse(CropMath.isValidQuad(listOf(a, b, c, c)))
    }

    @Test
    fun simpleConvexQuadsAreAccepted() {
        assertTrue(CropMath.isValidQuad(listOf(p(0.0, 0.0), p(100.0, 0.0), p(100.0, 100.0), p(0.0, 100.0))))
        // slightly skew quadrilateral, both windings
        val q = listOf(p(150.0, 140.0), p(1500.0, 120.0), p(1550.0, 1900.0), p(100.0, 1950.0))
        assertTrue(CropMath.isValidQuad(q))
        assertTrue(CropMath.isValidQuad(q.reversed()))
    }

    // ------------------------------------------------------------------
    // straighten math (parallelogram solve used by double-tap snap)
    // ------------------------------------------------------------------

    @Test
    fun parallellogramSolveOrthogonalBasis() {
        // origin + a*(1,0) + b*(0,1) == t  ->  a = t.x, b = t.y
        val (a, b) = CropMath.parallelogramSolve(
            p(0.0, 0.0), p(1.0, 0.0), p(0.0, 1.0), p(2.5, 3.5),
        )!!
        assertEquals(2.5, a, 1e-9)
        assertEquals(3.5, b, 1e-9)
    }

    @Test
    fun parallellogramSolveShearedBasis() {
        // origin + a*(1,0) + b*(1,1) == t(3,2) -> b=2, a=1
        val (a, b) = CropMath.parallelogramSolve(p(0.0, 0.0), p(1.0, 0.0), p(1.0, 1.0), p(3.0, 2.0))!!
        assertEquals(1.0, a, 1e-9)
        assertEquals(2.0, b, 1e-9)
    }

    @Test
    fun parallellogramRoundTripAndDegenerateBasis() {
        val origin = p(10.0, 20.0)
        val u = p(5.0, 1.0)
        val v = p(1.0, 5.0)
        val ab = CropMath.parallelogramSolve(origin, u, v, p(42.0, 13.0))!!
        val back = CropMath.applyParallelogram(origin, u, v, ab)
        assertEquals(42.0, back.x, 1e-9)
        assertEquals(13.0, back.y, 1e-9)
        // collinear basis -> singular, must be reported as no-solution
        assertNull(CropMath.parallelogramSolve(p(0.0, 0.0), p(1.0, 1.0), p(2.0, 2.0), p(3.0, 3.0)))
    }
}
