package com.cft.realtime.process;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Area;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;


public class CameraView extends JavaCameraView implements ICameraView {

    private static final String TAG = CameraView.class.getSimpleName();
    private Analyzer.ResultsCallback callback;
    private int quality = Quality.MEDIUM;
    private Size highResolution;
    private Size mediumResolution;
    private Size lowResolution;
    private List<Point> coordinates;
    private int[] plateBorderRgb = new int[]{0, 0, 255};
    private boolean plateBorderEnabled;
    private String country = "us";
    private boolean tapToFocusEnabled;
    private boolean torchEnabled = false;
    private int rotation;

    public CameraView(Context context, int cameraId) {
        super(context, cameraId);
    }

    public CameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }


    public interface Quality {
        int LOW = 0;
        int MEDIUM = 1;
        int HIGH = 2;
    }

    private CvCameraViewListener2 createCvCameraViewListener() {
        return new CvCameraViewListener2() {
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

//                if (callback != null) {
                    Analyzer.getInstance(new WeakReference<>(getContext())).process(rgba, rotation, new Analyzer.ResultsCallback() {
                        @Override
                        public void onResults(Boolean hasMeter) {
                            if (getContext() == null) return;
                            Log.d("CAM_MSG", hasMeter.toString());
//                            callback.onResults(hasMeter);
                        }

                        @Override
                        public void onFail() {
                            if (getContext() == null) return;
//                            callback.onFail();
                        }
                    }, new WeakReference<>(getContext()));
//                }

                return rgba;
            }
        };
    }

    public static Activity scanForActivity(Context viewContext) {
        if (viewContext == null)
            return null;
        else if (viewContext instanceof Activity)
            return (Activity) viewContext;
        else if (viewContext instanceof ContextWrapper)
            return scanForActivity(((ContextWrapper) viewContext).getBaseContext());
//
//        else if (viewContext instanceof ThemedReactContext)
//            return ((ThemedReactContext) viewContext).getCurrentActivity();

        return null;
    }

    private List<Point> getOpenCVPoints(List<android.graphics.Point> coordinates) {
        android.graphics.Point tl = coordinates.get(0);
        android.graphics.Point tr = coordinates.get(1);
        android.graphics.Point br = coordinates.get(2);
        android.graphics.Point bl = coordinates.get(3);

        final Point tlP = new Point(tl.x, tl.y);
        final Point trP = new Point(tr.x, tr.y);
        final Point brP = new Point(br.x, br.y);
        final Point blP = new Point(bl.x, bl.y);

        return new ArrayList<Point>() {{
            add(tlP);
            add(trP);
            add(brP);
            add(blP);
        }};
    }


    @Override
    public void setResultsCallback(Analyzer.ResultsCallback callback) {
        this.callback = callback;
    }


//
//    @Override
//    protected int[] getPlateBorderRgb() {
//        return plateBorderRgb;
//    }
//
//    @Override
//    protected Point[] getCoordinates() {
//        if (coordinates == null || !plateBorderEnabled) {
//            return null;
//        }
//        Point tl = coordinates.get(0);
//        Point br = coordinates.get(2);
//        Point tlP = new Point(widthOffset + tl.x * widthRatio, heightOffset + tl.y * heightRatio);
//        Point brP = new Point(widthOffset + br.x * widthRatio, heightOffset + br.y * heightRatio);
//
//        return new Point[]{tlP, brP};
//    }

    private void initResolutions() {
        List<Size> resolutionList = mCamera.getParameters().getSupportedPreviewSizes();
        highResolution = mCamera.getParameters().getPreviewSize();
        mediumResolution = highResolution;
        lowResolution = mediumResolution;

        ListIterator<Size> resolutionItr = resolutionList.listIterator();
        while (resolutionItr.hasNext()) {
            Size s = resolutionItr.next();
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
        connectCamera(getWidth(), getHeight());
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
//                this.aspect = JavaCameraView.ALPRCameraAspect.ALPRCameraAspectFit;
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
        }
    }

    @Override
    public void onResumeALPR() {
        if (getContext() == null) return;

        setCvCameraViewListener(createCvCameraViewListener());
        CameraView.this.enableView();

//        BaseLoaderCallback loaderCallback = new BaseLoaderCallback(getContext()) {
//            @Override
//            public void onManagerConnected(int status) {
//                switch (status) {
//                    case LoaderCallbackInterface.SUCCESS: {
//                        Log.i(TAG, "OpenCV loaded successfully");
//                        if (getContext() != null) {
//                            setCvCameraViewListener(createCvCameraViewListener());
//                            CameraView.this.enableView();
//                        }
//                    }
//                    break;
//                    default: {
//                        super.onManagerConnected(status);
//                    }
//                    break;
//                }
//            }
//        };
//        if (!OpenCVLoader.initDebug()) {
//            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
//            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, getContext(), loaderCallback);
//        } else {
//            Log.d(TAG, "OpenCV library found inside package. Using it!");
//            loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
//        }


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
                camera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        if (camera.getParameters().getFocusMode().equals(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                            Parameters parameters = camera.getParameters();
                            parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                            if (parameters.getMaxNumFocusAreas() > 0) {
                                parameters.setFocusAreas(null);
                            }
                            camera.setParameters(parameters);
                            camera.startPreview();
                        }
                    }
                });
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
//        disableView();
//        this.zoom = zoom;
//        onResumeALPR();
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
//        removeCvCameraViewListener();
        super.disableView();
        Analyzer.getInstance(new WeakReference<>(getContext())).finish();
    }
}
