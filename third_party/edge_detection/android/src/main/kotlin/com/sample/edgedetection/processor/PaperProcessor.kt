package com.sample.edgedetection.processor

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

const val TAG: String = "PaperProcessor"

fun processPicture(previewFrame: Mat): Corners? {
    val contours = findContours(previewFrame)
    return getCorners(contours, previewFrame.size())
}

fun cropPicture(picture: Mat, pts: List<Point>): Mat {
    pts.forEach { Log.i(TAG, "point: $it") }

    // T-03 / no-data-loss guard: a degenerate quad (duplicate vertices, zero
    // area, self-intersection) makes getPerspectiveTransform singular and
    // produced inverted/blank crops. Instead of corrupting the output, fall
    // back to the full image so the user always gets the whole page.
    val safePts: List<Point> = if (CropMath.isValidQuad(pts)) {
        pts
    } else {
        Log.w(TAG, "cropPicture: invalid quad, falling back to full image")
        listOf(
            Point(0.0, 0.0),
            Point(picture.width().toDouble(), 0.0),
            Point(picture.width().toDouble(), picture.height().toDouble()),
            Point(0.0, picture.height().toDouble()),
        )
    }

    val tl = safePts[0]
    val tr = safePts[1]
    val br = safePts[2]
    val bl = safePts[3]

    val widthA = sqrt((br.x - bl.x).pow(2.0) + (br.y - bl.y).pow(2.0))
    val widthB = sqrt((tr.x - tl.x).pow(2.0) + (tr.y - tl.y).pow(2.0))

    val dw = max(widthA, widthB)
    val maxWidth = max(1, java.lang.Double.valueOf(dw).toInt())


    val heightA = sqrt((tr.x - br.x).pow(2.0) + (tr.y - br.y).pow(2.0))
    val heightB = sqrt((tl.x - bl.x).pow(2.0) + (tl.y - bl.y).pow(2.0))

    val dh = max(heightA, heightB)
    val maxHeight = max(1, java.lang.Double.valueOf(dh).toInt())

    val croppedPic = Mat(maxHeight, maxWidth, CvType.CV_8UC4)

    val srcMat = Mat(4, 1, CvType.CV_32FC2)
    val dstMat = Mat(4, 1, CvType.CV_32FC2)

    srcMat.put(0, 0, tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
    dstMat.put(0, 0, 0.0, 0.0, dw, 0.0, dw, dh, 0.0, dh)

    val m = Imgproc.getPerspectiveTransform(srcMat, dstMat)

    Imgproc.warpPerspective(picture, croppedPic, m, croppedPic.size())
    m.release()
    srcMat.release()
    dstMat.release()
    Log.i(TAG, "crop finish")
    return croppedPic
}

fun enhancePicture(src: Bitmap?): Bitmap {
    val srcMat = Mat()
    Utils.bitmapToMat(src, srcMat)
    Imgproc.cvtColor(srcMat, srcMat, Imgproc.COLOR_RGBA2GRAY)
    Imgproc.adaptiveThreshold(
        srcMat,
        srcMat,
        255.0,
        Imgproc.ADAPTIVE_THRESH_MEAN_C,
        Imgproc.THRESH_BINARY,
        15,
        15.0
    )
    val result = Bitmap.createBitmap(src?.width ?: 1080, src?.height ?: 1920, Bitmap.Config.RGB_565)
    Utils.matToBitmap(srcMat, result, true)
    srcMat.release()
    return result
}

private fun findContours(src: Mat): List<MatOfPoint> {

    val grayImage: Mat
    val cannedImage: Mat
    val kernel: Mat = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(9.0, 9.0))
    val dilate: Mat
    val size = Size(src.size().width, src.size().height)
    grayImage = Mat(size, CvType.CV_8UC4)
    cannedImage = Mat(size, CvType.CV_8UC1)
    dilate = Mat(size, CvType.CV_8UC1)

    Imgproc.cvtColor(src, grayImage, Imgproc.COLOR_BGR2GRAY)
    Imgproc.GaussianBlur(grayImage, grayImage, Size(5.0, 5.0), 0.0)
    Imgproc.threshold(grayImage, grayImage, 20.0, 255.0, Imgproc.THRESH_TRIANGLE)
    Imgproc.Canny(grayImage, cannedImage, 75.0, 200.0)
    Imgproc.dilate(cannedImage, dilate, kernel)
    val contours = ArrayList<MatOfPoint>()
    val hierarchy = Mat()
    Imgproc.findContours(
        dilate,
        contours,
        hierarchy,
        Imgproc.RETR_TREE,
        Imgproc.CHAIN_APPROX_SIMPLE
    )

    val filteredContours = contours
        .filter { p: MatOfPoint -> Imgproc.contourArea(p) > 100e2 }
        .sortedByDescending { p: MatOfPoint -> Imgproc.contourArea(p) }
        .take(25)

    hierarchy.release()
    grayImage.release()
    cannedImage.release()
    kernel.release()
    dilate.release()

    return filteredContours
}

private fun getCorners(contours: List<MatOfPoint>, size: Size): Corners? {
    val indexTo: Int = when (contours.size) {
        in 0..5 -> contours.size - 1
        else -> 4
    }
    for (index in 0..contours.size) {
        if (index in 0..indexTo) {
            val c2f = MatOfPoint2f(*contours[index].toArray())
            val peri = Imgproc.arcLength(c2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx, 0.03 * peri, true)
            val points = approx.toArray().asList()
            val convex = MatOfPoint()
            approx.convertTo(convex, CvType.CV_32S)
            // select biggest 4 angles polygon
            if (points.size >= 3 && Imgproc.isContourConvex(convex)) {
                // T-03: CropMath.sortPoints never returns a duplicated vertex
                // (the old min/max metric collision produced [A,B,C,C] quads);
                // a near-triangle contour is completed into a real quad.
                // On failure, fall through to the next contour / null result.
                val foundPoints = CropMath.sortPoints(points) ?: continue
                return Corners(foundPoints, size)
            }
        } else {
            return null
        }
    }

    return null
}

