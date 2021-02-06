package com.cft.realtime.process.result

import android.content.res.AssetManager
import com.cft.realtime.process.classification.MeterNameModel
import com.cft.realtime.process.classification.QualityModel
import com.cft.realtime.process.classification.TariffModel
import com.cft.realtime.process.model.ContainsModel
import com.cft.realtime.process.ocr.OcrReadValueModel
import com.cft.realtime.process.ocr.OcrSerialModel
import com.cft.realtime.process.segmentation.FaceSegmentationModel
import com.cft.realtime.process.segmentation.FieldsSegmentationModel
import com.cft.realtime.process.segmentation.RealtimeFieldsSegmentationModel
import com.cft.realtime.process.segmentation.WaterFieldSegmentationModel

class ModelsRepository {

    lateinit var qualityModel: QualityModel
    lateinit var containsModel: ContainsModel
    lateinit var tariffModel: TariffModel
    lateinit var faceSegmentationModel: FaceSegmentationModel
    lateinit var realtimeFieldsSegmentationModel: RealtimeFieldsSegmentationModel
    lateinit var fieldsSegmentationModel: FieldsSegmentationModel
    lateinit var waterFieldSegmentationModel: WaterFieldSegmentationModel
    lateinit var meterNameModel: MeterNameModel

    lateinit var ocrReadValueModel: OcrReadValueModel
    lateinit var ocrSerialModel: OcrSerialModel


    fun initModels(assets: AssetManager) {
        // ocr allocateDirect не силльно долго работает(нет прибавки по скорости работы при заранее выделенных буферах)

        qualityModel =
                QualityModel(
                        assets
                )

        containsModel =
                ContainsModel(
                        assets
                )

        tariffModel =
                TariffModel(
                        assets
                )

        faceSegmentationModel =
                FaceSegmentationModel(
                        assets
                )

        realtimeFieldsSegmentationModel =
                RealtimeFieldsSegmentationModel(
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