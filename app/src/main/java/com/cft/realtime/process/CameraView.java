package com.cft.realtime.process;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Area;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

//import com.facebook.react.uimanager.ThemedReactContext;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;


public class CameraView extends JavaCameraView implements ICameraView, Camera.PictureCallback {

    private static final String TAG = CameraView.class.getSimpleName();
    private Analyzer.ResultsCallback callback;
    private ImageSavedCallback onSavedCallback;
    private int quality = Quality.MAX;
    private Size highResolution;
    private Size mediumResolution;
    private Size lowResolution;
    private Size maxResolution;

    private List<Point> coordinates;
    private int[] plateBorderRgb = new int[]{0, 0, 255};
    private boolean plateBorderEnabled;
    private String country = "us";
    private boolean tapToFocusEnabled;
    private boolean torchEnabled = true;
    private int rotation;
    private File filename;
    private int goodPhotoCounter = 0;
    private int MIN_COUNT = 10;
    int REQUEST_CODE_CAMERA_PERMISSION = 101;


    private CvCameraViewListener2 mListener;
    private Bitmap mCacheBitmap;

    public CameraView(Context context, int cameraId) {
        super(context, cameraId);
    }

    public CameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mScale = 1;
    }

    @Override
    public void enableView() {
        if(true){
            super.enableView();
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            try {
                super.enableView();
            }catch (Exception e){
                Log.e("repair", "unbelievable exception");
            }
            return;
        }


        if (isCameraGranted()) {
            try {
                super.enableView();
            }catch (Exception e){
                Log.e("repair", "unbelievable exception");
            }
            return;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(scanForActivity(getContext()), Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(scanForActivity(getContext()), new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);

            Toast.makeText(getContext(), "Предоставьте доступ к камере в настройках приложения.", Toast.LENGTH_LONG).show();
            Toast.makeText(getContext(), "Подсказка: Разрешения->Камера", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", scanForActivity(getContext()).getPackageName(), null);
            intent.setData(uri);
            scanForActivity(getContext()).startActivity(intent);
        } else {
            ActivityCompat.requestPermissions(scanForActivity(getContext()), new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);
        }
    }

    private boolean isCameraGranted(){
        return ContextCompat.checkSelfPermission(
                getContext(),
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    public interface ImageSavedCallback {
        void onResults(String imagePath);
    }


    private void applyOrientation(Mat rgba, boolean clockwise, int rotation) {
        if (rotation == Surface.ROTATION_0) {
            // Rotate clockwise / counter clockwise 90 degrees
            Mat rgbaT = rgba.t();
            Core.flip(rgbaT, rgba, clockwise ? 1 : -1);
            rgbaT.release();
        } else if (rotation == Surface.ROTATION_270) {
            // Rotate clockwise / counter clockwise 180 degrees
            Mat rgbaT = rgba.t();
            Core.flip(rgba.t(), rgba, clockwise ? 1 : -1);
            rgbaT.release();
            Mat rgbaT2 = rgba.t();
            Core.flip(rgba.t(), rgba, clockwise ? 1 : -1);
            rgbaT2.release();
        }
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        try {
            Mat mat = Imgcodecs.imdecode(new MatOfByte(data), Imgcodecs.CV_LOAD_IMAGE_UNCHANGED);
            applyOrientation(mat, true, rotation);

            Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2RGB);

            Bitmap btm = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(mat, btm);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            btm.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
            byte[] byteArray = byteArrayOutputStream.toByteArray();

            FileOutputStream fos = new FileOutputStream(this.filename);
            fos.write(byteArray);
            fos.close();
        } catch (java.io.IOException e) {
            Log.e("PictureDemo", "Exception in photoCallback", e);
        }
        onSavedCallback.onResults(this.filename.getAbsolutePath());
    }

    public void takePicture(final File fileName) {
        Log.i("PICTURE", "Taking picture");
        this.filename = fileName;
        // Postview and jpeg are sent in the same buffers if the queue is not empty when performing a capture.
        // Clear up buffers to avoid mCamera.takePicture to be stuck because of a memory issue
        mCamera.setPreviewCallback(null);

        // PictureCallback is implemented by the current class

        mCamera.takePicture(null, null, this);
    }


    private final Matrix mMatrix = new Matrix();

    private void updateMatrix() {
        float mw = this.getWidth();
        float mh = this.getHeight();

        float hw = this.getWidth() / 2.0f;
        float hh = this.getHeight() / 2.0f;

        float cw = (float) Resources.getSystem().getDisplayMetrics().widthPixels; //Make sure to import Resources package
        float ch = (float) Resources.getSystem().getDisplayMetrics().heightPixels;

        float scale = cw / (float) mh;
        float scale2 = ch / (float) mw;
        if (scale2 > scale) {
            scale = scale2;
        }

        boolean isFrontCamera = mCameraIndex == CAMERA_ID_FRONT;

        mMatrix.reset();
        if (isFrontCamera) {
            mMatrix.preScale(-1, 1, hw, hh); //MH - this will mirror the camera
        }
        mMatrix.preTranslate(hw, hh);
        if (isFrontCamera) {
            mMatrix.preRotate(270);
        } else {
            mMatrix.preRotate(90);
        }
        mMatrix.preTranslate(-hw, -hh);
        mMatrix.preScale(scale, scale, hw, hh);
    }

    @Override
    public void layout(int l, int t, int r, int b) {
        super.layout(l, t, r, b);
        updateMatrix();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        updateMatrix();
    }

    public void setMScale(float value) {
        mScale = value;
    }

    public float getMScale() {
        return mScale;
    }

    protected void deliverAndDrawFrame(CvCameraViewFrame frame) { //replaces existing deliverAndDrawFrame
        Mat modified;

        if (mListener != null) {
            modified = mListener.onCameraFrame(frame);
        } else {
            modified = frame.rgba();
        }

        boolean bmpValid = true;
        if (modified != null) {
            try {
                if (mCacheBitmap == null)
                    mCacheBitmap = Bitmap.createBitmap(mFrameWidth, mFrameHeight, Bitmap.Config.ARGB_8888);

                Utils.matToBitmap(modified, mCacheBitmap);
            } catch (Exception e) {
                Log.e(TAG, "Mat type: " + modified);
                Log.e(TAG, "Bitmap type: " + mCacheBitmap.getWidth() + "*" + mCacheBitmap.getHeight());
                Log.e(TAG, "Utils.matToBitmap() throws an exception: " + e.getMessage());
                bmpValid = false;
            }
        }

        if (bmpValid && mCacheBitmap != null) {
            Canvas canvas = getHolder().lockCanvas();
            if (canvas != null) {
                canvas.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
                int saveCount = canvas.save();
                canvas.setMatrix(mMatrix);

                float scale1 = canvas.getWidth() * 1.0f / mCacheBitmap.getWidth();
                float scale2 = canvas.getHeight() * 1.0f / mCacheBitmap.getHeight();

                float scale = mScale * Math.min(scale1, scale2);

                Rect src = new Rect(0, 0, mCacheBitmap.getWidth(), mCacheBitmap.getHeight());

                Rect dst = new Rect((int) ((canvas.getWidth() - scale * mCacheBitmap.getWidth()) / 2),
                        (int) ((canvas.getHeight() - scale * mCacheBitmap.getHeight()) / 2),
                        (int) ((canvas.getWidth() - scale * mCacheBitmap.getWidth()) / 2 + scale * mCacheBitmap.getWidth()),
                        (int) ((canvas.getHeight() - scale * mCacheBitmap.getHeight()) / 2 + scale * mCacheBitmap.getHeight()));

                canvas.drawBitmap(mCacheBitmap, src, dst, null);

                //Restore canvas after draw bitmap
                canvas.restoreToCount(saveCount);

                if (mFpsMeter != null) {
                    mFpsMeter.measure();
                    mFpsMeter.draw(canvas, 20, 30);
                }
                getHolder().unlockCanvasAndPost(canvas);
            }
        }
    }

    public interface Quality {
        int LOW = 0;
        int MEDIUM = 1;
        int HIGH = 2;
        int MAX = 3;
    }

    private CvCameraViewListener2 createCvCameraViewListener() {
        mListener = new CvCameraViewListener2() {
            @Override
            public void onCameraViewStarted(int width, int height) {
                rotation = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
                initResolutions();
                setFlashMode(torchEnabled);
            }

            @Override
            public void onCameraViewStopped() {

            }

            @Override
            public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
                Mat rgba = inputFrame.rgba();

                Log.d("CAM_RGB", "RGB");

                if (callback != null) {
                    Analyzer.getInstance(new WeakReference<>(getContext())).process(rgba, rotation, new Analyzer.ResultsCallback() {
                        @Override
                        public void onResults(Boolean hasMeter) {
                            if (getContext() == null) return;
                            Log.d("CAM_MSG", hasMeter.toString());

                            if (hasMeter) {
                                goodPhotoCounter++;
                                if (goodPhotoCounter > MIN_COUNT) {
                                    callback.onResults(true);
                                }
                            } else if (goodPhotoCounter > 0) {
                                goodPhotoCounter--;
                            }
                        }

                        @Override
                        public void onFail() {
                            if (getContext() == null) return;
//                            callback.onFail();
                        }
                    }, new WeakReference<>(getContext()));
                }
                return rgba;
            }
        };
        return mListener;
    }

    public static Activity scanForActivity(Context viewContext) {
        if (viewContext == null)
            return null;
        else if (viewContext instanceof Activity)
            return (Activity) viewContext;
        else if (viewContext instanceof ContextWrapper)
            return scanForActivity(((ContextWrapper) viewContext).getBaseContext());

        //else if (viewContext instanceof ThemedReactContext)
        //    return ((ThemedReactContext) viewContext).getCurrentActivity();

        return null;
    }

    //@Override
    public void setOnImageSavedCallback(ImageSavedCallback onSavedCallback) {
        this.onSavedCallback = onSavedCallback;
    }

    @Override
    public void setResultsCallback(Analyzer.ResultsCallback callback) {
        this.callback = callback;
    }

    private void initResolutions() {
        List<Size> resolutionList = mCamera.getParameters().getSupportedPreviewSizes();
        maxResolution = mCamera.getParameters().getPreviewSize();
        highResolution = maxResolution;
        mediumResolution = maxResolution;
        lowResolution = maxResolution;

        ListIterator<Size> resolutionItr = resolutionList.listIterator();
        while (resolutionItr.hasNext()) {
            Size s = resolutionItr.next();
            if (s.width > maxResolution.width && s.height > maxResolution.height)
                maxResolution = s;

            if (s.width < highResolution.width && s.height < highResolution.height && mediumResolution.equals(highResolution)) {
                mediumResolution = s;
            } else if (s.width < mediumResolution.width && s.height < mediumResolution.height) {
                lowResolution = s;
            }
        }
        if (lowResolution.equals(highResolution)) {
            lowResolution = mediumResolution;
        }
        applyQuality(quality);
    }


    private void setResolution(Size resolution) {
        if (resolution == null) return;
        disconnectCamera();
        mMaxHeight = resolution.height;
        mMaxWidth = resolution.width;
        connectCamera(resolution.width, resolution.height);
    }

    public void setQuality(int captureQuality) {
//        switch (captureQuality) {
//            case ALPRCameraManager.ALPRCameraCaptureSessionPreset.ALPRCameraCaptureSessionPresetLow:
//                this.quality = Quality.LOW;
//                this.setQuality = 0;
//                break;
//            case ALPRCameraManager.ALPRCameraCaptureSessionPreset.ALPRCameraCaptureSessionPresetMedium:
//                this.quality = Quality.MEDIUM;
//                this.setQuality = 1;
//                break;
//            case ALPRCameraManager.ALPRCameraCaptureSessionPreset.ALPRCameraCaptureSessionPresetHigh:
//            case ALPRCameraManager.ALPRCameraCaptureSessionPreset.ALPRCameraCaptureSessionPresetPhoto:
//            case ALPRCameraManager.ALPRCameraCaptureSessionPreset.ALPRCameraCaptureSessionPreset480p:
//            case ALPRCameraManager.ALPRCameraCaptureSessionPreset.ALPRCameraCaptureSessionPreset720p:
//            case ALPRCameraManager.ALPRCameraCaptureSessionPreset.ALPRCameraCaptureSessionPreset1080p:
//                this.quality = Quality.HIGH;
//                this.setQuality = 2;
//                break;
//
//        }
//        applyQuality(quality);
    }

    @Override
    public void setAspect(int aspect) {
//        disableView();
//        switch (aspect) {
//            case ALPRCameraManager.ALPRCameraAspect.ALPRCameraAspectFill:
//                this.aspect = JavaCameraView.ALPRCameraAspect.ALPRCameraAspectFill;
//                break;
//            case ALPRCameraManager.ALPRCameraAspect.ALPRCameraAspectFit:
        this.setAspect(1);
//                break;
//            case ALPRCameraManager.ALPRCameraAspect.ALPRCameraAspectStretch:
//                this.aspect = JavaCameraView.ALPRCameraAspect.ALPRCameraAspectStretch;
//                break;
//        }
//        onResumeALPR();
    }

    private void applyQuality(int quality) {
        switch (quality) {
            case Quality.LOW:
                setResolution(lowResolution);
                break;
            case Quality.MEDIUM:
                setResolution(mediumResolution);
                break;
            case Quality.HIGH:
                setResolution(highResolution);
                break;
            case Quality.MAX:
                setResolution(maxResolution);
                break;
        }
    }


    @Override
    public void onResumeALPR() {
        if (getContext() == null)
            return;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            try {
                if(OpenCVLoader.initDebug()){
                    setCvCameraViewListener(createCvCameraViewListener());
                    CameraView.this.enableView();
                }else{
                    Log.e("repair", "OpenCVLoader no");
                }
            }catch (Exception e){
                Log.e("repair", "unbelievable exception");
            }
            return;
        }


        if (isCameraGranted()) {
            try {
                if(OpenCVLoader.initDebug()){
                    setCvCameraViewListener(createCvCameraViewListener());
                    CameraView.this.enableView();
                }else{
                    Log.e("repair", "OpenCVLoader no");
                }
            }catch (Exception e){
                Log.e("repair", "unbelievable exception");
            }
            return;
        }

        if (ActivityCompat.shouldShowRequestPermissionRationale(scanForActivity(getContext()), Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(scanForActivity(getContext()), new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);

            Toast.makeText(getContext(), "Предоставьте доступ к камере в настройках приложения.", Toast.LENGTH_LONG).show();
            Toast.makeText(getContext(), "Подсказка: Разрешения->Камера", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", scanForActivity(getContext()).getPackageName(), null);
            intent.setData(uri);
            scanForActivity(getContext()).startActivity(intent);
        } else {
            ActivityCompat.requestPermissions(scanForActivity(getContext()), new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);
        }

        Log.e("repair", "stub");





        //setCvCameraViewListener(createCvCameraViewListener());
        //CameraView.this.enableView();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (tapToFocusEnabled && mCamera != null) {
            Camera camera = mCamera;
            camera.cancelAutoFocus();
            Rect focusRect = new Rect(-1000, -1000, 1000, 0);

            Parameters parameters = camera.getParameters();
            if (parameters.getFocusMode().equals(Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Parameters.FOCUS_MODE_AUTO);
            }

            if (parameters.getMaxNumFocusAreas() > 0) {
                List<Area> mylist = new ArrayList<Area>();
                mylist.add(new Area(focusRect, 1000));
                parameters.setFocusAreas(mylist);
            }

            try {
                camera.cancelAutoFocus();
                camera.setParameters(parameters);
                camera.startPreview();

                /*camera.autoFocus((success, camera1) -> {
                    if (camera1.getParameters().getFocusMode().equals(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                        Parameters parameters1 = camera1.getParameters();
                        parameters1.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                        if (parameters1.getMaxNumFocusAreas() > 0) {
                            parameters1.setFocusAreas(null);
                        }
                        camera1.setParameters(parameters1);
                        camera1.startPreview();
                    }
                });*/
            } catch (Exception e) {
                Log.e(TAG, "onTouchEvent", e);
            }
        }
        return true;
    }

    @Override
    public void setCountry(String country) {
        this.country = country;
    }

    @Override
    public void setZoom(int zoom) {
    }

    @Override
    public void setTapToFocus(boolean enabled) {
        tapToFocusEnabled = enabled;
    }

    @Override
    public void setPlateBorderEnabled(boolean enabled) {
        plateBorderEnabled = enabled;
    }

    @Override
    public void setPlateBorderColorHex(String colorStr) {
        colorStr = colorStr.replace("#", "");
        int color = Integer.parseInt(colorStr, 16);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = (color >> 0) & 0xFF;
        plateBorderRgb = new int[]{r, g, b};
    }

    private void setFlashMode(boolean torchEnabled) {
        if (mCamera == null) {
            return;
        }
        Parameters params = mCamera.getParameters();
        List<String> FlashModes = params.getSupportedFlashModes();
        if (torchEnabled) {
            if (FlashModes != null && FlashModes.contains(Parameters.FLASH_MODE_TORCH)) {
                params.setFlashMode(Parameters.FLASH_MODE_TORCH);
            } else {
                Log.e(TAG, "Torch Mode not supported");
            }
        } else {
            if (FlashModes != null && FlashModes.contains(Parameters.FLASH_MODE_OFF)) {
                params.setFlashMode(Parameters.FLASH_MODE_OFF);
            }
        }
        mCamera.setParameters(params);
    }

    @Override
    public void setRotateMode(boolean isLandscape) {
        Context context = getContext();
        if (context == null) return;
        Activity activity = scanForActivity(context);
        if (activity == null) return;
        activity.setRequestedOrientation(isLandscape
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Override
    public void setTorchMode(boolean enabled) {
        this.torchEnabled = enabled;
        setFlashMode(enabled);
    }

    @Override
    public void disableView() {
        super.disableView();
        Analyzer.getInstance(new WeakReference<>(getContext())).finish();
    }
}
