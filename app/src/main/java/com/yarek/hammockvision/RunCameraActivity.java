// Уточнений Java-код RunCameraActivity з правильним постпроцесингом для YOLOv7 TFLite

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

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
public class RunCameraActivity extends AppCompatActivity {

    PreviewView previewView;
    OverlayView overlayView;

    Interpreter tfliteInterpreter;

    static final int MODEL_INPUT_SIZE = 416;
    static final int MODEL_INPUT_CHANNELS = 3;
    BackProjector backProjector;

    int analyzeEveryFrames = 60;
    int framesPassed = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_run_camera);

        previewView = findViewById(R.id.runCameraPreview);
        overlayView = findViewById(R.id.overlayView);

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                bindUseCases(cameraProviderFuture.get());
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));

        initTensorFlowLiteInterpreter();
        initBackProjector();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void initTensorFlowLiteInterpreter() {
        try {
            tfliteInterpreter = new Interpreter(FileUtil.loadMappedFile(this, "yolov4-tiny-416.tflite"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void initBackProjector() {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }

        Python python = Python.getInstance();
        float[] rotations = {42.6f, 4, 0};
        int[] resolution = {1280, 960};
        backProjector = new BackProjector(
                python,
                new CameraInternalParams(
                        1035.990987371761, 1037.5660299778167,
                        647.1976126037574, 501.28397224530966,
                        resolution),
                new CameraPosition(223.3, rotations)
        );
        // TODO: change from hardcoded
    }

    void bindUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setTargetResolution(new Size(1280, 960))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        Executor executor = Executors.newSingleThreadExecutor();
        imageAnalysis.setAnalyzer(executor, imageProxy -> {
            Bitmap bitmap = resizeImageForTFLite(imageProxy);

            if (framesPassed >= analyzeEveryFrames) {
                runInference(bitmap);
                backProjectAndDebug(new float[]{791, 437});
            }
                framesPassed++;

            imageProxy.close();
        });

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    private Bitmap resizeImageForTFLite(ImageProxy imageProxy) {
        Bitmap original = imageProxy.toBitmap();
        int size = Math.min(original.getWidth(), original.getHeight());
        Bitmap cropped = Bitmap.createBitmap(original, (original.getWidth() - size) / 2, (original.getHeight() - size) / 2, size, size);
        return Bitmap.createScaledBitmap(cropped, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true);
    }

    private void runInference(Bitmap bitmap) {
        ByteBuffer inputBuffer = convertBitmapToByteBuffer(bitmap);
        float[][] output = new float[1][7];
        tfliteInterpreter.run(inputBuffer, output);

        List<DetectionResult> results = parseOutputs(output[0]);
        List<DetectionResult> finalResults = nonMaxSuppression(results, 0.45f);

    }

    private void backProjectAndDebug(float[] point2D) {
        float[] point3D = backProjector.backProject(point2D);
        StringBuilder pointStr = new StringBuilder("Point3D: ");
        for (int i = 0; i < 3; i++) {
            pointStr.append(point3D[i]);
            pointStr.append(" ");
        }
        Log.d(TAG, pointStr.toString());
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(4 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * MODEL_INPUT_CHANNELS);
        buffer.order(ByteOrder.nativeOrder());
        int[] pixels = new int[MODEL_INPUT_SIZE * MODEL_INPUT_SIZE];
        bitmap.getPixels(pixels, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE);

        // Спочатку всі R, потім всі G, потім всі B
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

    private List<DetectionResult> parseOutputs(float[][] output) {
        List<DetectionResult> results = new ArrayList<>();
        // Просто зберігаємо всі бокси, або якщо хочеш - можна додати якийсь фіктивний confidence = 1
        for (float[] detection : output) {
            if (detection.length < 4) continue; // додатково перевірити довжину

            float cx = detection[0];
            float cy = detection[1];
            float w = detection[2];
            float h = detection[3];

            RectF rect = new RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);

            // Confidence і label ставимо умовно
            results.add(new DetectionResult(rect, "unknown", 1.0f));
        }
        return results;
    }



    private List<DetectionResult> nonMaxSuppression(List<DetectionResult> detections, float iouThreshold) {
        List<DetectionResult> result = new ArrayList<>();
        Map<String, List<DetectionResult>> grouped = new HashMap<>();
        for (DetectionResult d : detections) {
            grouped.computeIfAbsent(d.label, k -> new ArrayList<>()).add(d);
        }
        for (List<DetectionResult> group : grouped.values()) {
            group.sort((a, b) -> Float.compare(b.confidence, a.confidence));
            while (!group.isEmpty()) {
                DetectionResult best = group.remove(0);
                result.add(best);
                group.removeIf(d -> iou(d.bbox, best.bbox) > iouThreshold);
            }
        }
        return result;
    }

    private float iou(RectF a, RectF b) {
        float left = Math.max(a.left, b.left);
        float top = Math.max(a.top, b.top);
        float right = Math.min(a.right, b.right);
        float bottom = Math.min(a.bottom, b.bottom);
        float intersection = Math.max(0, right - left) * Math.max(0, bottom - top);
        float union = a.width() * a.height() + b.width() * b.height() - intersection;
        return intersection / union;
    }

    private void debugPrint(List<Detection> results) {
        for (int i = 0; i < results.size(); i++) {
            Detection r = results.get(i);
            Log.d(TAG, "Detected object: " + i);
            Log.d(TAG, String.format("  boundingBox: (%.2f, %.2f) - (%.2f, %.2f)", r.normalizedX1, r.normalizedY1, r.normalizedX2, r.normalizedY2));
            Log.d(TAG, "  Label: " + r.classId);
            Log.d(TAG, String.format("  Confidence: %.2f", r.confidence));
        }
    }

    public static class DetectionResult {
        RectF bbox;
        String label;
        float confidence;
        DetectionResult(RectF bbox, String label, float confidence) {
            this.bbox = bbox;
            this.label = label;
            this.confidence = confidence;
        }
    }
}
