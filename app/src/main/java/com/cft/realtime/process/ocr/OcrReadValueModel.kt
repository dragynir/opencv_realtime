package com.cft.realtime.process.ocr

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.cft.realtime.process.model.AnalyzerUtils
import com.cft.realtime.process.model.Model

//Модель чтения данных с счётчика
class OcrReadValueModel(assets: AssetManager): OcrModel, Model() {

    //Список цифр
    private val symbols = arrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')

    var OUTPUT_STEPS: Int = 32
    var SYMBOLS_COUNT: Int = 11

    //Инициализация параметров
    init {
        MODEL_FILENAME = "ocr_value_i128_1024_o32_14lite_optimize_size.tflite"
        INPUT_H = 128
        INPUT_W = 1024
        OUTPUT_STEPS = 32
        SYMBOLS_COUNT = 11
        CHANNELS_COUNT = 3
        BATCH_SIZE = 1
        BYTES_PER_POINT = 4
        TYPES_COUNT = SYMBOLS_COUNT * OUTPUT_STEPS

        USE_GPU = false //crashes on true

        init(assets)
    }

    //Выдаёт значения счётчика по картинке
    override fun getResultValue(inputBitmap: Bitmap): String{

        //Приводим к серому
        AnalyzerUtils.rgbToGray(
                inputBitmap
        )

        //Анализируем картинку и получаем Float матрицу
        val ctcMatrix = getCtcMatrix(inputBitmap)
        val stringBuilder = StringBuilder()

        //Получаем строку ctc
        val valuesList =
                AnalyzerUtils.decodeCtcMatrix(
                        ctcMatrix,
                        symbols,
                        OUTPUT_STEPS,
                        SYMBOLS_COUNT - 1
                )

        //Загружаем значения в stringBuilder
        for (value in valuesList) {
            stringBuilder.append(value)
        }

        //Выдаём обычную строку
        return stringBuilder.toString()
    }

    //Анализирует картинку и выдаёт матрицу
    private fun getCtcMatrix(bitmap: Bitmap): Array<FloatArray>{

        //Создаём картинку (масштабирование методом ближайшего соседа)
        val inputBitmap = Bitmap.createScaledBitmap(bitmap,
                INPUT_W,
                INPUT_H, true)

        //Устанавливаем буферы на начало
        output.rewind()
        imageBuffer.rewind()

        //Копировать картинку в ByteBuffer
        AnalyzerUtils.imageToFloatBuffer(
                inputBitmap,
                imageBuffer
        )

        //Запуск модели
        run()

        //Преобразуем ByteBuffer в Float матрицу
        return AnalyzerUtils.decodeOcrOutput(
                output,
                OUTPUT_STEPS,
                SYMBOLS_COUNT,
                BYTES_PER_POINT
        )
    }

}