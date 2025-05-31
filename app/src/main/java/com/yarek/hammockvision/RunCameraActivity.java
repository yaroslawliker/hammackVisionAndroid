// Уточнений Java-код RunCameraActivity з правильним постпроцесингом для YOLOv7 TFLite

package com.yarek.hammockvision;

import static android.content.ContentValues.TAG;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
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

import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.google.common.util.concurrent.ListenableFuture;
import com.yarek.hammockvision.objectdetection.Detection;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
public class RunCameraActivity extends AppCompatActivity {

    PreviewView previewView;
    OverlayView overlayView;

    Interpreter tfliteInterpreter;

    static final int MODEL_INPUT_SIZE = 640;
    static final int MODEL_INPUT_CHANNELS = 3;
    BackProjector backProjector;

    int analyzeEveryFrames = 30;
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
            tfliteInterpreter = new Interpreter(FileUtil.loadMappedFile(this, "yolov7-tiny.tflite"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int[] outputShape = tfliteInterpreter.getOutputTensor(0).shape();
        Log.d("TFLite", Arrays.toString(outputShape));
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
//                backProjectAndDebug(new float[]{791, 437});
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
//        saveBitmapToGallery(getApplicationContext(), bitmap, new RectF(
//                    output[0][1],
//                    output[0][2],
//                    output[0][3],
//                    output[0][4]
//                        ),
//                "my_image_" + System.currentTimeMillis() + ".png");


        for (float[] detection: output){
            Log.d("Detection", Arrays.toString(detection));
            float confidence = 1 / (1 + (float)Math.exp(-detection[4]));
            Log.d("Detection", "scoreSigm" + String.valueOf(confidence));
        }

//        Set<String> uniqueBoxes = new HashSet<>();
//        for (int i = 0; i < 100; i++) {
//            float[] detection = output[i]; // або output[i] залежно від форми
//            String key = Arrays.toString(detection);
//            uniqueBoxes.add(key);
//        }

        List<Detection> results = parseOutputs(output);
//        List<Detection> finalResults = nonMaxSuppression(results, 0.45f);

        List<Detection> viewSizeResults = new ArrayList<>();

        for (Detection result: results) {
            RectF detect = new RectF(
                    result.pixelStartX, result.pixelStartY,
                    result.pixelEndX, result.pixelEndY
                    );
            RectF viewScale = scaleRectF(detect, MODEL_INPUT_SIZE, new Point(previewView.getWidth(), previewView.getHeight()));

            viewSizeResults.add(new Detection(
                    viewScale.left, viewScale.top,
                    viewScale.right, viewScale.bottom,
                    result.confidence, result.classId
                    ));
        }
        overlayView.setResults(results);

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


    private List<Detection> parseOutputs(float[][] output) {
        List<Detection> results = new ArrayList<>();
        // Просто зберігаємо всі бокси, або якщо хочеш - можна додати якийсь фіктивний confidence = 1
        for (float[] detectionArray : output) {

            float pixelStartX = detectionArray[1];
            float pixelStartY = detectionArray[2];
            float pixelEndX = detectionArray[3];
            float pixelEndY = detectionArray[4];
            int classId = (int)detectionArray[5];
            float score = detectionArray[6];

            if (score < 0.2) continue;


            Detection detection = new Detection(
                    pixelStartX, pixelStartY,
                    pixelEndX, pixelEndY,
                    score,
                    classId
            );

            results.add(detection);
        }
//        debugPrint(results);
        return results;
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
            Log.d(TAG, String.format("  boundingBox: (%.2f, %.2f) - (%.2f, %.2f)", r.pixelStartX, r.pixelStartY, r.pixelEndX, r.pixelEndY));
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

    public void saveBitmapToGallery(Context context, Bitmap bitmap, RectF box, String displayName) {

        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setAlpha(113);
        paint.setStyle(Paint.Style.FILL);

        canvas.drawRect(box, paint);

        OutputStream fos;
        ContentResolver resolver = context.getContentResolver();

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.WIDTH, bitmap.getWidth());
        values.put(MediaStore.Images.Media.HEIGHT, bitmap.getHeight());

        // Для Android Q (API 29) і вище: встановлюємо відносний шлях
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyAppImages"); // Галерея/Картинки/MyAppImages
        }

        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        try {
            if (uri != null) {
                fos = resolver.openOutputStream(uri);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                if (fos != null) {
                    fos.flush();
                    fos.close();
                }
                Log.d("SaveToGallery", "Збережено до галереї: " + uri.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private RectF scaleRectF(RectF rect, int detectSize, Point neededSize) {
        int w = neededSize.x;
        int h = neededSize.y;

        float scaleBack = (float) h / detectSize;

        RectF unscaled = new RectF(
                rect.left * scaleBack,
                rect.top * scaleBack,
                rect.right * scaleBack,
                rect.bottom * scaleBack
        );

        float offsetX = (w - h) / 2f;
        unscaled.offset(offsetX, 0);

        return unscaled;
    }






}
