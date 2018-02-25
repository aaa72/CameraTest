package com.facial.facialapp.util

import android.graphics.*
import android.media.Image
import android.util.Log

import java.io.ByteArrayOutputStream
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

object ColorFormatUtil {
    private val TAG = "ColorFormatUtil"
    private val DEBUG_CONVERT = false

    val COLOR_FormatI420 = 1
    val COLOR_FormatNV21 = 2

    fun getDataFromImage(image: Image, colorFormat: Int): ByteArray {
        if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
            throw IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21")
        }
        when (image.format) {
            ImageFormat.YUV_420_888, ImageFormat.NV21, ImageFormat.YV12 -> {
            }
            else -> throw RuntimeException("can't convert Image to byte array, format " + image.format)
        }
        val crop = image.cropRect
        val format = image.format
        val width = crop.width()
        val height = crop.height()
        val planes = image.planes
        val data = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
        val rowData = ByteArray(planes[0].rowStride)
        var channelOffset = 0
        var outputStride = 1
        if (DEBUG_CONVERT) {
            Log.d(TAG, "image size " + image.width + "x" + image.height)
            Log.d(TAG, "crop rect " + crop)
        }
        for (i in planes.indices) {
            when (i) {
                0 -> {
                    channelOffset = 0
                    outputStride = 1
                }
                1 -> if (colorFormat == COLOR_FormatI420) {
                    channelOffset = width * height
                    outputStride = 1
                } else if (colorFormat == COLOR_FormatNV21) {
                    channelOffset = width * height + 1
                    outputStride = 2
                }
                2 -> if (colorFormat == COLOR_FormatI420) {
                    channelOffset = (width.toDouble() * height.toDouble() * 1.25).toInt()
                    outputStride = 1
                } else if (colorFormat == COLOR_FormatNV21) {
                    channelOffset = width * height
                    outputStride = 2
                }
            }
            val buffer = planes[i].buffer
            val rowStride = planes[i].rowStride
            val pixelStride = planes[i].pixelStride
            if (DEBUG_CONVERT) {
                Log.d(TAG, "pixelStride " + pixelStride)
                Log.d(TAG, "rowStride " + rowStride)
                Log.d(TAG, "width " + width)
                Log.d(TAG, "height " + height)
                Log.d(TAG, "buffer size " + buffer.remaining())
            }
            val shift = if (i == 0) 0 else 1
            val w = width shr shift
            val h = height shr shift
            buffer.position(rowStride * (crop.top shr shift) + pixelStride * (crop.left shr shift))
            for (row in 0 until h) {
                val length: Int
                if (pixelStride == 1 && outputStride == 1) {
                    length = w
                    buffer.get(data, channelOffset, length)
                    channelOffset += length
                } else {
                    length = (w - 1) * pixelStride + 1
                    buffer.get(rowData, 0, length)
                    for (col in 0 until w) {
                        data[channelOffset] = rowData[col * pixelStride]
                        channelOffset += outputStride
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length)
                }
            }
        }
        return data
    }

    fun NV21toBitmap(nv21: ByteArray, width: Int, height: Int): Bitmap {
        val out = ByteArrayOutputStream()
        val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        yuv.compressToJpeg(Rect(0, 0, width, height), 100, out)
        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    }

    fun BitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        return stream.toByteArray()
    }

    fun saveBitmap(bmp: Bitmap, file: File) {
        var out: FileOutputStream? = null
        try {
            file.parentFile.mkdirs()
            out = FileOutputStream(file)
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, out)
        } catch (e: Exception) {
            Log.w(TAG, "" + e, e)
        } finally {
            out?.close()
        }
    }

    fun RGBtoNV21(yuv420sp: ByteArray, argb: ByteArray, width: Int, height: Int) {
        val frameSize = width * height

        var yIndex = 0
        var uvIndex = frameSize

        var A: Int
        var R: Int
        var G: Int
        var B: Int
        var Y: Int
        var U: Int
        var V: Int
        var index = 0
        var rgbIndex = 0

        for (i in 0 until height) {
            for (j in 0 until width) {

                R = argb[rgbIndex++].toInt()
                G = argb[rgbIndex++].toInt()
                B = argb[rgbIndex++].toInt()
                A = argb[rgbIndex++].toInt() // Ignored right now.

                // RGB to YUV conversion according to
                // https://en.wikipedia.org/wiki/YUV#Y.E2.80.B2UV444_to_RGB888_conversion
                Y = (66 * R + 129 * G + 25 * B + 128 shr 8) + 16
                U = (-38 * R - 74 * G + 112 * B + 128 shr 8) + 128
                V = (112 * R - 94 * G - 18 * B + 128 shr 8) + 128

                // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor
                // of 2 meaning for every 4 Y pixels there are 1 V and 1 U.
                // Note the sampling is every other pixel AND every other scanline.
                yuv420sp[yIndex++] = (if (Y < 0) 0 else if (Y > 255) 255 else Y).toByte()
                if (i % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (if (V < 0) 0 else if (V > 255) 255 else V).toByte()
                    yuv420sp[uvIndex++] = (if (U < 0) 0 else if (U > 255) 255 else U).toByte()
                }
                index++
            }
        }
    }

    fun mirrorImage(buffer: ByteArray?, stride: Int, height: Int, padding: Int, Y_len: Int, orientation: Int) {
        var tmp: Byte
        var i: Int
        var j: Int
        var width: Int

        var nBuffer_Index = 0
        var nCbCr_buffer_index = 0
        do {
            if (buffer == null || stride <= 0 || height <= 0 || Y_len <= 0) {
                Log.w(TAG, "mirrorImage parameter invalid. Ignore.")
                break
            }
            width = stride - padding

            if (orientation == 0 || orientation == 180)
            // orientation 0 & 180
            {
                i = 0
                while (i < height) {
                    j = 0
                    while (j < width shr 1) {
                        tmp = buffer[nBuffer_Index + width - j - 1]
                        buffer[nBuffer_Index + width - j - 1] = buffer[nBuffer_Index + j]
                        buffer[nBuffer_Index + j] = tmp
                        j++
                    }
                    nBuffer_Index += stride
                    i++
                }
                nCbCr_buffer_index += Y_len


                i = 0
                while (i < height shr 1) {
                    j = 0
                    while (j < width shr 1) {
                        tmp = buffer[nCbCr_buffer_index + width - j - 2]
                        buffer[nCbCr_buffer_index + width - j - 2] = buffer[nCbCr_buffer_index + j]
                        buffer[nCbCr_buffer_index + j] = tmp
                        tmp = buffer[nCbCr_buffer_index + width - j - 1]
                        buffer[nCbCr_buffer_index + width - j - 1] = buffer[nCbCr_buffer_index + j + 1]
                        buffer[nCbCr_buffer_index + j + 1] = tmp
                        j += 2
                    }
                    nCbCr_buffer_index += stride
                    i++
                }
            } else
            // orientation 90 & 270
            {
                var SwapIndex = (height - 1) * stride

                i = 0
                while (i < height / 2) {
                    j = 0
                    while (j < width) {
                        tmp = buffer[j + i * stride]
                        buffer[j + i * stride] = buffer[SwapIndex + j]
                        buffer[SwapIndex + j] = tmp
                        j++
                    }
                    SwapIndex -= stride
                    i++
                }
                nCbCr_buffer_index += Y_len

                SwapIndex = (height / 2 - 1) * stride
                i = 0
                while (i < height / 4) {
                    j = 0
                    while (j < width) {
                        tmp = buffer[nCbCr_buffer_index + j + i * stride]
                        buffer[nCbCr_buffer_index + j + i * stride] = buffer[nCbCr_buffer_index + SwapIndex + j]
                        buffer[nCbCr_buffer_index + SwapIndex + j] = tmp
                        j++
                    }
                    SwapIndex -= stride
                    i++
                }
            }
        } while (false)

    }

    fun rotateNV21(data: ByteArray, imageWidth: Int, imageHeight: Int, rowStride: Int, nRotation: Int): ByteArray {
        Log.d(TAG, "rotateNV21() nRotation:" + nRotation)
        var retBuf: ByteArray

        try {
            when (nRotation) {
                90 -> retBuf = rotateYUV420Degree90(data, imageWidth, imageHeight, rowStride)

                180 -> retBuf = rotateYUV420Degree180(data, imageWidth, imageHeight, rowStride)

                270 -> retBuf = rotateYUV420Degree270(data, imageWidth, imageHeight, rowStride)

                0 -> retBuf = data
                else -> retBuf = data
            }
        } catch (ex: Exception) {
            Log.w(TAG, "rotateNV21() exception:" + ex)
            retBuf = data
        }

        return retBuf
    }

    private fun rotateYUV420Degree90(data: ByteArray, imageWidth: Int, imageHeight: Int, rowStride: Int): ByteArray {
        val yuv = ByteArray(imageWidth * imageHeight * 3 / 2)
        // Rotate the Y luma
        var i = 0
        for (x in 0 until imageWidth) {
            for (y in imageHeight - 1 downTo 0) {
                yuv[i] = data[y * rowStride + x]
                i++
            }
        }

        val wh = rowStride * imageHeight
        // Rotate the U and V color components
        i = imageWidth * imageHeight * 3 / 2 - 1
        var x = imageWidth - 1
        while (x > 0) {
            for (y in 0 until imageHeight / 2) {
                yuv[i] = data[wh + y * rowStride + x]
                i--
                yuv[i] = data[wh + y * rowStride + (x - 1)]
                i--
            }
            x = x - 2
        }
        return yuv
    }

    private fun rotateYUV420Degree180(data: ByteArray, imageWidth: Int, imageHeight: Int, rowStride: Int): ByteArray {
        val yuv = ByteArray(rowStride * imageHeight * 3 / 2)

        for (y in 0 until imageHeight) {
            for (x in 0 until imageWidth) {
                yuv[y * rowStride + x] = data[(imageHeight - 1 - y) * rowStride + (imageWidth - 1 - x)]
            }
        }

        val wh = rowStride * imageHeight
        for (y in 0 until imageHeight / 2) {
            var x = 0
            while (x < imageWidth) {
                yuv[wh + y * rowStride + x] = data[wh + (imageHeight / 2 - 1 - y) * rowStride + (imageWidth - 1 - x) - 1]
                yuv[wh + y * rowStride + x + 1] = data[wh + (imageHeight / 2 - 1 - y) * rowStride + (imageWidth - 1 - x)]
                x += 2
            }
        }

        return yuv
    }

    private fun rotateYUV420Degree270(data: ByteArray, imageWidth: Int, imageHeight: Int, rowStride: Int): ByteArray {
        val yuv = ByteArray(imageWidth * imageHeight * 3 / 2)
        // Rotate the Y luma
        var i = 0
        for (x in imageWidth - 1 downTo 0) {
            for (y in 0 until imageHeight) {
                yuv[i] = data[y * rowStride + x]
                i++
            }
        }

        // Rotate the U and V color components
        val wh = rowStride * imageHeight
        i = imageWidth * imageHeight
        var x = imageWidth - 1
        while (x > 0) {
            for (y in 0 until imageHeight / 2) {
                yuv[i] = data[wh + y * rowStride + (x - 1)]
                i++
                yuv[i] = data[wh + y * rowStride + x]
                i++
            }
            x = x - 2
        }
        return yuv
    }

    fun rotateBitmap(sourceBitmap: Bitmap, degree: Int, frontCamera: Boolean): Bitmap {
        if (degree == 0 && !frontCamera) {
            return sourceBitmap
        }
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        if (frontCamera) {
            matrix.postScale(-1f, 1f)
        }
        return Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, matrix, false)
    }
}
