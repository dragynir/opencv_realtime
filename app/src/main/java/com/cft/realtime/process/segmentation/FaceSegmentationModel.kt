package com.cft.realtime.process.segmentation

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.cft.realtime.process.model.AnalyzerUtils
import com.cft.realtime.process.model.Model

class FaceSegmentationModel(assets: AssetManager) : Model() {
    // TODO make separate face segmentation(strong, realtime)

    var OUTPUT_W: Int = 128
    var OUTPUT_H: Int = 128
    var THRESHOLD = 0.8f
    var OUTPUT_LABELS: Int = 1


    init {
        MODEL_FILENAME = "face_segmentation_256_256.tflite"
        INPUT_H = 256
        INPUT_W = 256
        OUTPUT_W = 128
        OUTPUT_H = 128
        THRESHOLD = 0.8f
        OUTPUT_LABELS = 1
        CHANNELS_COUNT = 3
        BATCH_SIZE = 1
        BYTES_PER_POINT = 4
        TYPES_COUNT = OUTPUT_H * OUTPUT_W * OUTPUT_LABELS

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

        run()

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
                        floatArrayOf(THRESHOLD),
                        palette,
                        BYTES_PER_POINT
                )

        return Bitmap.createScaledBitmap(mask, originalWidth, originalHeight, true)
    }

}