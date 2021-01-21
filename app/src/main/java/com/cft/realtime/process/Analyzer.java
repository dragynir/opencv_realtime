package com.cft.realtime.process;


import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import com.cft.realtime.process.model.MetersFinder;
import com.google.gson.JsonSyntaxException;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Analyzer {
    private static final String TAG = Analyzer.class.getSimpleName();
    private static Analyzer instance;
    private Handler mHandler = null;
    private AtomicBoolean isProcessing = new AtomicBoolean(false);
    private MetersFinder metersFinder;


    /**
     * constructor for initialization message handling on separate thread
     */
    private Analyzer(Context context) {
        // load models
        metersFinder = new MetersFinder(context.getAssets());

        HandlerThread handlerThread = new HandlerThread("HandlerThread");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Log.i(TAG, "handleMessage");
                HashMap<String, Object> data = (HashMap<String, Object>) msg.obj;
                if ((Boolean) data.get("finish")) {
                    // finishAlpr();
                } else {
                    executeRecognition(
                            (Mat) data.get("m"),
                            (ResultsCallback) data.get("callback"),
                            (WeakReference<Context>) data.get("context"),
                            (int) data.get("rotation")
                    );
                }

            }
        };
    }

    /**
     * singleton implementation, is used from main thread only
     */
    static Analyzer getInstance(WeakReference<Context> context) {
        if (instance == null) {
            instance = new Analyzer(context.get());
        }
        return instance;
    }

    public interface ResultsCallback {
        void onResults(Boolean hasMeter);
        void onFail();
    }

    /**
     * prepare data and recognize the license plate
     */
    private void executeRecognition(final Mat m, final ResultsCallback callback, final WeakReference<Context> context, int rotation) {
        Log.i(TAG, "executeRecognition");
        Context ctx = context.get();

        if (ctx == null) {
            finishExecution(ctx, callback);
            return;
        }
        if (m.cols() == 0 || m.rows() == 0) {
            finishExecution(ctx, callback);
            return;
        }

        applyOrientation(m, true, rotation);

        Bitmap bm = Bitmap.createBitmap(m.cols(), m.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(m, bm);


        final boolean hasMeter = metersFinder.findMeter(bm);

        Handler handler = new Handler(ctx.getMainLooper());

        handler.post(new Runnable() {

            @Override
            public void run() {

                callback.onResults(hasMeter);
                // callback.onFail();

                if (isProcessing != null) isProcessing.set(false);


            }
        });
    }

    /**
     * finishes processing if something went wrong
     */
    private void finishExecution(Context ctx, final ResultsCallback callback) {
        if (ctx == null) {
            if (isProcessing != null) isProcessing.set(false);
            return;
        }
        Handler handler = new Handler(ctx.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (callback != null) callback.onFail();
                if (isProcessing != null) isProcessing.set(false);
            }
        });
    }

    /**
     * applies proper orientation for an image
     */
    private static void applyOrientation(Mat rgba, boolean clockwise, int rotation) {
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

    /**
     * entry point for frame processing request
     */
    void process(final Mat m, int rotation, final ResultsCallback callback, final WeakReference<Context> context) {

        // post message to separate thread with image and related data
        if (!mHandler.hasMessages(10) && isProcessing.compareAndSet(false, true)) {
            HashMap<String, Object> map = new HashMap<>();
            map.put("m", m.clone());
            map.put("callback", callback);
            map.put("context", context);
            map.put("rotation", rotation);
            map.put("finish", false);
            Message mes = mHandler.obtainMessage(10, 0, 0, map);
            mHandler.sendMessage(mes);
        }
    }

    void finish() {
        // for next version
    }

}
