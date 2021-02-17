package com.cft.realtime.process.result

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.cft.realtime.process.classification.MeterNameModel
import com.cft.realtime.process.ocr.OcrModel
import com.cft.realtime.process.opencv.CvUtils
import com.cft.realtime.process.segmentation.FaceSegmentationModel
import com.cft.realtime.process.segmentation.FieldsSegmentationModel
import com.cft.realtime.process.segmentation.VerticalFieldSegmentation
import com.cft.realtime.process.segmentation.WaterFieldSegmentationModel
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat

class ResultPipeline() {

    //Получить морду счётчика, если есть, иначе вернуть исходное изображение
    fun getFaceIfFound(image: Bitmap, faceModel: FaceSegmentationModel): Bitmap{
        // TODO первый вызов функции возвращает 0, какой-то баг тут в AnalyzerUtils (RESOLVED)
        val faceMask = faceModel.getMask(image)

        val face = CvUtils.findMeterFace(faceMask, image)

        if (face != null) {
            return face
        }
        return image
    }


    //Получить имя (тип) модели счётчика
    fun getMeterName(image: Bitmap, model: MeterNameModel): Int{
        return model.getModelIndex(image)
    }

    //Получить маску поля с данными
    fun getFieldsMask(image: Bitmap, checkWater: Boolean, fieldsModel: FieldsSegmentationModel,
                      waterFieldModel: WaterFieldSegmentationModel
    ): Bitmap {
        return if (checkWater) waterFieldModel.getMask(image) else fieldsModel.getMask(image)
    }



    //Повернуть картинку
    fun rotateImage(image: Bitmap?, degree: Float): Bitmap? {
        val matrix = Matrix()
        matrix.postRotate(degree)
        return Bitmap.createBitmap(image!!, 0, 0, image.width, image.height, matrix, true)
    }

    //Получить вертикальную серию счётчика (если нашли)
    fun readVerticalSerial(image: Bitmap, serialChannel: Mat, verticalSegmentationModel: VerticalFieldSegmentation, serialModel: OcrModel): String?{

        //Вырезать серию (баркод) с заданным масштабом (если есть)
        val croppedBarCodeArea = CvUtils.cropImageFromMaskChannelWithPad(
                image,
                serialChannel,
                0.8f,
                3.3f,
                0.15f,
                0.3f
        )

//
//        if (croppedBarCodeArea != null) {
//            DebugModelsUtils.saveToGallery(context, croppedBarCodeArea, "crop")
//        }

        //если нашли
        if (croppedBarCodeArea != null) {

            val barCodeAndVerticalMask = verticalSegmentationModel.getMask(croppedBarCodeArea)

            //DebugModelsUtils.saveToGallery(context, barCodeAndVerticalMask, "crop")

            val barCodeAndVerticalChannels = CvUtils.splitBitmap(barCodeAndVerticalMask)
            
            //минимизируем контуры            
            var barcodeRect = CvUtils.findMinAreaRect(barCodeAndVerticalChannels[2])
            var verticalRect = CvUtils.findMinAreaRect(barCodeAndVerticalChannels[0])

            //Контуры не выделелись
            if(barcodeRect == null || verticalRect == null){
                Log.d("VERTCHECK", "No barcode or vert")
                return null
            }

            //Вырезаем баркод
            val mat = Mat()
            Utils.bitmapToMat(barCodeAndVerticalMask, mat)
            val cropedBarcodeMask = CvUtils.cropMinAreaRect(barcodeRect, mat, true)

            //Разбиваем на каналы
            val channels = ArrayList<Mat>()
            Core.split(cropedBarcodeMask, channels)

            //кол-во не нулевых этементов
            val count = Core.countNonZero(channels[2])
            //Метрика доли информации            
            val rectMetric = count / (barcodeRect.size.height * barcodeRect.size.width)

            // drop low rectangular mask
            //если слишком мало для баркода            
            if(rectMetric < 0.92){
                return null
            }

            Log.d("TEST_RECT", "Rat:$rectMetric")

            //Меняем ориентацию
            barcodeRect = CvUtils.decodeMinRect(barcodeRect)
            verticalRect = CvUtils.decodeMinRect(verticalRect)


            //Считаем площадь
            val barcodeArea = barcodeRect.size.width * barcodeRect.size.height
            val verticalArea = verticalRect.size.width * verticalRect.size.height

            //Проверки на корректность
            if(barcodeArea < verticalArea || (barcodeRect.center.x - barcodeRect.size.width / 2)  < verticalRect.center.x ||
                    verticalRect.size.width * 1.3 > verticalRect.size.height || verticalRect.size.height < 0.8 * barcodeRect.size.height){

                Log.d("VERTCHECK", (barcodeArea < verticalArea).toString())
                Log.d("VERTCHECK", (barcodeRect.center.x - barcodeRect.size.width / 2).toString())
                Log.d("VERTCHECK", (verticalRect.size.width < 1.3 * verticalRect.size.height).toString())
                Log.d("VERTCHECK", (verticalRect.size.height < 0.8 * barcodeRect.size.height).toString())

                return null
            }


            //Выризаем серию
            var croppedVerticalSerial =
                    CvUtils.cropImageFromMaskChannelWithPad(croppedBarCodeArea, barCodeAndVerticalChannels[0], 1.1f, 1.3f, 0.0f, 0.0f)

            //Если нашли
            if (croppedVerticalSerial != null) {

                // DebugModelsUtils.saveToGallery(context, croppedVerticalSerial, "crop")

                //Переводим в горизонтальное положение
                croppedVerticalSerial = rotateImage(croppedVerticalSerial, 90.0f)

                //Изменяемая копия
                val field = croppedVerticalSerial?.copy(croppedVerticalSerial.config, true)

                if (field != null) {

                    //Работа модели
                    var value = serialModel.getResultValue(field)

                    Log.d("LOG_VERTICAL_SER", value)

                    //Парсим значение
                    value = MetersAnalyzer.parseVerticalSerial(value)

                    if(value.length < 5 || value.length > 6){
                        return null
                    }

                    return value
                }
            }
        }

        return null
    }
}