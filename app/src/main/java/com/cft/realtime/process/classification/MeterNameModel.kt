package com.cft.realtime.process.classification

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.cft.realtime.process.model.AnalyzerUtils
import com.cft.realtime.process.model.Model

class MeterNameModel(assets: AssetManager): Model() {

    init {
        MODEL_FILENAME = "model_classification_512_512_25.tflite"
        INPUT_H = 512
        INPUT_W = 512
        TYPES_COUNT = 25
        CHANNELS_COUNT = 3
        BATCH_SIZE = 1
        BYTES_PER_POINT = 4
        init(assets)
    }

    fun getModelIndex(inputBitmap: Bitmap): Int {
        val bitmap = Bitmap.createScaledBitmap(inputBitmap, INPUT_W, INPUT_H, true)

        output.rewind()
        imageBuffer.rewind()

        AnalyzerUtils.imageToFloatBuffer(bitmap, imageBuffer, true)

        run()

        var maxConfidence = 0f
        var maxIndex = 0
        for(i in 0 until TYPES_COUNT){
            val classConfidence = output.getFloat(i * 4)
            if(classConfidence > maxConfidence){
                maxIndex = i
                maxConfidence = classConfidence
            }
        }

        return maxIndex
    }
}