package com.yarek.hammockvision;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.yarek.hammockvision.objectdetection.Detection;

import java.util.ArrayList;
import java.util.List;

public class OverlayView extends View {

    private final Paint boxPaint = new Paint();
    private final Paint textPaint = new Paint();
    private List<Detection> results = new ArrayList<>();

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        boxPaint.setColor(0xFFFF0000); // Red
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(5.0f);

        textPaint.setColor(0xFFFFFFFF); // White
        textPaint.setTextSize(50f);
        textPaint.setStyle(Paint.Style.FILL);
    }

    public void setResults(List<Detection> results) {
        this.results = results;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        for (Detection result : results) {
            RectF coords = new RectF(result.normalizedX1, result.normalizedX2, result.normalizedY1, result.normalizedY2);
            RectF box = scaleRect(coords);
            canvas.drawRect(box, boxPaint);
            canvas.drawText(result.classId + " " + String.format("%.2f", result.confidence), box.left, box.top - 10, textPaint);
        }
    }

    private RectF scaleRect(RectF rect) {
        float scaleX = (float) getWidth() / RunCameraActivity.MODEL_INPUT_SIZE;
        float scaleY = (float) getHeight() / RunCameraActivity.MODEL_INPUT_SIZE;

        return new RectF(
                rect.left * scaleX,
                rect.top * scaleY,
                rect.right * scaleX,
                rect.bottom * scaleY
        );
    }
}
