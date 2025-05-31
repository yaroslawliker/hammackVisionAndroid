package com.yarek.hammockvision.objectdetection;

import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class Yolov7TinyObjectDetector implements ObjectDetector {

    private final int MODEL_INPUT_SIZE;
    Interpreter tfliteInterpreter;


    public Yolov7TinyObjectDetector(int modelInputSize, Interpreter tfliteInterpreter) {
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
    public List<Detection> parseOutput(Object outputObject) {

        float[][] output = (float[][])outputObject;

        List<Detection> results = new ArrayList<>();
        for (float[] detectionArray : output) {

            float pixelStartX = detectionArray[1];
            float pixelStartY = detectionArray[2];
            float pixelEndX = detectionArray[3];
            float pixelEndY = detectionArray[4];
            int classId = (int)detectionArray[5];
            float score = detectionArray[6];

            if (score < 0.35) continue;


            Detection detection = new Detection(
                    pixelStartX, pixelStartY,
                    pixelEndX, pixelEndY,
                    score,
                    classId
            );

            results.add(detection);
        }
        return results;
    }

    @Override
    public List<Detection> runInference(Bitmap bitmap) {
        Bitmap prepared = prepareImage(bitmap);
        ByteBuffer inputBuffer = convertBitmapToByteBuffer(prepared);
        float[][] output = new float[100][7];
        tfliteInterpreter.run(inputBuffer, output);
        return parseOutput(output);
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(4 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 3);
        buffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[MODEL_INPUT_SIZE * MODEL_INPUT_SIZE];
        bitmap.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE);

        for (int c = 0; c < 3; c++) {
            for (int i = 0; i < pixels.length; i++) {
                int pixel = pixels[i];
                float value;
                switch (c) {
                    case 0: value = ((pixel >> 16) & 0xFF) / 255.f; break; // R
                    case 1: value = ((pixel >> 8) & 0xFF) / 255.f; break;  // G
                    case 2: value = (pixel & 0xFF) / 255.f; break;         // B
                    default: value = 0;
                }
                buffer.putFloat(value);
            }
        }

        return buffer;
    }
}
