package com.yarek.hammockvision.objectdetection;

import java.util.List;

public interface ObjectDetector {
    List<Detection> parseOutput(Object output);

}
