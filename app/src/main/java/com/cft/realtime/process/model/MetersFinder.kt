package com.cft.realtime.process.model

import android.content.res.AssetManager
import android.graphics.Bitmap

class MetersFinder(assets: AssetManager) {

    private val containsModel: ContainsModel = ContainsModel(
            assets
    )

    fun findMeter(bitmap: Bitmap): Boolean{
        return containsModel.getIfContains(bitmap)
    }
}