package com.facial.facialapp.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.media.ImageReader.OnImageAvailableListener
import android.support.v4.content.ContextCompat
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import com.facial.facialapp.util.CameraUtil
import com.facial.facialapp.util.ColorFormatUtil

import java.util.ArrayList
import java.util.HashMap

class Camera2Manager(context: Context) {

    companion object {
        private val TAG = "Camera2Manager"

        private val IMAGE_FORMAT = ImageFormat.YUV_420_888
        private val sPreviewSizeCache = HashMap<Size, Size?>()
        private var sFrontCameraConfig: CameraConfig? = null
        private var sBackCameraConfig: CameraConfig? = null

        private class CameraConfig(val id: String) {
            var maxZoom: Float = 0f
            var activeRect: Rect? = null

            override fun toString(): String {
                return "CameraConfig {id = $id, maxZoom = $maxZoom, activeRect = $activeRect}"
            }
        }
    }

    enum class CameraMode {
        BACK, FRONT
    }

    init {
        obtainCameraId(context)
    }

    private val mContext: Context = context.applicationContext
    private val mSurfaceList = ArrayList<Surface>()
    private var mCameraDevice: CameraDevice? = null
    private var mCameraConfig: CameraConfig? = sFrontCameraConfig
    private var mPreviewSize: Size? = null
    private var mPreviewSurfaceHolder: SurfaceHolder? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var requestBuilder: CaptureRequest.Builder? = null
    private var mCameraStateListener: CameraStateListener? = null
    private var mImageReader: ImageReader? = null
    private var mFrameCallback: FrameCallback? = null
    private var rotateDegree: Int = 0
    private var mOpeningCamera: Boolean = false

    var cameraMode: CameraMode = CameraMode.FRONT
        set(value) {
            mCameraConfig = when (value) {
                CameraMode.BACK -> sBackCameraConfig
                CameraMode.FRONT -> sFrontCameraConfig
            }
            field = value
        }

    var scale: Float = 0f // 0 - 1
        set(value) {
            if (field != value) {
                requestBuilder?.set(CaptureRequest.SCALER_CROP_REGION, getScaleRect(field))
                try {
                    mCaptureSession?.setRepeatingRequest(requestBuilder?.build(), null, null)
                } catch (e: Exception) {
                    Log.w(TAG, "" + e, e)
                }
            }
            field = value
        }

