package com.countrydelight.facematch.internal

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy

class ImageUtils {

    fun rotateImageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            var bitmap = imageProxy.toBitmap()
            val rotation = imageProxy.imageInfo.rotationDegrees
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                )
                if (rotated != bitmap) bitmap.recycle()
                bitmap = rotated
            }
            bitmap
        } catch (e: Exception) {
            Log.e("Utils", "Failed to rotate ImageProxy to bitmap", e)
            null
        }
    }
}