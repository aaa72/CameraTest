package com.leo.cameratest.camera;

import android.util.Size;
import android.view.SurfaceHolder;

public interface IFaceUnlockCamera {
    int getImageFormat();
    boolean isFrontCamera();
    int getRotateDegree();
    void setOutputSurfaceHolder(SurfaceHolder surfaceHolder);
    Size setPreferSize(Size size);
    void setFrameCallback(FrameCallback callback);
    void addCallbackBuffer();
    void openCamera(CameraStateListener listener);
    void closeCamera();
}
