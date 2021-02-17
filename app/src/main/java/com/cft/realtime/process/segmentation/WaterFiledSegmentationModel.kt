package com.cft.realtime.process.segmentation

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.cft.realtime.process.model.AnalyzerUtils
import com.cft.realtime.process.model.Model

class WaterFieldSegmentationModel(assets: AssetManager): Model() {

    var OUTPUT_W: Int = 128
    var OUTPUT_H: Int = 128
    var THRESHOLD = 0.7f
    var OUTPUT_LABELS: Int = 1

    init {
        MODEL_FILENAME = "water_field256_256.tflite"
        INPUT_H = 256
        INPUT_W = 256
        OUTPUT_W = 256
        OUTPUT_H = 256
        THRESHOLD = 0.7f
        OUTPUT_LABELS = 1
        CHANNELS_COUNT = 3
        BATCH_SIZE = 1
        BYTES_PER_POINT = 4
        TYPES_COUNT = OUTPUT_H * OUTPUT_W * OUTPUT_LABELS

        USE_GPU = false //crashes on true?

        init(assets)
    }


    fun getMask(image: Bitmap): Bitmap {

        val originalWidth = image.width
        val originalHeight = image.height
        val inputBitmap = Bitmap.createScaledBitmap(image,
            INPUT_W,
            INPUT_H, true)

        //Устанавливаем указатели буферов на нулевую позицию
        output.rewind()
        imageBuffer.rewind()

        //Перевод изображения в буфер
        AnalyzerUtils.imageToFloatBuffer(
            inputBitmap,
            imageBuffer
        )

        //Запуск модели
        run()

        //Создаём палитру
        val palette =
            AnalyzerUtils.createPalette(
                OUTPUT_LABELS
            )


        //Int матрица для decodeSegmentationMasks
        val segmentBits = Array(OUTPUT_W) {
            IntArray(
                OUTPUT_H
            )
        }


        //Преобразовать битовый буфер в картинку (маску)
        val mask =
            AnalyzerUtils.decodeSegmentationMasks(
                output,
                segmentBits,
                OUTPUT_W,
                OUTPUT_H,
                OUTPUT_LABELS,
                floatArrayOf(THRESHOLD),
                palette,
                BYTES_PER_POINT
            )

        //Создаем маску нужных размеров и возвращаем её
        return Bitmap.createScaledBitmap(mask, originalWidth, originalHeight, true)
    }

}