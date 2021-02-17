package com.cft.realtime

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.cft.realtime.process.Analyzer
import com.cft.realtime.process.CameraView
import com.cft.realtime.process.TfliteReactNativeModule
import com.cft.realtime.process.utils.Bucket
import kotlinx.android.synthetic.main.activity_main.*
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.CameraBridgeViewBase
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import java.io.File

//Главный объект работы приложения
class MainActivity : AppCompatActivity() {

    private lateinit var mOpenCvCameraView: CameraView
    private lateinit var mIntermediateMat: Mat

    //Запуск приложения
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        mOpenCvCameraView = findViewById(R.id.camera_view)

        val module = TfliteReactNativeModule(this)

        module.loadModels()

        mOpenCvCameraView.setResultsCallback(object: Analyzer.ResultsCallback{
            override fun onFail() {

            }

            override fun onResults(hasMeter: Boolean?, delay: Long) {
                val cw = ContextWrapper(applicationContext)
                val directory = cw.getDir("imageDir", Context.MODE_PRIVATE)
                val mypath = File(directory, "test.png")
                Log.e("repair", "before take")
                mOpenCvCameraView.takePicture(mypath)
                Log.e("repair", "after take")

                val b = BitmapFactory.decodeFile(mypath.absolutePath)

                Log.d("PICTURE", "load picture")
            }
        })


        mOpenCvCameraView.setOnImageSavedCallback {
            val start = System.currentTimeMillis()
            Log.e("repair", "SavedCallback: "+it)
            Bucket.GPU_FOR_F_SEGM = gpu_sw.isChecked
            val result = module.analyze(it, false)
            Log.e("repair", "result: " + result)
            text_out.setText(result+": " + Bucket.GPU_FOR_F_SEGM)
            Log.e("METER_TIMES", "time: " + (System.currentTimeMillis()-start))
        }

    }

    private fun activateOpenCVCameraView() {
        // everything needed to start a camera preview
//        mOpenCvCameraView.setPe()
        mOpenCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_ANY)
        mOpenCvCameraView.visibility = SurfaceView.VISIBLE
        mOpenCvCameraView.enableView()
    }

    //Отвечает за готовность приложения получать запросы пользователя
    override fun onResume() {
        super.onResume()

        mOpenCvCameraView.onResumeALPR()

        // there's no need to load the opencv library if there is no camera preview (I think that sounds reasonable (?))
        if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
        ) {
            if (!OpenCVLoader.initDebug()) {
                log("Internal OpenCV library not found. Using OpenCV Manager for initialization");
                OpenCVLoader.initAsync(
                        OpenCVLoader.OPENCV_VERSION_3_0_0, this,
                        mLoaderCallback
                )
            } else {
                log("OpenCV library found inside package. Using it!");
                mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
            }
        }
    }

    private val mLoaderCallback: BaseLoaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    log("OpenCV loaded successfully")
                    activateOpenCVCameraView()
                }
                else -> super.onManagerConnected(status)
            }
        }
    }

    private fun log(message: String) {
        Log.e("repair", message)
    }
}