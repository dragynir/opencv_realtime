package com.cft.realtime.process.opencv

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

object CvUtils {

    //Разбить изображение на каналы
    fun splitBitmap(bitmap: Bitmap): ArrayList<Mat>{
        val mat = Mat() //Матрица
        val bmp32 = bitmap.copy(Bitmap.Config.ARGB_8888, true)//Изменяемая копия
        Utils.bitmapToMat(bmp32, mat)//Конвертируем
        val channels = ArrayList<Mat>()
        Core.split(mat, channels)//Разбиваем изображение на каналы
        return channels
    }

    //Получить самый большой контур
    fun getBiggestContour(image: Mat): Rect {
        val contours = ArrayList<MatOfPoint>()//Контуры
        val hierarchy = Mat()//Информация об иерархии контуров

        //Получить все контуры и положить их в contours
        //см доукментацию https://docs.opencv.org/master/javadoc/org/opencv/imgproc/Imgproc.html#findContours(org.opencv.core.Mat,java.util.List,org.opencv.core.Mat,int,int)
        Imgproc.findContours(
            image,
            contours,
            hierarchy,
            Imgproc.RETR_TREE, //Извлекает все контуры и восстанавливает полную иерархию вложенных контуров.
            Imgproc.CHAIN_APPROX_SIMPLE//Хранит абсолютно все контурные точки. 
                                       //То есть любые 2 последующие точки (x1,y1) и (x2,y2) контура будут либо горизонтальными,
                                       //либо вертикальными, либо диагональными соседями, то есть max(abs(x1-x2),abs(y2-y1))==1.
        )

        //Поиск самого большого контура
        var boundRect = Rect()
        var bufRect: Rect
        for (i in contours.indices) {
            bufRect = Imgproc.boundingRect(contours[i])

            if (bufRect.area() > boundRect.area()) {
                boundRect = bufRect.clone()
            }
        }

        return boundRect
    }

    //Поменять width и height
    private fun swapSizeHW(s: Size){
        val w = s.width
        val h = s.height
        s.width = h
        s.height = w
    }

    //Поменять параметры RotatedRect
    fun decodeMinRectParams(angle: Double, size: Size): Double{
        var a = angle
        when {
            angle == -90.0 -> {
                swapSizeHW(size)
                a = 0.0
            }
            angle == -0.0 -> {
                a = 0.0
            }
            angle > -45 -> {
                // a =  -angle
                a = angle
            }
            angle < -45 -> {
                swapSizeHW(size)
                a += 90
            }
        }
        return a
    }

    //Вырезать прямоугольник из изображения
    fun cropMinAreaRect(rect: RotatedRect, fullImage: Mat, decode: Boolean): Mat{
        
        //Параметры прямоугольника        
        val center = rect.center
        val size = rect.size
        var angle = rect.angle

        //Если надо преобразуем
        if(decode) {
            angle = decodeMinRectParams(angle, size)
        }

        //Матрица двумерного вращения
        val m = Imgproc.getRotationMatrix2D(center, angle, 1.0)
        val rotatedImage = Mat()

        //Поворачиваем изображение и сохраняем результат в rotatedImage        
        Imgproc.warpAffine(fullImage, rotatedImage, m, Size(fullImage.width().toDouble(), fullImage.height().toDouble()))

        val channels = ArrayList<Mat>()
        Core.split(rotatedImage, channels)//Разбиваем на каналы
        channels.removeAt(3) //Уберём третий
        val rgbMat = Mat()
        Core.merge(channels, rgbMat)
        val croppedImage = Mat()

       // it is works with one or three channels
        //Вырезать прямоугольную область из rgbMat и сохранить в croppedImage
        Imgproc.getRectSubPix(rgbMat, size, center, croppedImage)

        return croppedImage
    }

    //Поменять ориентацию (RotatedRect)
    fun decodeMinRect(minRect: RotatedRect): RotatedRect{
        val size = minRect.size
        var angle = minRect.angle
        angle = decodeMinRectParams(angle, size)

        minRect.size = size
        minRect.angle = angle
        return minRect
    }

    //Найти минимальный контур объекта
    fun findMinAreaRect(channel: Mat): RotatedRect?{
        val contours = ArrayList<MatOfPoint>() //Контуры
        val hierarchy = Mat()//Информация об иерархии контуров

        //Получить все контуры и положить их в contours
        Imgproc.findContours(channel, contours, hierarchy, 
            Imgproc.RETR_EXTERNAL,//Извлекает только крайние внешние контуры. Он устанавливает иерархию hierarchy[i][2]=hierarchy[i][3]=-1 
            Imgproc.CHAIN_APPROX_SIMPLE//Хранит абсолютно все контурные точки.
        )

        //Контуры не выделелись
        if(contours.size == 0){
            return null
        }

        var cnt = contours[0]
        // find biggest contour TODO rewrite algorithm
        if(contours.size > 1){
            for (i in contours.indices) {
                if (Imgproc.contourArea(contours[i]) > Imgproc.contourArea(cnt)) {
                    cnt = contours[i]
                }
            }
        }

        val c = MatOfPoint2f()
        cnt.convertTo(c, CvType.CV_32FC2)//Конвертим ??к float32??

        val peri = Imgproc.arcLength(c, true)//Длинна кривой (true -- замкнутой).
        val poly = MatOfPoint2f()
        Imgproc.approxPolyDP(c, poly, 0.015 * peri, true)//Аппроксимация контураменьшим контуром с погрешностью = 0.015 * peri
        return Imgproc.minAreaRect(poly)
    }

