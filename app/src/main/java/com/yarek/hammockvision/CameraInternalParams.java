package com.yarek.hammockvision;

/**
 * Class describes camera matrix with fx, fy, skew, u, v
 */
public class CameraInternalParams {

    public double fx;
    public double fy;
    public double u;
    public double v;
    public double skew = 0;


    CameraInternalParams(double fx, double fy, double u, double v) {
        this.fx = fx;
        this.fy = fy;
        this.u = u;
        this.v = v;
    }

    // TODO: move into a separate file
    CameraInternalParams() {
        this.fx = 1035.990987371761;
        this.fy = 1037.5660299778167;
        this.u = 647.1976126037574;
        this.v = 501.28397224530966;
    }
}
