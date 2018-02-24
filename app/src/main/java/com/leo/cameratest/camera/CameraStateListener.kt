package com.leo.cameratest.camera

interface CameraStateListener {
    fun onSurfaceCreated()
    fun onSuccess()
    fun onError(error: Int)
}
