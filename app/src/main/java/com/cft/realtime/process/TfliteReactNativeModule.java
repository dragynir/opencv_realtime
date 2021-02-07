package com.cft.realtime.process;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;

import com.cft.realtime.process.result.Answer;
import com.cft.realtime.process.result.MetersAnalyzer;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class TfliteReactNativeModule {

    private final Context reactContext;
    private MetersAnalyzer analyzer;


    public TfliteReactNativeModule(Context reactContext) {
        this.reactContext = reactContext;
    }

    public void loadModels() throws IOException {
        analyzer = new MetersAnalyzer(reactContext);
    }

    private static int exifToDegrees(int exifOrientation) {
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) { return 90; }
        else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {  return 180; }
        else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {  return 270; }
        return 0;
    }

    public String analyze(final String path, final Boolean isWater) {
        String imagePath = path.replace("file://", "");

		int rotationInDegrees = 0;
		Bitmap bitmapRaw = null;
		try {
		  ExifInterface exif = new ExifInterface(imagePath);
		  int rotation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
		  rotationInDegrees = exifToDegrees(rotation);
		  InputStream inputStream = new FileInputStream(imagePath);
		  bitmapRaw = BitmapFactory.decodeStream(inputStream);
		  Answer answer = analyzer.analyze(bitmapRaw, reactContext.getAssets(), isWater, rotationInDegrees);
		  return answer.toJson();
		}catch (IOException exc){
		  return null;
		}
    }

}