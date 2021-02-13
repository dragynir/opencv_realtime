package com.cft.realtime.process.result

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import com.cft.realtime.process.knowledge.Meter
import com.cft.realtime.process.knowledge.MetersInfo
import com.cft.realtime.process.ocr.OcrModel
import com.cft.realtime.process.opencv.CvUtils
import com.cft.realtime.process.segmentation.VerticalFieldSegmentation
import com.cft.realtime.process.utils.MyTimeStamp
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.core.Rect
import java.io.File
import java.io.FileOutputStream
import kotlin.system.measureTimeMillis


class MetersAnalyzer(appContext: Context){


    private var answer: Answer = Answer(Status.NOTHING)
    private var isWater: Boolean = false
    private var context: Context = appContext

    private lateinit var modelsRepository:ModelsRepository


    init{
        initializeComponents()
    }


    private val mOpenCVCallBack = object : BaseLoaderCallback(context) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    Log.d("TEST", "OpenCV Loaded Sucessfully")
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    private fun initializeComponents() {
        if (!OpenCVLoader.initDebug()) {
            Log.d(
                    "TEST",
                    "Internal OpenCV library not found. Using OpenCV Manager for Initialization"
            )

            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, context, mOpenCVCallBack)
        } else {
            Log.d("TEST", "OpenCV library found inside package. Using it!")
//            mOpenCVCallBack.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }

        modelsRepository = ModelsRepository()
        modelsRepository.initModels(context.assets)
    }


    private fun parseSerialNumber(serial: String): String {
        val regex = Regex("[\\-0-9]+")
        val matches = regex.findAll(serial)
        val numbers = matches.map { it.groupValues[0] }
        var maxLength = 0
        var serialValue: String = serial
        for (n in numbers) if (n.length > maxLength) {
            maxLength = n.length
            serialValue = n
        }
        return serialValue
    }


    companion object{
        @JvmStatic
        fun parseVerticalSerial(serial: String): String {
            val regex = Regex("[0-9]+")
            val matches = regex.findAll(serial)
            val numbers = matches.map { it.groupValues[0] }

            var maxLength = 0
            var serialValue: String = serial
            for (n in numbers) {
                if (n.length > maxLength) {
                    maxLength = n.length
                    serialValue = n
                }
            }
            return serialValue
        }
    }


    private fun saveBitmap(bitmap: Bitmap, tag: String, context: Context): String {
        val cw = ContextWrapper(context)
        val directory = cw.getDir("imageDir", Context.MODE_PRIVATE)
        val mypath = File(directory, MyTimeStamp.timestamp() + tag + ".png")
        val fos = FileOutputStream(mypath)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        fos.close()
        return mypath.toString()
    }


    // Two times run(bug): 900 s, 900 s
    private fun getOcrResult(
            image: Bitmap,
            channel: Mat,
            model: OcrModel,
            isValue: Boolean,
            context: Context
    ): Bitmap? {

        val croppedField = CvUtils.cropImageFromMaskChannel(image, channel) ?: return null

        val field = croppedField.copy(croppedField.config, true)

        val timeAll = measureTimeMillis {

            var value = ""
            value = model.getResultValue(field)

            if (isValue) {
                // cut water meter value
                if (isWater) {
                    if (value.length == 7 || value.length == 8) {
                        value = value.subSequence(0, value.length - 3).toString()
                    }
                }else{
                    // cut energy meter value
//                    val newLength = value.length - answer.fraction
//                    if (newLength > 1) {
//                        value = value.substring(0, value.length - answer.fraction)
//                    }
                }
            } else {
                value = parseSerialNumber(value)
            }

            if (isValue) {
                answer.value = Result_OCR("", value)
            } else {
                answer.serial = Result_OCR("", value)
            }

        }
        Log.d("TIME_STEPS_AN", "Ocr without load model and crop: $timeAll")

        return croppedField
    }

    private fun checkValueRect(boundRectValue: Rect, mask: Bitmap): Boolean{
        val boundRectValueRatio = boundRectValue.height.toFloat() / boundRectValue.width
        val boundRectValueImageRatio = boundRectValue.height.toFloat() * boundRectValue.width / (mask.width * mask.height)
        if (boundRectValueRatio < 0.097 || boundRectValueRatio > 0.369 || boundRectValueImageRatio < 0.001) {
            return false
        }
        return true
    }

