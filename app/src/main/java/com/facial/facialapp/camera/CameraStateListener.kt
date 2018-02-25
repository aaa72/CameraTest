package com.facial.facialapp.camera

interface CameraStateListener {
    fun onSurfaceCreated()
    fun onSuccess()
    fun onError(error: Int)
}
