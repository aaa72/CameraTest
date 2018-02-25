package com.facial.facialapp.camera

import android.util.Size
import android.view.SurfaceHolder

interface ICameraManager {
    var cameraMode: CameraMode
    var scale: Float
    fun setOutputSurfaceHolder(surfaceHolder: SurfaceHolder)
    fun setPreferSize(size: Size): Size
    fun setFrameCallback(callback: FrameCallback)
    fun addCallbackBuffer()
    fun openCamera(listener: CameraStateListener?)
    fun closeCamera()
}

enum class CameraMode {
    BACK, FRONT
}