    @SuppressLint("SetTextI18n")
    fun analyze(inputMeterImage: Bitmap, assets: AssetManager, isWater: Boolean, imageRotation: Int): Answer {

        // TODO check rotation!!!!!!!

        var boundRectValue: Rect
        var boundRectSerial: Rect
        val readValueCrop: Bitmap?
        val readSerialCrop: Bitmap?
        this.isWater = isWater

        var start = System.currentTimeMillis()
        var end = start






        val faceModel = modelsRepository.faceSegmentationModel
        val fieldsModel = modelsRepository.fieldsSegmentationModel
        val waterFieldModel = modelsRepository.waterFieldSegmentationModel
        val tariffModel = modelsRepository.tariffModel
        val readValueOcr = modelsRepository.ocrReadValueModel
        val readSerialOcr = modelsRepository.ocrSerialModel
        val meterNameModel = modelsRepository.meterNameModel
        val verticalSegmentationModel = modelsRepository.verticalSegmentationModel


        val pipeline = ResultPipeline()
        val metersInfo = MetersInfo()
        val metersList: List<Meter> = metersInfo.loadModelsInfo(context)


        end = System.currentTimeMillis()
        Log.e("METER_TIMES", "load info: " + (end - start))
        start = end



        val inputImage = if(imageRotation != 0) {
            pipeline.rotateImage(inputMeterImage, imageRotation.toFloat())
                    ?: return answer
        }else{
            inputMeterImage
        }


        if(!isWater) {
            val meterIndex = pipeline.getMeterName(inputImage, meterNameModel)
            val currentMeter: Meter = metersList[meterIndex]
            answer.model = currentMeter.name
            answer.fraction = currentMeter.fraction
        }



        end = System.currentTimeMillis()
        Log.e("METER_TIMES", "get meter name: " + (end - start))
        start = end




        var image = pipeline.getFaceIfFound(inputImage, faceModel)


        end = System.currentTimeMillis()
        Log.e("METER_TIMES", "face if found: " + (end - start))
        start = end

        //DebugModelsUtils.saveToGallery(context, image, "img")

        // check face
        var mask = pipeline.getFieldsMask(image, isWater, fieldsModel, waterFieldModel)


        end = System.currentTimeMillis()
        Log.e("METER_TIMES", "get fields 1: " + (end - start))
        start = end


        //DebugModelsUtils.saveToGallery(context, mask, "mask")

        var channels = CvUtils.splitBitmap(mask)
        boundRectValue = CvUtils.getBiggestContour(channels[0])
        boundRectSerial = CvUtils.getBiggestContour(channels[2])

        if (!checkValueRect(boundRectValue, mask)) {
            // check full image
            image = inputImage
            mask = pipeline.getFieldsMask(image, isWater, fieldsModel, waterFieldModel)
            channels = CvUtils.splitBitmap(mask)
            boundRectValue = CvUtils.getBiggestContour(channels[0])
            boundRectSerial = CvUtils.getBiggestContour(channels[2])


            end = System.currentTimeMillis()
            Log.e("METER_TIMES", "get fields on full image: " + (end - start))
            start = end
        }

        if (!checkValueRect(boundRectValue, mask)) {
            answer.status = 20
            return answer
        }

        answer.status = Status.NOTHING

        if (boundRectValue.empty()) {
            answer.status = 20
            return answer
        }




        end = System.currentTimeMillis()
        Log.e("METER_TIMES", "get fields: " + (end - start))
        start = end




        readValueCrop = getOcrResult(
                image,
                channels[0],
                readValueOcr,
                true,
                context
        )



        end = System.currentTimeMillis()
        Log.e("METER_TIMES", "first ocr: " + (end - start))
        start = end

        answer.tariff = tariffModel.getTariff(readValueCrop!!)
        answer.status = Status.VALUE

        if (isWater || boundRectSerial.empty())
            return answer


        end = System.currentTimeMillis()
        Log.e("METER_TIMES", "tarif: " + (end - start))
        start = end


        readSerialCrop = getOcrResult(
                image,
                channels[2],
                readSerialOcr,
                false,
                context
        )

        if (readSerialCrop == null) {
            return answer
        }
        answer.status = Status.SERIAL_VALUE


        end = System.currentTimeMillis()
        Log.e("METER_TIMES", "second ocr: " + (end - start))
        start = end

        val value = pipeline.readVerticalSerial(
                image,
                channels[2],
                verticalSegmentationModel,
                readSerialOcr
        )


        end = System.currentTimeMillis()
        Log.e("METER_TIMES", "vertical: " + (end - start))
        start = end

        if (value != null) {
            answer.vertical = Result_OCR("", value)
        }
        return answer
    }

}