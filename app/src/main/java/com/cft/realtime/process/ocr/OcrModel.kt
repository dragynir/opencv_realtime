package com.cft.realtime.process.ocr

import android.graphics.Bitmap

interface OcrModel {
    fun getResultValue(inputBitmap: Bitmap): String
}