package com.cft.realtime.process.ocr

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import com.cft.realtime.process.model.AnalyzerUtils
import com.cft.realtime.process.model.Model
import kotlin.system.measureTimeMillis


class OcrSerialModel(
        assets: AssetManager
): OcrModel, Model() {

    private val symbols = arrayOf('-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'г', '№')

    var OUTPUT_STEPS: Int = 64
    var SYMBOLS_COUNT: Int = 14

    init {

        MODEL_FILENAME = "ocr_serial_i128_1024_o64_14lite_optimize_size.tflite"
        INPUT_H = 128
        INPUT_W = 1024
        OUTPUT_STEPS = 64
        SYMBOLS_COUNT = 14
        CHANNELS_COUNT = 3
        BATCH_SIZE = 1
        BYTES_PER_POINT = 4
        TYPES_COUNT = SYMBOLS_COUNT * OUTPUT_STEPS

        USE_GPU = false //crashes on true

        init(assets)
    }


    override fun getResultValue(inputBitmap: Bitmap): String{

        AnalyzerUtils.rgbToGray(
                inputBitmap
        )

        val ctcMatrix = getCtcMatrix(inputBitmap)


        val stringBuilder = StringBuilder()

        val time = measureTimeMillis {

            val valuesList =
                    AnalyzerUtils.decodeCtcMatrix(
                            ctcMatrix,
                            symbols,
                            OUTPUT_STEPS,
                            SYMBOLS_COUNT - 1
                    )
            for (value in valuesList) {
                stringBuilder.append(value)
            }

        }

        Log.d("TIME_STEPS_AN", "Decode ctc matrix: $time")

        return stringBuilder.toString()
    }

    private fun getCtcMatrix(bitmap: Bitmap): Array<FloatArray>{

        val inputBitmap = Bitmap.createScaledBitmap(bitmap,
                INPUT_W,
                INPUT_H, true)

        output.rewind()
        imageBuffer.rewind()

        var time = measureTimeMillis {
            AnalyzerUtils.imageToFloatBuffer(
                    inputBitmap,
                    imageBuffer
            )
        }
        Log.d("TIME_STEPS_AN", "imageToFloatBuffer: $time")

        time = measureTimeMillis {
            run()
        }

        Log.d("TIME_STEPS_AN", "Run interpreter: $time")

        return AnalyzerUtils.decodeOcrOutput(
                output,
                OUTPUT_STEPS,
                SYMBOLS_COUNT,
                BYTES_PER_POINT
        )
    }

}