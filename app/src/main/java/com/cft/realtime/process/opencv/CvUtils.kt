package com.cft.realtime.process.opencv

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

object CvUtils {

    fun splitBitmap(bitmap: Bitmap): ArrayList<Mat>{
        val mat = Mat()
        val bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        Utils.bitmapToMat(bmp32, mat)
        val channels = ArrayList<Mat>()
        Core.split(mat, channels)
        return channels
    }

    fun getBiggestContour(image: Mat): Rect {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
                image,
                contours,
                hierarchy,
                Imgproc.RETR_TREE,
                Imgproc.CHAIN_APPROX_SIMPLE
        )

        var boundRect = Rect()
        var bufRect: Rect
        for (i in contours.indices) {
            bufRect = Imgproc.boundingRect(contours[i])

            if (bufRect.area() > boundRect.area()) {
                boundRect = bufRect.clone()
            }
        }
        return boundRect
    }

    private fun swapSizeHW(s: Size){
        val w = s.width
        val h = s.height
        s.width = h
        s.height = w
    }


    fun decodeMinRectParams(angle: Double, size: Size): Double{
        var a = angle
        when {
            angle == -90.0 -> {
                swapSizeHW(size)
                a = 0.0
            }
            angle == -0.0 -> {
                a = 0.0
            }
            angle > -45 -> {
                // a =  -angle
                a = angle
            }
            angle < -45 -> {
                swapSizeHW(size)
                a += 90
            }
        }
        return a
    }

    fun cropMinAreaRect(rect: RotatedRect, fullImage: Mat, decode: Boolean): Mat{
        val center = rect.center
        val size = rect.size
        var angle = rect.angle

        if(decode) {
            angle = decodeMinRectParams(angle, size)
        }

        val m = Imgproc.getRotationMatrix2D(center, angle, 1.0)
        val rotatedImage = Mat()
        Imgproc.warpAffine(fullImage, rotatedImage, m, Size(fullImage.width().toDouble(), fullImage.height().toDouble()))

        val channels = ArrayList<Mat>()
        Core.split(rotatedImage, channels)
        channels.removeAt(3)
        val rgbMat = Mat()
        Core.merge(channels, rgbMat)
        val croppedImage = Mat()
        // it is works with one or three channels
        Imgproc.getRectSubPix(rgbMat, size, center, croppedImage)

        return croppedImage
    }

    fun decodeMinRect(minRect: RotatedRect): RotatedRect{
        val size = minRect.size
        var angle = minRect.angle
        angle = decodeMinRectParams(angle, size)

        minRect.size = size
        minRect.angle = angle
        return minRect
    }


    fun findMinAreaRect(channel: Mat): RotatedRect?{
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(channel, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        if(contours.size == 0){
            return null
        }

        var cnt = contours[0]
        // find biggest contour TODO rewrite algorithm
        if(contours.size > 1){
            for (i in contours.indices) {
                if (Imgproc.contourArea(contours[i]) > Imgproc.contourArea(cnt)) {
                    cnt = contours[i]
                }
            }
        }

        val c = MatOfPoint2f()
        cnt.convertTo(c, CvType.CV_32FC2)

        val peri = Imgproc.arcLength(c, true)
        val poly = MatOfPoint2f()
        Imgproc.approxPolyDP(c, poly, 0.015 * peri, true)
        return Imgproc.minAreaRect(poly)
    }

    fun findMeterFace(mask: Bitmap, fullImage: Bitmap): Bitmap?{

        val matImage = Mat()
        Utils.bitmapToMat(fullImage, matImage)

        val channel = splitBitmap(mask)[0]
        val minRect = findMinAreaRect(channel) ?: return null

        val croppedMat = cropMinAreaRect(minRect, matImage, true)

        val ratio = (croppedMat.size().width * croppedMat.size().height) / (matImage.size().height * matImage.size().height)

        Log.d("RATIO", ratio.toString())

        if(ratio < 0.1){
            Log.d("RATIO", "return")
            return fullImage
        }

        val resultBitmap = Bitmap.createBitmap(croppedMat.cols(), croppedMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(croppedMat, resultBitmap)

        return resultBitmap
    }

    fun applyClahe(image: Bitmap): Bitmap {

        val matImage = Mat()
        val labImage = Mat()
        Utils.bitmapToMat(image, matImage)
        Imgproc.cvtColor(matImage, labImage, Imgproc.COLOR_RGB2Lab)


        val channels = ArrayList<Mat>()
        Core.split(labImage, channels)

        val clahe = Imgproc.createCLAHE()
        clahe.clipLimit = 2.0

        val destImage = Mat()
        clahe.apply(channels[0], destImage)

        destImage.copyTo(channels[0])

        Core.merge(channels, labImage)

        Imgproc.cvtColor(labImage, matImage, Imgproc.COLOR_Lab2RGB)

        Utils.matToBitmap(matImage, image)

        return image
    }


    fun cropImageFromMaskChannel(fullImage: Bitmap, channel: Mat): Bitmap?{
        val matImage = Mat()
        Utils.bitmapToMat(fullImage, matImage)

        val minRect = findMinAreaRect(channel) ?: return null

        if (minRect.size.width == 0.0 ||  minRect.size.height == 0.0){
            return null
        }
        val croppedMat = cropMinAreaRect(minRect, matImage, true)
        val resultBitmap = Bitmap.createBitmap(croppedMat.cols(), croppedMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(croppedMat, resultBitmap)
        return resultBitmap
    }

    fun cropImageFromMaskChannelWithPad(fullImage: Bitmap, channel: Mat, widthRatio: Float, heightRatio: Float, rightRatio: Float, topRatio: Float): Bitmap? {
        val matImage = Mat()
        Utils.bitmapToMat(fullImage, matImage)
        val minRect = findMinAreaRect(channel) ?: return null

        val size = minRect.size
        var angle = minRect.angle
        angle = decodeMinRectParams(angle, size)

        minRect.size = size
        minRect.angle = angle

        minRect.size.width+= minRect.size.width * widthRatio
        minRect.size.height+= minRect.size.height * heightRatio
        minRect.center.x-=minRect.size.width * rightRatio
        minRect.center.y-=minRect.size.height * topRatio


        val croppedMat = cropMinAreaRect(minRect, matImage, false)
        val resultBitmap = Bitmap.createBitmap(croppedMat.cols(), croppedMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(croppedMat, resultBitmap)
        return resultBitmap
    }

}