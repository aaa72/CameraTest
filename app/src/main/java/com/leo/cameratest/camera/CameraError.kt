package com.leo.cameratest.camera

object CameraError {
    val NO_CAMERA_PERMISSION = 1
    /**
     * call setPreferSize() before openCamera()"
     */
    val CALL_SET_PREFER_SIZE = 2
    /**
     * Can not find front camera
     */
    val NO_FRONT_CAMERA = 3
    val OPEN_CAMERA_TIMEOUT = 4
    val OPEN_CAMERA_FAIL = 5
    val CAMERA_DISCONNECTED = 6
    val CAMERA_CLOSED = 7
    val CAMERA_ALREADY_OPENED = 8
}
