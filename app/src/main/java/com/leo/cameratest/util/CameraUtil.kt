package com.facial.facialapp.util

import android.content.Context
import android.hardware.Camera
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.WindowManager

import java.util.ArrayList
import java.util.Collections
import java.util.Comparator

object CameraUtil {
    private val TAG = "CameraUtil"

    fun getCameraRotateDegree(context: Context): Int {
        val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        val degree: Int
        when (rotation) {
            Surface.ROTATION_0 // portrait
            -> degree = 90
            Surface.ROTATION_90 // landscape
            -> degree = 0
            Surface.ROTATION_180 // portrait-reverse
            -> degree = 270
            Surface.ROTATION_270 // landscape-reverse
            -> degree = 180
            else -> degree = 90
        }
        return degree
    }

    private fun isScale(size: Size, targetScale: Float): Boolean {
        if (size.width * targetScale == size.height.toFloat())
            return true
        val tolerant = 0.01f // %1
        val scale = size.height.toFloat() / size.width
        return scale > targetScale - tolerant && scale < targetScale + tolerant
    }

    private fun getFitPreviewSize(supportSizes: Array<Size>?, preferSize: Size, preferScale: Float): Size {
        var preferScale = preferScale
        if (supportSizes == null) {
            return preferSize
        }
        if (preferScale > 0)
            preferScale = Math.round(preferScale * 100f) / 100f
        Log.d(TAG, "preferScale = " + preferScale)
        val bigEnough = ArrayList<Size>()
        val notBigEnough = ArrayList<Size>()
        for (supportSize in supportSizes) {
            Log.d(TAG, "supportSize = " + supportSize)
            if (preferScale <= 0 /* don't care scale */ || isScale(supportSize, preferScale)) {
                if (supportSize.width >= preferSize.width && supportSize.height >= preferSize.height) {
                    bigEnough.add(supportSize)
                } else {
                    notBigEnough.add(supportSize)
                }
            }
        }
        class CompareSizesByArea : Comparator<Size> {
            override fun compare(lhs: Size, rhs: Size): Int {
                // We cast here to ensure the multiplications won't overflow
                return java.lang.Long.signum(lhs.width.toLong() * lhs.height - rhs.width.toLong() * rhs.height)
            }
        }
        if (bigEnough.size > 0) {
            return Collections.min(bigEnough, CompareSizesByArea())
        } else if (notBigEnough.size > 0) {
            return Collections.max(notBigEnough, CompareSizesByArea())
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size")
            return getFitPreviewSize(supportSizes, preferSize, 0f)
        }
    }

    fun getFitPreviewSize(supportSizes: Array<Size>, preferSize: Size): Size {
        return getFitPreviewSize(supportSizes, preferSize, preferSize.height.toFloat() / preferSize.width)
    }

//    fun getFitPreviewSize(previewSizes: List<Camera.Size>?, preferSize: Size): Size {
//        if (previewSizes == null) {
//            return preferSize
//        }
//        val sizes = arrayOfNulls<Size>(previewSizes.size)
//        for (i in previewSizes.indices) {
//            val size = previewSizes[i]
//            sizes[i] = Size(size.width, size.height)
//        }
//        return getFitPreviewSize(sizes, preferSize)
//    }

    fun copySize(size: Size): Size {
        return Size(size.width, size.height)
    }
}
