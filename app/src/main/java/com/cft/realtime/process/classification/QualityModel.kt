package com.cft.realtime.process.classification

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.cft.realtime.process.model.AnalyzerUtils
import com.cft.realtime.process.model.Model

class QualityModel(assets: AssetManager): Model() {
    private val types = arrayOf("defocused_blurred", "motion_blurred", "sharp")

    init {
        MODEL_FILENAME = "mobile_500K_95_sigmoid.tflite"
        INPUT_H = 256
        INPUT_W = 256
        TYPES_COUNT = 3
        CHANNELS_COUNT = 3
        BATCH_SIZE = 1
        BYTES_PER_POINT = 4

        init(assets)
    }

    //Даёт оценку изображения разбиением на 3 класса "defocused_blurred", "motion_blurred" и "sharp"
    fun getResultValue(inputBitmap: Bitmap): String {
        
        //Масштабируем картинку
        val bitmap = Bitmap.createScaledBitmap(inputBitmap, INPUT_W, INPUT_H, true)

        //Устанавливаем указатели буферов на нулевую позицию 
        output.rewind()
        imageBuffer.rewind()

        //Скопировать картинку в ByteBuffer
        AnalyzerUtils.imageToFloatBuffer(bitmap, imageBuffer, true)

        //Запуск модели  
        run()

        //Определяем наиболее вероятный класс качества изображения
        var maxType = 0
        val outputFloat =
                floatArrayOf(output.getFloat(0), output.getFloat(4), output.getFloat(4 * 2))
        for (i in 1 until TYPES_COUNT)
            if (outputFloat[i] > outputFloat[maxType])
                maxType = i

        return types[maxType]
    }
}