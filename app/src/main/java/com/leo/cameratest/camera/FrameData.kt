package com.facial.facialapp.camera

import android.annotation.SuppressLint
import android.os.Parcelable
import android.util.Log
import com.facial.facialapp.camera.FrameData.Companion.TAG
import com.facial.facialapp.util.ColorFormatUtil
import kotlinx.android.parcel.Parcelize

@SuppressLint("ParcelCreator")
@Parcelize
class FrameData(var data: ByteArray? = null
                     , var format: Int = 0
                     , var width: Int = 0
                     , var height: Int = 0
                     , var stride: Int = 0
                     , var orientation: Int = 0
                     , var mirror: Boolean = false
) : Parcelable {
    companion object {
        val TAG = "FrameData"
    }
}

fun FrameData.rotateToPositive() {
    data = data?.let {
        var bytes = it

        if (orientation != 0) {
            Log.d(TAG, "rotateNV21++")
            bytes = ColorFormatUtil.rotateNV21(bytes, width, height, width, orientation)
            Log.d(TAG, "rotateNV21--")
            when (orientation) {
                90, 270 -> {
                    val tmp = width
                    width = height
                    height = tmp
                }
            }
            orientation = 0
        }

        if (mirror) {
            Log.d(TAG, "mirrorImage++")
            ColorFormatUtil.mirrorImage(bytes, width, height, 0, width * height, 0)
            Log.d(TAG, "mirrorImage--")
        }

        bytes
    }
}