    //Вырезать морду счётчика (если есть)
    fun findMeterFace(mask: Bitmap, fullImage: Bitmap): Bitmap?{

        val matImage = Mat()
        //Картинка в матрицу        
        Utils.bitmapToMat(fullImage, matImage)

        val channel = splitBitmap(mask)[0]

        //Получаем контур счётчика (иначе вернуть null)        
        val minRect = findMinAreaRect(channel) ?: return null

        //Вырезаем счётчик
        val croppedMat = cropMinAreaRect(minRect, matImage, true)

        //Соотношение плошадей
        val ratio = (croppedMat.size().width * croppedMat.size().height) / (matImage.size().height * matImage.size().height)

        Log.d("RATIO", ratio.toString())

        //Всё и так "ОК"
        if(ratio < 0.1){
            Log.d("RATIO", "return")
            return fullImage
        }

        //Делаем картинку из вырезаного куска
        val resultBitmap = Bitmap.createBitmap(croppedMat.cols(), croppedMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(croppedMat, resultBitmap)

        return resultBitmap
    }

    //Преобразовать нулевой канал выравниванием и пороговм обрезанием гистограммы 
    fun applyClahe(image: Bitmap): Bitmap {

        val matImage = Mat()
        val labImage = Mat()
        Utils.bitmapToMat(image, matImage)
        //Преобразование изображения из RGB в L*a*b (лучше см документацию https://vovkos.github.io/doxyrest-showcase/opencv/sphinx_rtd_theme/page_imgproc_color_conversions.html#doxid-de-d25-imgproc-color-conversions-1color-convert-rgb-lab)         
        Imgproc.cvtColor(matImage, labImage, Imgproc.COLOR_RGB2Lab)


        val channels = ArrayList<Mat>()
        Core.split(labImage, channels)

        //cv::CLAHE базовый класс для адаптивного выравнивания гистограммы с ограниченным контрастом.
        val clahe = Imgproc.createCLAHE()
        clahe.clipLimit = 2.0 //Пороног для гистограммы

        val destImage = Mat()
        //Выравнивает гистограмму изображения в оттенках серого с помощью адаптивного выравнивания гистограммы с ограниченным контрастом.
        clahe.apply(channels[0], destImage)
        
       //Собираем изображение обратно 
        
        destImage.copyTo(channels[0])

        Core.merge(channels, labImage)

        Imgproc.cvtColor(labImage, matImage, Imgproc.COLOR_Lab2RGB)

        Utils.matToBitmap(matImage, image)

        return image
    }


    //Вырезать объект (если есть)
    fun cropImageFromMaskChannel(fullImage: Bitmap, channel: Mat): Bitmap?{

        val matImage = Mat()
        Utils.bitmapToMat(fullImage, matImage)//Изображение в матрицу

        //Получаем контур счётчика (если есть)
        val minRect = findMinAreaRect(channel) ?: return null

        //Фильтр неподходящего объекта
        if (minRect.size.width == 0.0 ||  minRect.size.height == 0.0){
            Log.e(TAG2, "minRect was bad: " + minRect.size.width+"x"+minRect.size.height)
            return null
        }

        //Вырезаем и формируем изображение
        val croppedMat = cropMinAreaRect(minRect, matImage, true)
        val resultBitmap = Bitmap.createBitmap(croppedMat.cols(), croppedMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(croppedMat, resultBitmap)
        return resultBitmap
    }


    //Вырезать объект с заданным масштабом (если есть)
    fun cropImageFromMaskChannelWithPad(fullImage: Bitmap, channel: Mat, widthRatio: Float, heightRatio: Float, rightRatio: Float, topRatio: Float): Bitmap? {
        val matImage = Mat()
        Utils.bitmapToMat(fullImage, matImage)
        val minRect = findMinAreaRect(channel) ?: return null

       //Правельная ориентация 
        val size = minRect.size
        var angle = minRect.angle
        angle = decodeMinRectParams(angle, size)

        minRect.size = size
        minRect.angle = angle

        //Маштабирование и установка нового центра
        minRect.size.width+= minRect.size.width * widthRatio
        minRect.size.height+= minRect.size.height * heightRatio
        minRect.center.x-=minRect.size.width * rightRatio
        minRect.center.y-=minRect.size.height * topRatio


        //Вырезаем и формируем картинку
        val croppedMat = cropMinAreaRect(minRect, matImage, false)
        val resultBitmap = Bitmap.createBitmap(croppedMat.cols(), croppedMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(croppedMat, resultBitmap)
        return resultBitmap
    }

}