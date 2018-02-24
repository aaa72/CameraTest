package com.leo.cameratest.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.facial.facialapp.util.CameraUtil;

import java.util.List;

public class FaceUnlockCamera1 implements IFaceUnlockCamera {
    private static final String TAG = "FaceUnlockCamera1";
    private static final int IMAGE_FORMAT = ImageFormat.NV21;

    private final Context mContext;
    private Camera mCamera;
    private Size mPreviewSize;
    private SurfaceHolder mPreviewSurfaceHolder;
    private CameraStateListener mCameraStateListener = null;
    private FrameCallback mFrameCallback;
    private final FrameData mFrameData = new FrameData();
    private byte[] mPreviewBuffer;
    private int mDegrees;
    private int mCameraIndex = -1;

    public FaceUnlockCamera1(Context context) {
        mContext = context.getApplicationContext();
        obtainCameraIndex();
    }

    private void obtainCameraIndex() {
        try {
            Camera.CameraInfo info = new Camera.CameraInfo();
            for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    mCameraIndex = i;
                    break;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "fail to get front camera", e);
        }
    }

    @Override
    public int getImageFormat() {
        return IMAGE_FORMAT;
    }

    @Override
    public boolean isFrontCamera() {
        return true;
    }

    @Override
    public int getRotateDegree() {
        return mDegrees;
    }

    @Override
    public void setOutputSurfaceHolder(SurfaceHolder surfaceHolder) {
        mPreviewSurfaceHolder = surfaceHolder;
    }

    @Override
    public Size setPreferSize(Size size) {
        Camera camera = getCamera();
        if (camera != null) {
            Parameters parameters = camera.getParameters();
            mPreviewSize = CameraUtil.copySize(CameraUtil.getFitPreviewSize(parameters.getSupportedPreviewSizes(), size));
        } else {
            mPreviewSize = CameraUtil.copySize(size);
        }
        return CameraUtil.copySize(mPreviewSize);
    }

    @Override
    public void setFrameCallback(FrameCallback callback) {
        mFrameCallback = callback;
    }

    @Override
    public void addCallbackBuffer() {
        if (mCamera != null && mPreviewBuffer != null) {
            mCamera.addCallbackBuffer(mPreviewBuffer);
        }
    }

    @Override
    public void openCamera(CameraStateListener listener) {
        Log.d(TAG, "openCamera()");
        if (mCamera != null) {
            if (listener != null) {
                listener.onError(CameraError.CAMERA_ALREADY_OPENED);
            }
            return;
        }

        mCameraStateListener = listener != null ? listener : null; // never null

        // check require permission
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            mCameraStateListener.onError(CameraError.NO_CAMERA_PERMISSION);
            return;
        }

        mCamera = getCamera();
        if (mCamera == null) {
            mCameraStateListener.onError(CameraError.NO_FRONT_CAMERA);
            return;
        }

        // check parameters
        if (mPreviewSize == null) {
            mCameraStateListener.onError(CameraError.CALL_SET_PREFER_SIZE);
            return;
        }

        try { // set camera parameters
            Parameters parameters = mCamera.getParameters();
            List<String> supportedFlashModes = parameters.getSupportedFlashModes();
            if (supportedFlashModes != null && supportedFlashModes.contains(Parameters.FLASH_MODE_OFF)) {
                parameters.setFlashMode(Parameters.FLASH_MODE_OFF);
            }
            List<String> supportedFocusModes = parameters.getSupportedFocusModes();
            if (supportedFocusModes != null && supportedFocusModes.contains(Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Parameters.FOCUS_MODE_AUTO);
            }
            parameters.setPreviewSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            parameters.setPreviewFormat(ImageFormat.NV21);
            parameters.setPictureFormat(ImageFormat.JPEG);
            parameters.setExposureCompensation(0);
            mCamera.setParameters(parameters);

            // set surface holder
            SurfaceHolder holder;
            if (mPreviewSurfaceHolder != null) {
                holder = mPreviewSurfaceHolder;
            } else {
                SurfaceView view = new SurfaceView(mContext);
                holder = view.getHolder();
            }
            mCamera.setPreviewDisplay(holder);
        } catch (Exception e) {
            Log.w(TAG , "openCamera() fail by " + e, e);
            mCameraStateListener.onError(CameraError.OPEN_CAMERA_FAIL);
            return;
        }

        // set camera orientation
        mDegrees = CameraUtil.getCameraRotateDegree(mContext);
        Log.d(TAG, "camera orientation degree: " + mDegrees);
        mCamera.setDisplayOrientation(mDegrees);

        // set preview callback
        mCamera.setPreviewCallback(mPreviewCallback);

        // set preview buffer
        mPreviewBuffer = new byte[mPreviewSize.getWidth() * mPreviewSize.getHeight() * 3 / 2];
        Log.d(TAG, "init preview buffer size: " + mPreviewBuffer.length);
        mCamera.addCallbackBuffer(mPreviewBuffer);
        mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);

        // start preview
        mCamera.startPreview();
        mCameraStateListener.onSuccess();
    }

    @Override
    public void closeCamera() {
        Log.d(TAG, "closeCamera()");
        if (mCamera != null) {
            mCamera.setPreviewCallbackWithBuffer(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    private Camera getCamera() {
        return mCameraIndex != -1 ? Camera.open(mCameraIndex) : null;
    }

    private final PreviewCallback mPreviewCallback = new PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            if (mFrameCallback == null) {
                return;
            }
            mFrameData.data = data;
            mFrameData.format = ImageFormat.NV21;
            mFrameData.width = mFrameData.stride = mPreviewSize.getWidth();
            mFrameData.height = mPreviewSize.getHeight();
            mFrameData.orientation = mDegrees;
            mFrameCallback.onFrameCallback(mFrameData);
        }
    };
}
