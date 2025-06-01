package com.yarek.hammockvision.backprojection;

/**
 * Class describes camera y and rotation
 */
public class CameraPosition {

    public CameraPosition(double y, float[] eulerRotationAngles) {
        this.y = y;
        this.eulerRotationAngles = eulerRotationAngles;
    }

    public double y;
    public float[] eulerRotationAngles = new float[3];
}
