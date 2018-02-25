package com.facial.facialapp.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.Camera
import android.hardware.Camera.Parameters
import android.hardware.Camera.PreviewCallback
import android.support.v4.content.ContextCompat
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.view.SurfaceView

import com.facial.facialapp.util.CameraUtil

class Camera1Manager(context: Context) : ICameraManager {
    private val mContext: Context
    private var mCamera: Camera? = null
    private var mPreviewSize: Size? = null
    private var mPreviewSurfaceHolder: SurfaceHolder? = null
    private var mCameraStateListener: CameraStateListener? = null
    private var mFrameCallback: FrameCallback? = null
    private val mFrameData = FrameData()
    private var mPreviewBuffer: ByteArray? = null
    private var mDegrees: Int = 0
    private var mCameraIndex = -1
    private var camera: Camera? = null
    private val mPreviewCallback = PreviewCallback { data, camera ->
        if (mFrameCallback == null) {
            return@PreviewCallback
        }
        mFrameData.data = data
        mFrameData.format = ImageFormat.NV21
        mFrameData.stride = mPreviewSize!!.width
        mFrameData.width = mFrameData.stride
        mFrameData.height = mPreviewSize!!.height
        mFrameData.orientation = mDegrees
        mFrameCallback!!.onFrameCallback(mFrameData)
    }

    override var cameraMode: CameraMode = CameraMode.FRONT
    override var scale: Float = 0f

    init {
        mContext = context.applicationContext
        obtainCameraIndex()
    }

    private fun obtainCameraIndex() {
        try {
            val info = Camera.CameraInfo()
            for (i in 0 until Camera.getNumberOfCameras()) {
                Camera.getCameraInfo(i, info)
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    mCameraIndex = i
                    break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "fail to get front camera", e)
        }
        Log.d(TAG, "camera id = $mCameraIndex")
    }

    override fun setOutputSurfaceHolder(surfaceHolder: SurfaceHolder) {
        mPreviewSurfaceHolder = surfaceHolder
    }

    override fun setPreferSize(size: Size): Size {
        val camera = camera
        if (camera != null) {
            val parameters = camera.parameters
            mPreviewSize = CameraUtil.copySize(CameraUtil.getFitPreviewSize(parameters.supportedPreviewSizes, size))
        } else {
            mPreviewSize = CameraUtil.copySize(size)
        }
        return CameraUtil.copySize(mPreviewSize!!)
    }

    override fun setFrameCallback(callback: FrameCallback) {
        mFrameCallback = callback
    }

    override fun addCallbackBuffer() {
        if (mCamera != null && mPreviewBuffer != null) {
            mCamera!!.addCallbackBuffer(mPreviewBuffer)
        }
    }

    override fun openCamera(listener: CameraStateListener?) {
        Log.d(TAG, "openCamera()")
        if (mCamera != null) {
            listener?.onError(CameraError.CAMERA_ALREADY_OPENED)
            return
        }

        mCameraStateListener = listener // never null

        // check require permission
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            mCameraStateListener!!.onError(CameraError.NO_CAMERA_PERMISSION)
            return
        }

        mCamera = Camera.open(mCameraIndex)
        if (mCamera == null) {
            mCameraStateListener!!.onError(CameraError.NO_FRONT_CAMERA)
            return
        }

        // check parameters
        if (mPreviewSize == null) {
            mCameraStateListener!!.onError(CameraError.CALL_SET_PREFER_SIZE)
            return
        }

            try { // set camera parameters
            val parameters = mCamera!!.parameters
            val supportedFlashModes = parameters.supportedFlashModes
            if (supportedFlashModes != null && supportedFlashModes.contains(Parameters.FLASH_MODE_OFF)) {
                parameters.flashMode = Parameters.FLASH_MODE_OFF
            }
            val supportedFocusModes = parameters.supportedFocusModes
            if (supportedFocusModes != null && supportedFocusModes.contains(Parameters.FOCUS_MODE_AUTO)) {
                parameters.focusMode = Parameters.FOCUS_MODE_AUTO
            }
            parameters.setPreviewSize(mPreviewSize!!.width, mPreviewSize!!.height)
            parameters.previewFormat = ImageFormat.NV21
            parameters.pictureFormat = ImageFormat.JPEG
            parameters.exposureCompensation = 0
            mCamera!!.parameters = parameters

            // set surface holder
            val holder: SurfaceHolder
            if (mPreviewSurfaceHolder != null) {
                holder = mPreviewSurfaceHolder as SurfaceHolder
            } else {
                val view = SurfaceView(mContext)
                holder = view.holder
            }
            mCamera!!.setPreviewDisplay(holder)
        } catch (e: Exception) {
            Log.w(TAG, "openCamera() fail by " + e, e)
            mCameraStateListener!!.onError(CameraError.OPEN_CAMERA_FAIL)
            return
        }

        // set camera orientation
        mDegrees = CameraUtil.getCameraRotateDegree(mContext)
        Log.d(TAG, "camera orientation degree: " + mDegrees)
        mCamera!!.setDisplayOrientation(mDegrees)

        // set preview callback
        mCamera!!.setPreviewCallback(mPreviewCallback)

        // set preview buffer
        mPreviewBuffer = ByteArray(mPreviewSize!!.width * mPreviewSize!!.height * 3 / 2)
        Log.d(TAG, "init preview buffer size: " + mPreviewBuffer!!.size)
        mCamera!!.addCallbackBuffer(mPreviewBuffer)
        mCamera!!.setPreviewCallbackWithBuffer(mPreviewCallback)

        // start preview
        mCamera!!.startPreview()
        mCameraStateListener!!.onSuccess()
    }

    override fun closeCamera() {
        Log.d(TAG, "closeCamera()")
        if (mCamera != null) {
            mCamera!!.setPreviewCallbackWithBuffer(null)
            mCamera!!.stopPreview()
            mCamera!!.release()
            mCamera = null
        }
    }

    companion object {
        private val TAG = "Camera1Manager"
        private val IMAGE_FORMAT = ImageFormat.NV21
    }
}
