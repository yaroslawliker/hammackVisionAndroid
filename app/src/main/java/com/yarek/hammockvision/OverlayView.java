package com.yarek.hammockvision;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PointF;
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
    private RectF coords = new RectF();
    private Point previewSize;

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

    public void setPreviewSize(int w, int h) {
        previewSize = new Point(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Detection result : results) {

            coords.left = result.pixelStartX;
            coords.top = result.pixelStartY;
            coords.right = result.pixelEndX;
            coords.bottom = result.pixelEndY;

            RectF rescaled = scaleRectF(coords, 640, previewSize);

            canvas.drawRect(rescaled, boxPaint);
            canvas.drawText(result.classId + " " + String.format("%.2f", result.confidence), rescaled.left, rescaled.top - 10, textPaint);
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