    private val mStateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(cameraDevice: CameraDevice) {
            Log.d(TAG, "onOpened")
            mCameraDevice = cameraDevice

            if (!mOpeningCamera) {
                Log.d(TAG, "onOpened() - !mOpeningCamera")
                closeCamera()
                return
            }

            mSurfaceList.clear()
            if (mPreviewSurfaceHolder != null) {
                mSurfaceList.add(mPreviewSurfaceHolder!!.surface)
            }

            // setup image reader
            mImageReader = ImageReader.newInstance(mPreviewSize!!.width, mPreviewSize!!.height, IMAGE_FORMAT, 2/*maxImages*/)
            mImageReader!!.setOnImageAvailableListener(mOnImageAvailableListener, null)
            mSurfaceList.add(mImageReader!!.surface)

            rotateDegree = CameraUtil.getCameraRotateDegree(mContext)
            Log.d(TAG, "camera orientation degree: " + rotateDegree)

            // setup capture
            try {
                Log.d(TAG, "createCaptureSession()++")
                mCameraDevice!!.createCaptureSession(mSurfaceList, mCaptureStateCallback, null)
                Log.d(TAG, "createCaptureSession()--")
            } catch (e: CameraAccessException) {
                Log.w(TAG, "create camera capture session fail by " + e, e)
                handleOpenCameraError(CameraError.OPEN_CAMERA_FAIL)
            }

        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            Log.w(TAG, "onDisconnected")
            handleOpenCameraError(CameraError.CAMERA_DISCONNECTED)
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            Log.w(TAG, "onError " + error)
            handleOpenCameraError(CameraError.OPEN_CAMERA_FAIL)
        }
    }

    private val mOnImageAvailableListener = OnImageAvailableListener { reader ->
//        Log.d(TAG, "onImageAvailable")
        if (mFrameCallback == null) {
            return@OnImageAvailableListener
        }
        val image = reader.acquireNextImage()
        if (image != null && image.format == IMAGE_FORMAT) {

            val imageWidth = image.width
            val imageHeight = image.height

//            Log.d(TAG, "getDataFromImage++")
            var data: ByteArray? = null
            try {
                data = ColorFormatUtil.getDataFromImage(image, ColorFormatUtil.COLOR_FormatNV21)
            } catch (e: Exception) {
                Log.w(TAG, "" + e, e)
            }
//            Log.d(TAG, "getDataFromImage--")

            image.close()

            if (data != null) {
                var rotate = rotateDegree
                var width = imageWidth
                var height = imageHeight

                rotate = when (cameraMode) {
                    CameraMode.FRONT -> when (rotate) {
                        90 -> 270
                        270 -> 90
                        else -> rotate
                    }
                    else -> rotate
                }

                val frameData = FrameData()
                frameData.data = data
                frameData.format = ImageFormat.NV21
                frameData.width = width
                frameData.height = height
                frameData.stride = width
                frameData.orientation = rotate
                frameData.mirror = cameraMode == CameraMode.FRONT
                mFrameCallback?.onFrameCallback(frameData)
            }
        }
    }

    private val mCaptureStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            Log.d(TAG, "onConfigured()")
            if (!mOpeningCamera) {
                Log.d(TAG, "onConfigured() - !mOpeningCamera")
                closeCamera()
                return
            }

            mCaptureSession = session
            try {
                requestBuilder = mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                for (surface in mSurfaceList) {
                    requestBuilder?.addTarget(surface)
                }
                // Auto focus should be continuous for camera preview.
                requestBuilder?.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                requestBuilder?.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)

                // Finally, we start displaying the camera preview.
                Log.d(TAG, "setRepeatingRequest()++")
                mCaptureSession!!.setRepeatingRequest(requestBuilder?.build(), null, null)
                Log.d(TAG, "setRepeatingRequest()--")
                mOpeningCamera = false
                mCameraStateListener?.onSuccess()
            } catch (e: CameraAccessException) {
                Log.w(TAG, "setRepeatingRequest() fail by " + e, e)
                handleOpenCameraError(CameraError.OPEN_CAMERA_FAIL)
            }

        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.w(TAG, "onConfigureFailed")
            handleOpenCameraError(CameraError.OPEN_CAMERA_FAIL)
        }
    }

    private fun obtainCameraId(context: Context) {
        if (sFrontCameraConfig == null) {
            val cameraIds: Array<String>
            try {
                val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                cameraIds = manager.cameraIdList
                for (id in cameraIds) {
                    val characteristics = manager.getCameraCharacteristics(id)
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                    if (facing != null) {
                        var config = CameraConfig(id)
                        if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                            sFrontCameraConfig = config
                            Log.d(TAG, "found front camera id: $id")
                        } else if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                            sBackCameraConfig = config
                            Log.d(TAG, "found back camera id: $id")
                        } else {
                            continue
                        }
                        config.maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
                        config.activeRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                        Log.d(TAG, "found front camera $config")
                    }
                }
                if (sFrontCameraConfig == null)
                    Log.w(TAG, "no front camera id")
                if (sBackCameraConfig == null)
                    Log.w(TAG, "no back camera id")
            } catch (e: CameraAccessException) {
                Log.w(TAG, "fail to get front camera id", e)
            }
        }
    }

    fun setFrameCallback(callback: FrameCallback) {
        mFrameCallback = callback
    }

    fun addCallbackBuffer() {
        // do nothing
    }

    fun setPreferSize(preferSize: Size): Size {
        val previewSize: Size? = sPreviewSizeCache.get(preferSize)
        if (previewSize != null) {
            mPreviewSize = previewSize
            Log.d(TAG, "find preview size from cache " + mPreviewSize)
        } else {
            try {
                val supportOutputSize = (mContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager)
                        .getCameraCharacteristics(sBackCameraConfig?.id)
                        .get<StreamConfigurationMap>(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
                        .getOutputSizes(IMAGE_FORMAT)
                mPreviewSize = CameraUtil.copySize(CameraUtil.getFitPreviewSize(supportOutputSize, preferSize))
                sPreviewSizeCache.put(preferSize, mPreviewSize)
                Log.d(TAG, "find preview size from camera config map " + mPreviewSize!!)
            } catch (e: Exception) {
                Log.w(TAG, "fail to get preview size", e)
                mPreviewSize = CameraUtil.copySize(preferSize)
            }

        }

        return CameraUtil.copySize(mPreviewSize!!)
    }

    fun setOutputSurfaceHolder(surfaceHolder: SurfaceHolder) {
        mPreviewSurfaceHolder = surfaceHolder
    }

    fun openCamera(listener: CameraStateListener?) {
        Log.d(TAG, "openCamera()++")

        // check require permission
        if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "openCamera() fail by no permission")
            listener?.onError(CameraError.NO_CAMERA_PERMISSION)
            return
        }

        // check parameters
        if (mPreviewSize == null) {
            Log.w(TAG, "openCamera() fail by not call setPreferSize()")
            listener?.onError(CameraError.CALL_SET_PREFER_SIZE)
            return
        }

        if (mCameraConfig == null) {
            Log.w(TAG, "openCamera() fail by no front camera")
            listener?.onError(CameraError.NO_FRONT_CAMERA)
            return
        }

        if (mCameraDevice != null) {
            Log.w(TAG, "openCamera() fail by already opened")
            listener?.onError(CameraError.CAMERA_ALREADY_OPENED)
            return
        }

        if (mOpeningCamera) {
            Log.w(TAG, "openCamera() fail by is opening")
            listener?.onError(CameraError.CAMERA_ALREADY_OPENED)
            return
        }

        try {
            mCameraStateListener = listener
            mOpeningCamera = true
            val manager = mContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            Log.d(TAG, "manager.openCamera()++")
            manager.openCamera(mCameraConfig?.id, mStateCallback, null)
            Log.d(TAG, "manager.openCamera()--")
        } catch (e: CameraAccessException) {
            Log.w(TAG, "fail to open camera by " + e, e)
            handleOpenCameraError(CameraError.OPEN_CAMERA_FAIL)
        }

        Log.d(TAG, "openCamera()--")
    }

    fun closeCamera() {
        Log.d(TAG, "closeCamera()++")
        if (mOpeningCamera) {
            Log.d(TAG, "closeCamera() is opening camera")
            mOpeningCamera = false
            return
        }

        if (mCaptureSession != null) {
            Log.d(TAG, "closeCamera() mCaptureSession.close()++")
            mCaptureSession!!.close()
            Log.d(TAG, "closeCamera() mCaptureSession.close()--")
            mCaptureSession = null
        }
        if (mImageReader != null) {
            Log.d(TAG, "closeCamera() mImageReader.close()++")
            mImageReader!!.close()
            Log.d(TAG, "closeCamera() mImageReader.close()--")
            mImageReader = null
        }
        mSurfaceList.clear()

        if (mCameraDevice != null) {
            Log.d(TAG, "closeCamera() mCameraDevice.close()++")
            mCameraDevice!!.close()
            Log.d(TAG, "closeCamera() mCameraDevice.close()--")
            mCameraDevice = null
        }

        mCameraStateListener = null

        Log.d(TAG, "closeCamera()--")
    }

    private fun handleOpenCameraError(error: Int) {
        mOpeningCamera = false
        closeCamera()
        mCameraStateListener?.onError(error)
    }

    private fun getScaleRect(scale: Float): Rect? {
        if (mCameraConfig == null || mCameraConfig!!.activeRect == null) {
            return null
        }
        val config = mCameraConfig!!
        val activeRect = config.activeRect!!

        Log.d(TAG, "getScaleRect scale = $scale, config = $config, activeRect = $activeRect")

        val rect = Rect(activeRect)

        val multiply = (config.maxZoom - (scale * (config.maxZoom - 1f))) / config.maxZoom

        val newWidth = activeRect.width() * multiply
        val newHeight = activeRect.height() * multiply

        rect.left = (rect.centerX() - (newWidth / 2)).toInt()
        rect.right = (rect.left + newWidth).toInt()
        rect.top = (rect.centerY() - (newHeight / 2)).toInt()
        rect.bottom = (rect.top + newHeight).toInt()

        Log.d(TAG, "getScaleRect multiply = $multiply, rect = $rect")

        return rect
    }
}
