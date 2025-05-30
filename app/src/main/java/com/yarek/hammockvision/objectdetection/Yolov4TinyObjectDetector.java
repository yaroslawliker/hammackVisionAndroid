package com.yarek.hammockvision.objectdetection;

import android.graphics.Bitmap;
import android.graphics.RectF;

import com.yarek.hammockvision.RunCameraActivity;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class Yolov4TinyObjectDetector implements ObjectDetector {

    private final int MODEL_INPUT_SIZE;
    Interpreter tfliteInterpreter;


    public Yolov4TinyObjectDetector(int modelInputSize, Interpreter tfliteInterpreter) {
        MODEL_INPUT_SIZE = modelInputSize;
        this.tfliteInterpreter = tfliteInterpreter;
    }

    @Override
    public Bitmap prepareImage(Bitmap original) {

        int size = Math.min(original.getWidth(), original.getHeight());
        Bitmap cropped = Bitmap.createBitmap(original, (original.getWidth() - size) / 2, (original.getHeight() - size) / 2, size, size);
        return Bitmap.createScaledBitmap(cropped, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true);
    }

    @Override
    public List<Detection> parseOutput(Object output) {
        float[][] outputFloat = ((float[][][]) output)[0];

        List<Detection> results = new ArrayList<>();
        for (float[] detection : outputFloat) {

            float cx = detection[0];
            float cy = detection[1];
            float w = detection[2];
            float h = detection[3];

            RectF rect = new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);

            // Confidence і label ставимо умовно
//            results.add(new RunCameraActivity.DetectionResult(rect, "unknown", 1.0f));
        }
        return results;


    }

    @Override
    public List<Detection> runInference(Bitmap image) {

        Bitmap prepared = prepareImage(image);

        ByteBuffer inputBuffer = convertBitmapToByteBuffer(prepared);
        float[][][] output = new float[1][2535][4];
        tfliteInterpreter.run(inputBuffer, output);

        return parseOutput(output);
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(4 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3);
        buffer.order(ByteOrder.nativeOrder());
        int[] pixels = new int[MODEL_INPUT_SIZE * MODEL_INPUT_SIZE];
        bitmap.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE);
        for (int pixel : pixels) {
            buffer.putFloat(((pixel >> 16) & 0xFF) / 255.f);
            buffer.putFloat(((pixel >> 8) & 0xFF) / 255.f);
            buffer.putFloat((pixel & 0xFF) / 255.f);
        }
        return buffer;
    }
}
