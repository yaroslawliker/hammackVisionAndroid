package com.yarek.hammockvision.objectdetection;

public class Detection {

    public Detection(
            float normalizedX1, float normalizedY1,
            float normalizedX2, float normalizedY2,
            float confidence, int classId
    ) {
        this.normalizedX1 = normalizedX1;
        this.normalizedY1 = normalizedY1;

        this.normalizedX2 = normalizedX2;
        this.normalizedY2 = normalizedY2;

        this.confidence = confidence;

        this.classId = classId;
    }

    public float normalizedX1;
    public float normalizedY1;
    public float normalizedX2;
    public float normalizedY2;
    public float confidence;
    public int classId;

}
