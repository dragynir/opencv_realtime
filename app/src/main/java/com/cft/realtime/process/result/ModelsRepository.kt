package com.cft.realtime.process.result

import android.content.res.AssetManager
import com.cft.realtime.process.classification.MeterNameModel
import com.cft.realtime.process.classification.QualityModel
import com.cft.realtime.process.classification.TariffModel
import com.cft.realtime.process.model.ContainsModel
import com.cft.realtime.process.ocr.OcrReadValueModel
import com.cft.realtime.process.ocr.OcrSerialModel
import com.cft.realtime.process.segmentation.*

class ModelsRepository {


    lateinit var tariffModel: TariffModel
    lateinit var faceSegmentationModel: FaceSegmentationModel
    lateinit var fieldsSegmentationModel: FieldsSegmentationModel
    lateinit var waterFieldSegmentationModel: WaterFieldSegmentationModel
    lateinit var meterNameModel: MeterNameModel

    lateinit var ocrReadValueModel: OcrReadValueModel
    lateinit var ocrSerialModel: OcrSerialModel
    lateinit var verticalSegmentationModel: VerticalFieldSegmentation


    fun initModels(assets: AssetManager) {

        verticalSegmentationModel = VerticalFieldSegmentation(assets)


        tariffModel =
          TariffModel(
            assets
          )

        faceSegmentationModel =
          FaceSegmentationModel(
            assets
          )

        fieldsSegmentationModel =
          FieldsSegmentationModel(
            assets
          )

        waterFieldSegmentationModel =
          WaterFieldSegmentationModel(
            assets
          )

        ocrReadValueModel = OcrReadValueModel(assets)

        ocrSerialModel = OcrSerialModel(assets)

        meterNameModel = MeterNameModel(assets)
    }
}