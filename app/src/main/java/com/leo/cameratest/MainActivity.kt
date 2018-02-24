package com.leo.cameratest

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.facial.facialapp.camera.Camera2Manager
import com.facial.facialapp.camera.CameraStateListener
import com.facial.facialapp.camera.FrameCallback
import com.facial.facialapp.camera.FrameData
import kotlinx.android.synthetic.main.activity_main.*

val TAG = "Leo"

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        camera_view.apply {
            cameraMode = Camera2Manager.CameraMode.BACK
            setCameraStateListener(object : CameraStateListener {
                override fun onSurfaceCreated() {
                    Log.d(TAG, "CameraStateListener.onSurfaceCreated")
                    startPreview()
                }

                override fun onSuccess() {
                    Log.d(TAG, "CameraStateListener.onSuccess")
                }

                override fun onError(error: Int) {
                    Log.d(TAG, "CameraStateListener.onError " + error)
                }
            })
            setFrameCallback(object : FrameCallback {
                override fun onFrameCallback(frameData: FrameData) {
                    Log.d(TAG, "onFrameCallback")
                }
            })
        }
    }
}
