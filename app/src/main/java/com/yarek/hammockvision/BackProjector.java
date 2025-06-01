package com.yarek.hammockvision;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;

import java.util.List;

/**
 * Class accomplishes a backprojection from 2D to 3D using camera parametrs
 */
public class BackProjector {

    private Python python;
    private PyObject backProjectionModule;

    private PyObject pyCamera;
    private PyObject photo;
    private PyObject photoPoint;
    private PyObject pyBackProjector;


    public BackProjector(Python python,
                         CameraInternalParams cameraInternalParams,
                         CameraPosition cameraPosition) {

        this.python = python;
        this.backProjectionModule = python.getModule("back_projection");

        // Instantiating the camera
        PyObject cameraPyClass = backProjectionModule.get("Camera");
        pyCamera = cameraPyClass.call(
                (double) cameraInternalParams.fx,
                (double) cameraInternalParams.fy,
                (double) cameraInternalParams.skew);

        // Setting the position
        pyCamera.callAttr("set_position",
                python.getModule("numpy").get("array").call(new double[]{0f, cameraPosition.y, 0f}));

        // Setting the orientation
        PyObject scipySpatialTransform = python.getModule("scipy.spatial.transform");
        PyObject rotationClass = scipySpatialTransform.get("Rotation");

        PyObject eulerArray = python.getModule("numpy").get("array").call(cameraPosition.eulerRotationAngles);
        PyObject rotationObj = rotationClass.callAttr("from_euler", "xyz", eulerArray, true);
        PyObject rotationMatrix = rotationObj.callAttr("as_matrix");

        pyCamera.callAttr("set_orientation",
                rotationMatrix);

        // Setting up the photo
        int[] resolution = cameraInternalParams.getResolution();

        photo = backProjectionModule.get("Photo").call(
                resolution[0], resolution[1],
                cameraInternalParams.u, cameraInternalParams.v);

        photoPoint = backProjectionModule.get("PhotoPoint").call(0, 0, photo);

        pyBackProjector = backProjectionModule.get("BackProjector").call(pyCamera);
    }



    float[] backProject(float[] point2D) {

        photoPoint.callAttr("set_point_pixels", point2D[0], point2D[1]);

        PyObject result = pyBackProjector.callAttr("back_project", photoPoint);

        PyObject pyList = result.callAttr("tolist");

        List<PyObject> list = pyList.asList();

        float[] point3D = new float[3];
        for (int i = 0; i < 3; i++) {
            point3D[i] = list.get(i).toFloat();
        }

        return point3D;
    }

}
