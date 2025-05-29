package com.yarek.hammockvision;

import static android.content.ContentValues.TAG;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class RunCameraActivity extends AppCompatActivity {

    PreviewView previewView;

    ProcessCameraProvider cameraProvider;
    CameraSelector cameraSelector;
    Camera camera;
    Preview preview;
    ImageAnalysis imageAnalysis;
    Executor analisysExecutor;

    int analyzeEveryFrames = 60;
    int framesPassed = 0;

    Interpreter tfliteInterpreter;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

    static final int MODEL_INPUT_SIZE = 640;
    static final int MODEL_INPUT_CHANNELS = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_run_camera);

        previewView = findViewById(R.id.runCameraPreview);

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));

        initInterpreter();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void initInterpreter() {
        try {
            tfliteInterpreter = new Interpreter(FileUtil.loadMappedFile(this, "tiny_original.tflite"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void bindUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        preview = new Preview.Builder()
                .build();

        imageAnalysis = new ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetResolution(new Size(1280, 960))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        analisysExecutor = Executors.newSingleThreadExecutor();

        imageAnalysis.setAnalyzer(analisysExecutor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {

                if (framesPassed >= analyzeEveryFrames) {

                    Bitmap processedImage = resizeImageForTFLite(imageProxy);

                    runInference(processedImage);

                    framesPassed = 0;
                }
                framesPassed++;

                imageProxy.close();
            }
        });

        cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        camera = cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageAnalysis);
    }

    public Bitmap resizeImageForTFLite(ImageProxy imageProxy) {
        Bitmap original = imageProxy.toBitmap();

        int size = Math.min(original.getWidth(), original.getHeight());
        int xOffset = (original.getWidth() - size) / 2;
        int yOffset = (original.getHeight() - size) / 2;

        Bitmap cropped = Bitmap.createBitmap(original, xOffset, yOffset, size, size);
        return Bitmap.createScaledBitmap(cropped, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true);
    }

    private void runInference(Bitmap bitmap) {
        ByteBuffer inputBuffer = convertBitmapToByteBuffer(bitmap);

        // Відповідно до помилки модель дає вихід [100, 7]
        float[][] output = new float[100][7];

        tfliteInterpreter.run(inputBuffer, output);

        List<DetectionResult> detections = parseOutputs(output);

        debugPrint(detections);
    }


    // Перетворює Bitmap у ByteBuffer з float32 normalized в [0..1]
    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(4 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * MODEL_INPUT_CHANNELS);
        buffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[MODEL_INPUT_SIZE * MODEL_INPUT_SIZE];
        bitmap.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE);

        for (int i = 0; i < pixels.length; ++i) {
            int pixel = pixels[i];

            // ARGB -> RGB float normalized
            float r = ((pixel >> 16) & 0xFF) / 255.f;
            float g = ((pixel >> 8) & 0xFF) / 255.f;
            float b = (pixel & 0xFF) / 255.f;

            buffer.putFloat(r);
            buffer.putFloat(g);
            buffer.putFloat(b);
        }

        buffer.rewind();
        return buffer;
    }

    private static class DetectionResult {
        RectF bbox;
        String label;
        float confidence;

        DetectionResult(RectF bbox, String label, float confidence) {
            this.bbox = bbox;
            this.label = label;
            this.confidence = confidence;
        }
    }

    // Приклад простої постобробки (потрібно підлаштувати під структуру твоєї моделі!)
    private List<DetectionResult> parseOutputs(float[][] output) {
        List<DetectionResult> results = new ArrayList<>();

        float threshold = 0.3f;

        for (int i = 0; i < output.length; i++) {
            float[] detection = output[i];

            float confidence = detection[4];
            if (confidence < threshold) continue;

            float cx = detection[0];
            float cy = detection[1];
            float w = detection[2];
            float h = detection[3];

            int classId = 0;
            float maxClassScore = detection[5];
            for (int c = 6; c < detection.length; c++) {
                if (detection[c] > maxClassScore) {
                    maxClassScore = detection[c];
                    classId = c - 5;
                }
            }

            float finalConfidence = confidence * maxClassScore;
            if (finalConfidence < threshold) continue;

            float left = cx - w / 2;
            float top = cy - h / 2;
            float right = cx + w / 2;
            float bottom = cy + h / 2;

            RectF bbox = new RectF(left, top, right, bottom);

            String label = "class_" + classId;

            results.add(new DetectionResult(bbox, label, finalConfidence));
        }

        return results;
    }


    private void debugPrint(List<DetectionResult> results) {
        int i = 0;
        for (DetectionResult result : results) {
            RectF box = result.bbox;

            Log.d(TAG, "Detected object: " + i);
            Log.d(TAG, String.format("  boundingBox: (%.3f, %.3f) - (%.3f, %.3f)", box.left, box.top, box.right, box.bottom));
            Log.d(TAG, "    Label: " + result.label);
            Log.d(TAG, "    Confidence: " + (int) (result.confidence * 100) + "%");
            i++;
        }
    }
}
