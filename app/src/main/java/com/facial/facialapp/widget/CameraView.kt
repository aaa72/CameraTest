package com.facial.facialapp.widget

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.view.View
import android.os.HandlerThread
import com.facial.facialapp.camera.*

class CameraView : SurfaceView {

    companion object {
        private val TAG = "CameraView"
    }

    private val matrix = Matrix()
    private var mPreviewScale = 0f
    private var previewScaleX = 0f
    private var previewScaleY = 0f

    lateinit private var mPreferSize: Size
    lateinit private var mPreviewSize: Size
    private var mPreviewWidth = 0
    private var mPreviewHeight = 0

    lateinit private var mCameraManager: ICameraManager
    private var mCameraStateListener: CameraStateListener? = null
    private var mSurfaceHolder: SurfaceHolder? = null
    private var mHandlerThread: HandlerThread? = null
    private var mHandler: Handler? = null

    private val mScaledMaskRect = Rect()
    var cameraMode
        get() = mCameraManager.cameraMode
        set(value) {
            mCameraManager.cameraMode = value
        }

    private val callback = object : SurfaceHolder.Callback {

        override fun surfaceCreated(holder: SurfaceHolder) {
            Log.d(TAG, "SurfaceHolder Created")
            mSurfaceHolder = holder
            holder.setFixedSize(mPreviewSize.width, mPreviewSize.height)
            mCameraManager.setOutputSurfaceHolder(holder)

            mCameraStateListener?.onSurfaceCreated()
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            Log.d(TAG, "SurfaceHolder Changed. width : $width, height : $height")
            if (width > height) {
                previewScaleX = width / mPreviewWidth.toFloat()
                previewScaleY = height / mPreviewHeight.toFloat()
            } else {
                previewScaleX = width / mPreviewHeight.toFloat()
                previewScaleY = height / mPreviewWidth.toFloat()
            }

            matrix.setScale(previewScaleX, previewScaleY)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            Log.d(TAG, "SurfaceHolder Destroyed")
            mSurfaceHolder = null
            closeCamera()
        }
    }

    constructor(context: Context) : super(context) {
        init(context, null)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        wm.defaultDisplay.getMetrics(displayMetrics)
        val resolution = displayMetrics.widthPixels

        mPreviewScale = getPreviewScale()
        holder.addCallback(callback)

        mPreferSize = Size(resolution, (resolution * mPreviewScale).toInt())

        mCameraManager = Camera1Manager(context)
        mPreviewSize = mCameraManager.setPreferSize(mPreferSize)
        mPreviewWidth = mPreviewSize.width
        mPreviewHeight = mPreviewSize.height
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (mHandlerThread == null || mHandlerThread?.isAlive == false) {
            mHandlerThread = HandlerThread(TAG)
            mHandlerThread?.start()
            mHandler = Handler(mHandlerThread?.looper)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mHandler = null
        mHandlerThread?.quitSafely()
        mHandlerThread = null
    }

    fun zoomIn() {
        mCameraManager.scale = Math.min(1f, mCameraManager.scale + 0.07f)
    }

    fun zoomOut() {
        mCameraManager.scale = Math.max(0f, mCameraManager.scale - 0.07f)
    }

    private fun getPreviewScale(): Float {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        wm.defaultDisplay.getMetrics(displayMetrics)
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()
        var scale = when {
            width > height -> height / width
            else -> width / height
        }
        scale = if (Math.abs(scale - 0.75f) > Math.abs(scale - 0.5625f)) 0.5625f else 0.75f
        Log.d(TAG, "displayMetrics.widthPixels : " + width)
        Log.d(TAG, "displayMetrics.heightPixels : " + height)
        Log.d(TAG, "scale : " + scale)
        return scale
    }

    fun setCameraStateListener(listener: CameraStateListener) {
        mCameraStateListener = listener
    }

    fun setFrameCallback(frameCallback: FrameCallback) {
        mCameraManager.setFrameCallback(frameCallback)
    }

    fun addCallbackBuffer() {
        mCameraManager.addCallbackBuffer()
    }

    fun switchCamera() {
        stopPreview()
        mCameraManager.cameraMode = when (mCameraManager.cameraMode) {
            CameraMode.FRONT -> CameraMode.BACK
            CameraMode.BACK -> CameraMode.FRONT
        }
        startPreview()
    }

    override fun getMatrix(): Matrix {
        return matrix
    }

    fun startPreview() {
        if (mSurfaceHolder != null) {
            openCamera()
        }
    }

    fun stopPreview() {
        closeCamera()
    }

    private fun openCamera() {
        keepScreenOn = true
        mHandler?.post { mCameraManager.openCamera(mCameraStateListener) }
    }

    private fun closeCamera() {
        keepScreenOn = false

        mHandler?.post { mCameraManager.closeCamera() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val originalWidth = View.MeasureSpec.getSize(widthMeasureSpec)
        val originalHeight = View.MeasureSpec.getSize(heightMeasureSpec)
        val scale: Float
        val finalWidth: Int
        val finalHeight: Int
        if (originalWidth < originalHeight) {
            scale = originalWidth * 1.0f / originalHeight
            if (scale == mPreviewScale) {
                finalWidth = originalWidth
                finalHeight = originalHeight
            } else {
                if (mPreviewScale == 0.75f) {
                    finalWidth = originalWidth
                    finalHeight = finalWidth * 4 / 3
                } else {
                    finalWidth = originalWidth
                    finalHeight = finalWidth * 16 / 9
                }
            }
        } else {
            scale = originalHeight * 1.0f / originalWidth
            if (scale == mPreviewScale) {
                finalWidth = originalWidth
                finalHeight = originalHeight
            } else {
                if (mPreviewScale == 0.75f) {
                    finalHeight = originalHeight
                    finalWidth = finalHeight * 4 / 3
                } else {
                    finalWidth = originalWidth
                    finalHeight = finalWidth * 9 / 16
                }
            }
        }
        Log.d(TAG, "originalWidth :$originalWidth, originalHeight :$originalHeight")
        Log.d(TAG, "finalWidth: $finalWidth, finalHeight: $finalHeight")
        setMeasuredDimension(finalWidth, finalHeight)
    }
}