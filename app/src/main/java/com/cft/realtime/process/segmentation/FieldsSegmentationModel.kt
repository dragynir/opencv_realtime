package com.cft.realtime.process.segmentation

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import com.cft.realtime.process.model.AnalyzerUtils
import com.cft.realtime.process.model.Model
import com.cft.realtime.process.utils.Bucket

class FieldsSegmentationModel(assets: AssetManager): Model() {

    var OUTPUT_W: Int = 128
    var OUTPUT_H: Int = 128
    var VALUE_THRESHOLD = 0.7f
    var SERIAL_THRESHOLD = 0.6f
    var MODEL_THRESHOLD = 0.5f
    var OUTPUT_LABELS: Int = 1

    init {
        MODEL_FILENAME = "meters_lite_fields_segment_without_model512_512.tflite"//TODO: OCR на рилтайм
        INPUT_H = 512
        INPUT_W = 512
        OUTPUT_W = 512
        OUTPUT_H = 512
        VALUE_THRESHOLD = 0.7f
        SERIAL_THRESHOLD = 0.6f
        MODEL_THRESHOLD = 0.5f
        OUTPUT_LABELS = 2
        CHANNELS_COUNT = 3
        BATCH_SIZE = 1
        BYTES_PER_POINT = 4
        TYPES_COUNT = OUTPUT_W * OUTPUT_H * OUTPUT_LABELS


        //USE_GPU = false//crash when true
        USE_GPU = Bucket.GPU_FOR_F_SEGM

        init(assets)
    }

    fun getMask(image: Bitmap): Bitmap {
        val originalWidth = image.width
        val originalHeight = image.height

        val inputBitmap = Bitmap.createScaledBitmap(image, INPUT_W, INPUT_H, true)

        output.rewind()
        imageBuffer.rewind()

        AnalyzerUtils.imageToFloatBuffer(
                inputBitmap,
                imageBuffer
        )

        Log.e("repair", "before stab")
        run()
        Log.e("repair", "after stab")

        val palette =
                AnalyzerUtils.createPalette(
                        OUTPUT_LABELS
                )
        val segmentBits = Array(OUTPUT_W) {
            IntArray(
                    OUTPUT_H
            )
        }

        val mask =
                AnalyzerUtils.decodeSegmentationMasks(
                        output,
                        segmentBits,
                        OUTPUT_W,
                        OUTPUT_H,
                        OUTPUT_LABELS,
                        floatArrayOf(VALUE_THRESHOLD, SERIAL_THRESHOLD, MODEL_THRESHOLD),
                        palette,
                        BYTES_PER_POINT
                )

        return Bitmap.createScaledBitmap(mask, originalWidth, originalHeight, true)
    }
}