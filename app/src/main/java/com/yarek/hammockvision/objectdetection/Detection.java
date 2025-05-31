package com.yarek.hammockvision.objectdetection;

public class Detection {

    public Detection(
            float pixelStartX, float pixelStartY,
            float pixelEndX, float normalizedY2,
            float confidence, int classId
    ) {
        this.pixelStartX = pixelStartX;
        this.pixelStartY = pixelStartY;

        this.pixelEndX = pixelEndX;
        this.pixelEndY = normalizedY2;

        this.confidence = confidence;

        this.classId = classId;
    }

    public float pixelStartX;
    public float pixelStartY;
    public float pixelEndX;
    public float pixelEndY;
    public float confidence;
    public int classId;

}
