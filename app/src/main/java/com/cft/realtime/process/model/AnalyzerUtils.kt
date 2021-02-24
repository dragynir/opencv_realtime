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

//Утилита для анализа
object AnalyzerUtils {

    private val random = Random(System.currentTimeMillis())

    //Загрузка файла модели 
    @Throws(IOException::class)
    fun loadModelFile(assets: AssetManager?, modelFilename: String): MappedByteBuffer {

        //Если AssetManager не задан
        if(assets == null){

            val file = File(
                "C:\\A_ML\\CFT_ENERGY\\MetersApp\\MetersApp\\app\\src\\main\\assets\\$modelFilename"
            )
            val stream = FileInputStream(
                file
            )
            //Загружает область файла (здесь непосредственно весь файл) этого канала непосредственно в память.
            return stream.channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
        }

        //Если AssetManager задан 

        val fileDescriptor = assets.openFd(modelFilename)
        val fileChannel = FileInputStream(fileDescriptor.fileDescriptor).channel
        //Загружает область файла (здесь заданную часть файла) этого канала непосредственно в память.
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    // TODO generate palette for multiple classes
    fun createPalette(outputLabels: Int): IntArray{
        val segmentColors = IntArray(outputLabels + 1)
        
        
        //Инициализация палитры (специфическая)
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

    //Копировать картинку в ByteBuffer (мечты о новой реализации)
    fun imageToFloatBuffer(bitmap: Bitmap, imgBuffer: ByteBuffer, normalize: Boolean = true){
        imageToFloatBufferOld(bitmap, imgBuffer, normalize)
    }

    //Копировать картинку в ByteBuffer 
    //20ms
    private fun imageToFloatBufferOld(bitmap: Bitmap, imgBuffer: ByteBuffer, normalize: Boolean = true){

        //Вспомогательные переменные 
        val inputWidth = bitmap.width
        val inputHeight = bitmap.height
        val norm = if (normalize) 255.0f else 1.0f

        //Перевести картинку (2D) в массив (1D)
        val inputPixelValues = IntArray(inputWidth * inputHeight)
        bitmap.getPixels(inputPixelValues, 0,
            inputWidth, 0, 0,
            inputWidth,
            inputHeight
        )

        //Непосредственно копирование
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

    //Занулить массив (2D)
    private fun fillZeroes(array: Array<IntArray>) {
        var r = 0
        while (r < array.size) {
            Arrays.fill(array[r], 0)
            r++
        }
    }

    //Преобразовать битовый буфер в картинку (маску)
    fun decodeSegmentationMasks(outputBuffer: ByteBuffer, segmentBits: Array<IntArray>, w: Int, h: Int,
                                outputLabels: Int, thresholds:FloatArray, palette: IntArray, bytesPerPoint: Int): Bitmap{
        //Создаём маску
        val maskBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        //Заниуляем массив
        fillZeroes(segmentBits)
        var score: Float

        // TODO WHAT: reorder cycle
        for (x in 0 until w) {
            for (y in 0 until h) {
                segmentBits[x][y] = outputLabels
                
                //перебор по уровням
                for (c in 0 until outputLabels) {
                    //Считывание буфера в соответствии с позициями в матрице и размером ячеек (bytesPerPoint)
                    score = outputBuffer.getFloat((y * w * outputLabels + x * outputLabels + c) * bytesPerPoint)
                    //Фильтр по уровню
                    if(score > thresholds[c]){
                        segmentBits[x][y] = c
                    }
                }

                //Определение цвета
                val pixelColor = palette[segmentBits[x][y]]
                //Помещение пикселя в картинку
                maskBitmap.setPixel(x, y, pixelColor)
            }
        }

        return maskBitmap
    }

    //ByteBuffer osr в Float Матрицу
    fun decodeOcrOutput(output: ByteBuffer, outputSteps: Int, symbolsCount: Int, bytesPerPoint: Int): Array<FloatArray>{
        
        //Создаём выходную матрицу
        val outputMatrix = Array(outputSteps) { FloatArray(
            symbolsCount
        ) }

        var value: Float
        for (y in 0 until outputSteps) {
            for (x in 0 until symbolsCount) {
                //Вынимаем биты в соответствии с позициями в матрице и размером ячеек (bytesPerPoint)
                value = output.getFloat((y * symbolsCount + x) * bytesPerPoint)
                outputMatrix[y][x] = value
            }
        }
        return outputMatrix
    }

    //Float матрицу в "строку" ctc 
    fun decodeCtcMatrix(ctc_res: Array<FloatArray>, symbols: Array<Char>, length: Int, space_val: Int): ArrayList<Char> {

        var value: Float
        val bufArray = IntArray(length)

        //Формируем массив индексов максимальных элементов массивов строк (индекс i бежит по сторкам)
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
        
        //Формируем выходной массив
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


    //Чветное в Чёрнобелое (будет 4 одинаковых серых канала)
    fun rgbToGray(bitmap: Bitmap){
        val m = Mat()
        Utils.bitmapToMat(bitmap, m)
        Imgproc.cvtColor(m, m, Imgproc.COLOR_BGR2GRAY)
        Imgproc.cvtColor(m, m, Imgproc.COLOR_GRAY2RGBA)
        Utils.matToBitmap(m, bitmap)
    }


}