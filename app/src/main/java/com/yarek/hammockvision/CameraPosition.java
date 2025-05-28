package com.yarek.hammockvision;

/**
 * Class describes camera y and rotation
 */
public class CameraPosition {

    CameraPosition(double y, float[] eulerRotationAngles) {
        this.y = y;
        this.eulerRotationAngles = eulerRotationAngles;
    }

    public double y;
    public float[] eulerRotationAngles = new float[3];
}
