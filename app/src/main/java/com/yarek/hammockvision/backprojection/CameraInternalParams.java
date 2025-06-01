package com.yarek.hammockvision.backprojection;

/**
 * Class describes camera matrix with fx, fy, skew, u, v
 */
public class CameraInternalParams {

    private int[] resolution;

    public double fx;
    public double fy;
    public double u;
    public double v;
    public double skew = 0;


    public CameraInternalParams(double fx, double fy, double u, double v, int[] resolution) {
        this.fx = fx;
        this.fy = fy;
        this.u = u;
        this.v = v;

        this.resolution = resolution;
    }

    // TODO: move into a separate file
    public CameraInternalParams() {
        this.fx = 1035.990987371761;
        this.fy = 1037.5660299778167;
        this.u = 647.1976126037574;
        this.v = 501.28397224530966;

        resolution = new int[2];
        resolution[0] = 1280;
        resolution[1] = 960;
    }

    /**
     * Sets the new resolution, adoption other params
     */
    public void stretchResolution(int newWidth, int newHeight) {

        float scaleX = (float)newWidth / this.resolution[0];
        float scaleY = (float)newHeight / this.resolution[1];

        this.fx *= scaleX;
        this.u *= scaleX;

        this.fy *= scaleY;
        this.v *= scaleY;

        this.resolution[0] = newWidth;
        this.resolution[1] = newHeight;
    }

    public int[] getResolution() {
        return resolution;
    }


}
