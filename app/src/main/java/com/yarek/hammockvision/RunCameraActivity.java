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

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.IOException;
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
    ObjectDetector objectDetector;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;

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
                // No errors need to be handled for this Future.
                // This should never be reached.
            }
        }, ContextCompat.getMainExecutor(this));

        initObjectDetector();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

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
                int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();

                if (framesPassed >= analyzeEveryFrames) {

                    Bitmap processedImage = resizeImageForTFLite(imageProxy);

                    try {
                        executeObjectDetection(processedImage);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    framesPassed = 0;
                }
                framesPassed += 1;

                imageProxy.close();
            }
        });

        cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        camera = cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, preview, imageAnalysis);
    }

    public Bitmap resizeImageForTFLite(ImageProxy imageProxy) {
        Bitmap original = imageProxy.toBitmap();

        int size = Math.min(original.getWidth(), original.getHeight());
        int xOffset = (original.getWidth() - size) / 2;
        int yOffset = (original.getHeight() - size) / 2;

        Bitmap cropped = Bitmap.createBitmap(original, xOffset, yOffset, size, size);
        return Bitmap.createScaledBitmap(cropped, 640, 640, true);
    }

    private void initObjectDetector() {
        ObjectDetector.ObjectDetectorOptions options = ObjectDetector.ObjectDetectorOptions.builder()
                .setMaxResults(2)
                .setScoreThreshold(0.3f)
                .build();
        try {
            objectDetector = ObjectDetector.createFromFileAndOptions(
                    this,
                    "tiny_original.tflite",
                    options
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private void executeObjectDetection(Bitmap bitmap) throws IOException {
        TensorImage tensorImage = TensorImage.fromBitmap(bitmap);
        List<Detection> results = objectDetector.detect(tensorImage);

        debugPrint(results);
    }

    private void debugPrint(List<Detection> results) {
        for (Detection result: results) {
            RectF box = result.getBoundingBox();

            Log.d(TAG, "Detected object: ${i} ");
            Log.d(TAG, "  boundingBox: (${box.left}, ${box.top}) - (${box.right},${box.bottom})");

            for (int j = 0; j < result.getCategories().size(); j++) {
                Category category = result.getCategories().get(j);
                Log.d(TAG, "    Label " + j + ": " + category.getLabel());
                int confidence = (int) (category.getScore() * 100);
                Log.d(TAG, "    Confidence: " + confidence + "%");
            }
        }
    }



}