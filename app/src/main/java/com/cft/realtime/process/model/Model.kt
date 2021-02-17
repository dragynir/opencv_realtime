package com.cft.realtime.process.model

import android.content.res.AssetManager
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer

//По сути это обёртка над моделью распознавания
//В частности это базовый класс для других обёрток моделей
open class Model() {
    lateinit var imageBuffer: ByteBuffer
    lateinit var output: ByteBuffer

    private lateinit var interpreter: Interpreter
    private lateinit var model: MappedByteBuffer
    private lateinit var options: Interpreter.Options
    private var initialized = false
    var USE_GPU = true
    private val numThreads = 2

    //Параметры по умолчанию
    var MODEL_FILENAME = "mobile_500K_95_sigmoid.tflite"
    var INPUT_H: Int = 256
    var INPUT_W: Int = 256
    var TYPES_COUNT: Int = 3
    var CHANNELS_COUNT: Int = 3
    var BATCH_SIZE: Int = 1
    var BYTES_PER_POINT: Int = 4

    //Инициализация модели
    fun init(assets: AssetManager) {

        //Выделяем битовый буфер нужного размера
        imageBuffer = ByteBuffer.allocateDirect(BATCH_SIZE * INPUT_H * INPUT_W * CHANNELS_COUNT * BYTES_PER_POINT)
        
        //Указываем собственный порядок байтов оборудования, на котором работает эта виртуальная машина Java
        imageBuffer.order(ByteOrder.nativeOrder())

        //Аналогично
        output = ByteBuffer.allocateDirect(BATCH_SIZE * TYPES_COUNT * BYTES_PER_POINT)
        output.order(ByteOrder.nativeOrder())

        options = Interpreter.Options()
        val compatList = CompatibilityList()

        if(USE_GPU){
            if(compatList.isDelegateSupportedOnThisDevice){
                Log.d("METER_TIMES", "use gpu")
                val delegateOptions = compatList.bestOptionsForThisDevice
                options.addDelegate(GpuDelegate(delegateOptions))
            }else{
                options.setNumThreads(numThreads)
            }
        } else{
            options.setNumThreads(numThreads)
        }

        //Загружаем саму модель из файла
        model = AnalyzerUtils.loadModelFile(assets, MODEL_FILENAME)
    }

    //Создание модели
    private fun create() {
        //Log.e(TAG2, MODEL_FILENAME + " create?")
        if (initialized)
            return
        //Log.e(TAG2, MODEL_FILENAME + " create+")
        initialized = true
        interpreter = Interpreter(model, options)
    }

    //Закрытие модели
    private fun close() {
        //Log.e(TAG2, MODEL_FILENAME + " close?")
        if (!initialized)
            return
        //Log.e(TAG2, MODEL_FILENAME + " close+")
        initialized = false
        interpreter.close()
    }

    //Запуск модели
    fun run() {
        //Подготовка
        create()
        output.rewind()
        try {
            //Запуск
            interpreter.run(imageBuffer, output)
        } catch (e: Exception) {
            //Запустились не правельно на устройстве -- перезапуск
            close()
            create()
            interpreter.run(imageBuffer, output)
        }
    }
}