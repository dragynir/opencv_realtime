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


    //Обработка колбека
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

    //Инициализация компонент
    private fun initializeComponents() {

        //Проверка OpenCV
        if (!OpenCVLoader.initDebug()) {
            Log.d(
                    "TEST",
                    "Internal OpenCV library not found. Using OpenCV Manager for Initialization"
            )

            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_1_0, context, mOpenCVCallBack)
        } else {
            Log.d("TEST", "OpenCV library found inside package. Using it!")
            //mOpenCVCallBack.onManagerConnected(LoaderCallbackInterface.SUCCESS)
        }

        //Инициализация репозитория  
        modelsRepository = ModelsRepository()
        modelsRepository.initModels(context.assets)
    }


    //Парсим значение
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


    //Сохранение картинки и получение пути до неё
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

        //Выризаем поле со значением (ести нашли) 
        val croppedField = CvUtils.cropImageFromMaskChannel(image, channel) ?: return null

             
        val field = croppedField.copy(croppedField.config, true)//Изменяемая копия

        val timeAll = measureTimeMillis {

            //Предсказание модели    
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

            //Записываем ответ модели 
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

    //Анализ изображения.
    //Распознание значения счётчика/серии/вертикальной серии
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





        //Получение ссылок на модели
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


        //Поворот изображения     
        val inputImage = if(imageRotation != 0) {
            pipeline.rotateImage(inputMeterImage, imageRotation.toFloat())
                    ?: return answer
        }else{
            inputMeterImage
        }


        //Получаем данные о счётчике и загружаем в answer
        if(!isWater) {
            val meterIndex = pipeline.getMeterName(inputImage, meterNameModel)
            val currentMeter: Meter = metersList[meterIndex]

            answer.model = currentMeter.name
            answer.fraction = currentMeter.fraction
        }



        end = System.currentTimeMillis()
        Log.e("METER_TIMES", "get meter name: " + (end - start))
        start = end



        //Выделяем морду счётчика
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

        //Разбиваем на каналы
        var channels = CvUtils.splitBitmap(mask)

        //получаем часть с значением и серией
        boundRectValue = CvUtils.getBiggestContour(channels[0])
        boundRectSerial = CvUtils.getBiggestContour(channels[2])

        //Ищем поля на всей фотке
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

        //Статус: ничего нет
        answer.status = Status.NOTHING

        //Если не нашли значения, выходим
        if (boundRectValue.empty()) {
            answer.status = 20
            return answer
        }




        end = System.currentTimeMillis()
        Log.e("METER_TIMES", "get fields: " + (end - start))
        start = end



        //Получаем значение с счётчика
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

        
        //Определяем тариф
        answer.tariff = tariffModel.getTariff(readValueCrop!!)
        //Статус: есть тариф счётчика
        answer.status = Status.VALUE

        if (isWater || boundRectSerial.empty())
            return answer


        end = System.currentTimeMillis()
        Log.e("METER_TIMES", "tarif: " + (end - start))
        start = end

        //Получаем серию счётчика
        readSerialCrop = getOcrResult(
                image,
                channels[2],
                readSerialOcr,
                false,
                context
        )

        //Если ничего не получили
        if (readSerialCrop == null) {
            return answer
        }

        //Статус: есть серия счётчика
        answer.status = Status.SERIAL_VALUE


        end = System.currentTimeMillis()
        Log.e("METER_TIMES", "second ocr: " + (end - start))
        start = end

        //Получаем серию счётчика (вертикально)
        val value = pipeline.readVerticalSerial(
                image,
                channels[2],
                verticalSegmentationModel,
                readSerialOcr
        )


        end = System.currentTimeMillis()
        Log.e("METER_TIMES", "vertical: " + (end - start))
        start = end

        //Если есть, загружаем. 
        if (value != null) {
            answer.vertical = Result_OCR("", value)
        }
        return answer
    }

}