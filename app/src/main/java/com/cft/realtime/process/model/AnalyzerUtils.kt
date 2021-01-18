package com.cft.realtime.process.model

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Color
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import kotlin.jvm.Throws


object AnalyzerUtils {

    private val random = Random(System.currentTimeMillis())

    @Throws(IOException::class)
    fun loadModelFile(assets: AssetManager?, modelFilename: String): MappedByteBuffer {

        if(assets == null){

            val file = File(
                "C:\\A_ML\\CFT_ENERGY\\MetersApp\\MetersApp\\app\\src\\main\\assets\\$modelFilename"
            )
            val stream = FileInputStream(
                file
            )
            return stream.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
        }


        val fileDescriptor = assets.openFd(modelFilename)
        val fileChannel = FileInputStream(fileDescriptor.fileDescriptor).channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    // TODO generate palette for multiple classes
    fun createPalette(outputLabels: Int): IntArray{
        val segmentColors = IntArray(outputLabels + 1)
        for (i in 0 until outputLabels + 1) {
            when (i) {
                0 ->
                    segmentColors[i] = Color.argb(120, 255, 0, 0)
                1 ->
                    segmentColors[i] = Color.argb(120, 0, 0, 255)
                2 ->
                    segmentColors[i] = Color.argb(120, 0, 255, 0)
                else ->
                    segmentColors[i] = Color.argb(
                        120,
                        (255 * random.nextFloat()).toInt(),
                        (255 * random.nextFloat()).toInt(),
                        (255 * random.nextFloat()).toInt()
                    )
            }
        }
        segmentColors[outputLabels] = Color.TRANSPARENT
        return segmentColors
    }
    fun imageToFloatBuffer(bitmap: Bitmap, imgBuffer: ByteBuffer, normalize: Boolean = true){
        imageToFloatBufferOld(bitmap, imgBuffer, normalize)
    }

    //20ms
    private fun imageToFloatBufferOld(bitmap: Bitmap, imgBuffer: ByteBuffer, normalize: Boolean = true){

        val inputWidth = bitmap.width
        val inputHeight = bitmap.height
        val norm = if (normalize) 255.0f else 1.0f

        val inputPixelValues = IntArray(inputWidth * inputHeight)

        bitmap.getPixels(inputPixelValues, 0,
            inputWidth, 0, 0,
            inputWidth,
            inputHeight
        )

        var pixel = 0
        for (i in 0 until inputWidth) {
            for (j in 0 until inputHeight) {
                if (pixel >= inputPixelValues.size) {
                    break
                }
                val value = inputPixelValues[pixel++]
                imgBuffer.putFloat(((value shr 16 and 0xFF) / norm))
                imgBuffer.putFloat(((value shr 8 and 0xFF)  / norm))
                imgBuffer.putFloat(((value and 0xFF) / norm))
            }
        }
    }

    private fun fillZeroes(array: Array<IntArray>) {
        var r = 0
        while (r < array.size) {
            Arrays.fill(array[r], 0)
            r++
        }
    }


    fun decodeSegmentationMasks(outputBuffer: ByteBuffer, segmentBits: Array<IntArray>, w: Int, h: Int,
                                outputLabels: Int, thresholds:FloatArray, palette: IntArray, bytesPerPoint: Int): Bitmap{
        val maskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        fillZeroes(segmentBits)
        var score: Float

        // TODO WHAT: reorder cycle
        for (x in 0 until w) {
            for (y in 0 until h) {
                segmentBits[x][y] = outputLabels
                for (c in 0 until outputLabels) {
                    score = outputBuffer.getFloat((y * w * outputLabels + x * outputLabels + c) * bytesPerPoint)
                    if(score > thresholds[c]){
                        segmentBits[x][y] = c
                    }
                }
                val pixelColor = palette[segmentBits[x][y]]
                maskBitmap.setPixel(x, y, pixelColor)
            }
        }

        return maskBitmap
    }

    fun decodeOcrOutput(output: ByteBuffer, outputSteps: Int, symbolsCount: Int, bytesPerPoint: Int): Array<FloatArray>{
        val outputMatrix = Array(outputSteps) { FloatArray(
            symbolsCount
        ) }

        var value: Float
        for (y in 0 until outputSteps) {
            for (x in 0 until symbolsCount) {
                value = output.getFloat((y * symbolsCount + x) * bytesPerPoint)
                outputMatrix[y][x] = value
            }
        }
        return outputMatrix
    }

    fun decodeCtcMatrix(ctc_res: Array<FloatArray>, symbols: Array<Char>, length: Int, space_val: Int): ArrayList<Char> {

        var value: Float
        val bufArray = IntArray(length)
        for (i in ctc_res.indices) {
            var argMax = 0
            var maxVal = 0f

            for (j in ctc_res[i].indices) {
                value = ctc_res[i][j]
                if (maxVal < value) {
                    maxVal = value
                    argMax = j
                }
            }
            bufArray[i] = argMax
        }
        val result = ArrayList<Char>()

        var curVal: Int
        curVal = bufArray[0]
        for (i in 1 until length) {
            if ((curVal != bufArray[i]) and (curVal != space_val)) {
                result.add(symbols[curVal])
            }
            curVal = bufArray[i]
        }
        return result
    }


    fun rgbToGray(bitmap: Bitmap){
        val m = Mat()
        Utils.bitmapToMat(bitmap, m)
        Imgproc.cvtColor(m, m, Imgproc.COLOR_BGR2GRAY)
        Imgproc.cvtColor(m, m, Imgproc.COLOR_GRAY2RGBA)
        Utils.matToBitmap(m, bitmap)
    }


}