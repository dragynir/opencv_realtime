package com.cft.realtime.process.model

import android.content.res.AssetManager
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer

open class Model() {
    lateinit var imageBuffer: ByteBuffer
    lateinit var output: ByteBuffer

    private lateinit var interpreter: Interpreter
    private lateinit var model: MappedByteBuffer
    private lateinit var options: Interpreter.Options
    private var initialized = false
    var USE_GPU = true
    private val numThreads = 2

    var MODEL_FILENAME = "mobile_500K_95_sigmoid.tflite"
    var INPUT_H: Int = 256
    var INPUT_W: Int = 256
    var TYPES_COUNT: Int = 3
    var CHANNELS_COUNT: Int = 3
    var BATCH_SIZE: Int = 1
    var BYTES_PER_POINT: Int = 4

    fun init(assets: AssetManager) {
        imageBuffer = ByteBuffer.allocateDirect(BATCH_SIZE * INPUT_H * INPUT_W * CHANNELS_COUNT * BYTES_PER_POINT)
        imageBuffer.order(ByteOrder.nativeOrder())

        output = ByteBuffer.allocateDirect(BATCH_SIZE * TYPES_COUNT * BYTES_PER_POINT)
        output.order(ByteOrder.nativeOrder())

        options = Interpreter.Options()
        if (USE_GPU){
            options.addDelegate(GpuDelegate())
        }else{
            options.setNumThreads(numThreads)
        }

        model = AnalyzerUtils.loadModelFile(assets, MODEL_FILENAME)
    }

    private fun create() {
        //Log.e(TAG2, MODEL_FILENAME + " create?")
        if (initialized)
            return
        //Log.e(TAG2, MODEL_FILENAME + " create+")
        initialized = true
        interpreter = Interpreter(model, options)
    }

    private fun close() {
        //Log.e(TAG2, MODEL_FILENAME + " close?")
        if (!initialized)
            return
        //Log.e(TAG2, MODEL_FILENAME + " close+")
        initialized = false
        interpreter.close()
    }

    fun run() {
        create()
        output.rewind()
        try {
            interpreter.run(imageBuffer, output)
        } catch (e: Exception) {
            close()
            create()
            interpreter.run(imageBuffer, output)
        }
    }
}