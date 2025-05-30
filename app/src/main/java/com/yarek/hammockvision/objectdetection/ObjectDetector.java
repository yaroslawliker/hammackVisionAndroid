package com.yarek.hammockvision.objectdetection;

import android.graphics.Bitmap;

import java.util.List;

public interface ObjectDetector {

    Bitmap prepareImage(Bitmap original);
    List<Detection> parseOutput(Object output);
    List<Detection> runInference(Bitmap image);

}
