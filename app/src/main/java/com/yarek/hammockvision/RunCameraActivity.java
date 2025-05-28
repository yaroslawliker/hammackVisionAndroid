package com.yarek.hammockvision;

import android.graphics.Bitmap;
import android.os.Bundle;
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

//    ObjectDetector objectDetector;

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

                Bitmap processedImage = resizeImageForTFLite(imageProxy);



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



}