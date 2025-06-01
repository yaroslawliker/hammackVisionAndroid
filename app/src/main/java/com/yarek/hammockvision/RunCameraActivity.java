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
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
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
import com.yarek.hammockvision.backprojection.BackProjector;
import com.yarek.hammockvision.backprojection.CameraInternalParams;
import com.yarek.hammockvision.backprojection.CameraPosition;
import com.yarek.hammockvision.objectdetection.Detection;
import com.yarek.hammockvision.objectdetection.ObjectDetector;
import com.yarek.hammockvision.objectdetection.Yolov7TinyObjectDetector;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
public class RunCameraActivity extends AppCompatActivity {

    PreviewView previewView;
    OverlayView overlayView;

    Interpreter tfliteInterpreter;
    ObjectDetector objectDetector;

    static final int MODEL_INPUT_SIZE = 640;
    static final int MODEL_INPUT_CHANNELS = 3;
    BackProjector backProjector;

    final int analyzeImageTime = 30;
    final int captureImageTime = 30;
    int framesPassed = 0;
    int[] resolution = {1280, 960};

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

        initTensorFlowLiteInterpreterAndDetector();
        initBackProjector();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void initTensorFlowLiteInterpreterAndDetector() {
        try {
            tfliteInterpreter = new Interpreter(FileUtil.loadMappedFile(this, "yolov7-tiny.tflite"));
            objectDetector = new Yolov7TinyObjectDetector(640, tfliteInterpreter);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void initBackProjector() {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }

        Python python = Python.getInstance();
        float[] rotations = {0, 0, 0};
        backProjector = new BackProjector(
                python,
                new CameraInternalParams(
                        1035.990987371761, 1037.5660299778167,
                        647.1976126037574, 501.28397224530966,
                        resolution),
                new CameraPosition(74.5, rotations)
        );
        // TODO: change from hardcoded
    }

    void bindUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        Executor executor = Executors.newSingleThreadExecutor();
        imageAnalysis.setAnalyzer(executor, imageProxy -> {
            Bitmap bitmap = imageProxy.toBitmap();

            if (framesPassed % analyzeImageTime == 0) {

                // Processing an inference
                List<Detection> detectionsAll = objectDetector.runInference(bitmap);
                List<Detection> detections = extractPersonDetections(detectionsAll);

                // Setting results to overlayView
                overlayView.setResults(detections);
                overlayView.setPreviewSize(previewView.getWidth(), previewView.getHeight());

                float[][] points3D = backProjectDetections(detections);

                if (framesPassed % captureImageTime == 0) {
                    saveBitmapToGallery(
                            getApplicationContext(), objectDetector.prepareImage(bitmap),
                            detections, points3D,
                            "my_image_" + System.currentTimeMillis() + ".png"
                            );
                }

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

    private float[][] backProjectDetections(List<Detection> detections) {

        // Back projecting necessary points
        float[][] points3D = new float[detections.size()][];
        for (int i = 0; i < detections.size(); i++) {
            float[] point2D = new float[2];
            Detection detection = detections.get(i);
            point2D[0] = (detection.pixelEndX + detection.pixelStartX) / 2;
            point2D[1] = detection.pixelEndY;

            Log.d("Distance", "raw = " + String.valueOf(point2D[0]) + " " + String.valueOf(point2D[1]));

            point2D = scalePoint(point2D, resolution);

            Log.d("Distance", "scaled = " + String.valueOf(point2D[0]) + " " + String.valueOf(point2D[1]));

            points3D[i] = backProjector.backProject(point2D);
        }

        return points3D;
    }

    public void saveBitmapToGallery(Context context, Bitmap bitmap,
                                    List<Detection> detections, float[][] distances,
                                    String displayName) {

        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setAlpha(63);
        paint.setStyle(Paint.Style.FILL);

        Paint textPaint = new Paint();
        textPaint.setTextSize(20);


        for (int i = 0; i < detections.size(); i++) {

            Detection detection = detections.get(i);

            RectF box = new RectF(
                    detection.pixelStartX,
                    detection.pixelStartY,
                    detection.pixelEndX,
                    detection.pixelEndY
            );

            canvas.drawRect(box, paint);
            canvas.drawText(String.valueOf(Math.round(detection.classId*100)/100f ), detection.pixelStartX, detection.pixelStartY, textPaint);
            canvas.drawText(String.valueOf( Math.round(detection.confidence*100)/100f), detection.pixelEndX, detection.pixelStartY, textPaint);
            String distanceStr = String.valueOf(Math.round(distances[i][0])) + ' ' + distances[i][2];
            canvas.drawText(distanceStr, detection.pixelStartX, detection.pixelEndY, textPaint);
        }


        OutputStream fos;
        ContentResolver resolver = context.getContentResolver();

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.WIDTH, bitmap.getWidth());
        values.put(MediaStore.Images.Media.HEIGHT, bitmap.getHeight());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyAppImages");
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

    float[] scalePoint(float[] pointInInferenceSize, int[] neededSize) {
        float scaleFactor = (float) neededSize[1] / (float) RunCameraActivity.MODEL_INPUT_SIZE;
        float[] scaled = new float[2];
        scaled[0] = pointInInferenceSize[0] * scaleFactor;
        scaled[1] = pointInInferenceSize[1] * scaleFactor;

        scaled[0] += ((float)neededSize[0] - neededSize[1]) / 2;
        return scaled;
    }

    List<Detection> extractPersonDetections(List<Detection> detections) {
        List<Detection> persons = new ArrayList<>();
        for(Detection detection: detections) {
            if (detection.classId == 0) {
                persons.add(detection);
            }
        }
        return persons;
    }

}
