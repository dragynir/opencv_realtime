package com.cft.realtime.process.knowledge

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException


class MetersInfo() {
    private fun getJsonDataFromAsset(context: Context, fileName: String): String? {
        val jsonString: String
        try {
            jsonString = context.assets.open(fileName).bufferedReader().use { it.readText() }
        } catch (ioException: IOException) {
            ioException.printStackTrace()
            return null
        }
        return jsonString
    }

    fun loadModelsInfo(context: Context): List<Meter>{
        val jsonFileString = getJsonDataFromAsset(context, "knowledge/meters_config.json")
        val gson = Gson()
        val metersList = object : TypeToken<List<Meter>>() {}.type
        return gson.fromJson(jsonFileString, metersList)
    }
}