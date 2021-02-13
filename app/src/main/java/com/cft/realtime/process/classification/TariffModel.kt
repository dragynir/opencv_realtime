package com.cft.realtime.process.classification

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.cft.realtime.process.model.AnalyzerUtils
import com.cft.realtime.process.model.Model

class TariffModel(assets: AssetManager): Model() {
    private val types = arrayOf(0, 1, 2, 3)

    init {
        MODEL_FILENAME = "tarif_classification_64_256_4class.tflite"
        INPUT_H = 64
        INPUT_W = 256
        TYPES_COUNT = 4
        CHANNELS_COUNT = 3
        BATCH_SIZE = 1
        BYTES_PER_POINT = 4
        init(assets)
    }


    fun getTariff(inputBitmap: Bitmap): Int {
        val bitmap = Bitmap.createScaledBitmap(inputBitmap, INPUT_W, INPUT_H, true)

        output.rewind()
        imageBuffer.rewind()

        AnalyzerUtils.imageToFloatBuffer(bitmap, imageBuffer, true)

        run()

        var maxType = 0
        val outputFloat =
                floatArrayOf(output.getFloat(0), output.getFloat(4), output.getFloat(4 * 2), output.getFloat(4 * 3))
        for (i in 1 until TYPES_COUNT)
            if (outputFloat[i] > outputFloat[maxType])
                maxType = i

        return types[maxType]
    }
}