package com.cft.realtime.process.model

import android.content.res.AssetManager
import android.graphics.Bitmap


//Проверяет наличие счётчика
class ContainsModel (assets: AssetManager): Model() {
    private val types = arrayOf(true, false)

    //Инициализация 
    init {
        MODEL_FILENAME = "energ_no&yes_9976.tflite"
        INPUT_H = 256
        INPUT_W = 256
        TYPES_COUNT = 2
        CHANNELS_COUNT = 3
        BATCH_SIZE = 1
        BYTES_PER_POINT = 4

        init(assets)
    }

    //По входной картинке определить есть ли счётчик (выдаёт "yes", "no")
    fun getIfContains(inputBitmap: Bitmap): Boolean {

        //Создание отмасштабированого изображения с использованием билинейной фильтрации 
        val bitmap = Bitmap.createScaledBitmap(inputBitmap, INPUT_W, INPUT_H, true)

        //Устанавливаем указатели буферов на нулевую позицию 
        output.rewind()
        imageBuffer.rewind()

        //Скопировать картинку в ByteBuffer 
        AnalyzerUtils.imageToFloatBuffer(bitmap, imageBuffer, true)

        //Запуск модели  
        run()

        //Выбираем наиболее вероятнвй класс (из двух классов)
        var maxType = 0
        //getFloat() считывает 4 байта и преобразует в float
        val outputFloat = floatArrayOf(output.getFloat(0), output.getFloat(4))
        for (i in 1 until TYPES_COUNT)
            if (outputFloat[i] > outputFloat[maxType])
                maxType = i

        return types[maxType]
    }
}