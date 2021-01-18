package com.cft.realtime.process.model

import android.content.res.AssetManager
import android.graphics.Bitmap

class ContainsModel (assets: AssetManager): Model() {
    private val types = arrayOf(true, false)

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


    fun getIfContains(inputBitmap: Bitmap): Boolean {
        val bitmap = Bitmap.createScaledBitmap(inputBitmap, INPUT_W, INPUT_H, true)

        output.rewind()
        imageBuffer.rewind()

        AnalyzerUtils.imageToFloatBuffer(bitmap, imageBuffer, true)

        run()

        var maxType = 0
        val outputFloat = floatArrayOf(output.getFloat(0), output.getFloat(4))
        for (i in 1 until TYPES_COUNT)
            if (outputFloat[i] > outputFloat[maxType])
                maxType = i

        return types[maxType]
    }
}