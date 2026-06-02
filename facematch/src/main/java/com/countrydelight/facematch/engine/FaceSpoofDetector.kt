package com.countrydelight.facematch.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import androidx.core.graphics.scale

/**
 * Face anti-spoofing detector using MiniFASNet dual-scale approach.
 * Detects printed photos, screen replays, and mask attacks.
 *
 * Input: 80x80 BGR float32. Output: 3-class [unknown, real, fake].
 */
class FaceSpoofDetector(context: Context) {

    companion object {
        private const val TAG = "FaceSpoofDetector"
        private const val MODEL_FILE = "2.7_80x80_MiniFASNetV2.tflite"
        private const val INPUT_SIZE = 80
        private const val CROP_SCALE = 2.7f

        // Require strong "real" confidence and that real beats fake.
        // Uncertain frames (e.g. real=0.36, fake=0.34) are treated as spoof.
        private const val REAL_THRESHOLD = 0.50f
    }

    private var interpreter: Interpreter? = null

    init {
        try {
            val options = Interpreter.Options().apply { setNumThreads(2) }
            interpreter = Interpreter(loadModelFile(context, MODEL_FILE), options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load spoof detection model", e)
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    /**
     * Detect if a face is real or spoofed.
     * @param fullBitmap the full camera frame (already rotated to match ML Kit coordinates)
     * @param faceBoundingBox ML Kit face bounding box
     * @return Pair(isReal, confidence). confidence is 0-1 where higher = more likely real.
     */
    @Synchronized
    fun detectSpoof(fullBitmap: Bitmap, faceBoundingBox: Rect): Pair<Boolean, Float> {
        val interp = interpreter ?: return Pair(true, 1f) // fail open if model not loaded

        return try {
            val crop = cropFaceAtScale(fullBitmap, faceBoundingBox, CROP_SCALE)
            val output = runModel(interp, crop)
            crop.recycle()

            val scores = softmax(output)

            // label 0 = unknown, 1 = real, 2 = fake
            val realScore = scores[1]
            val fakeScore = scores[2]
            val isReal = realScore >= REAL_THRESHOLD && realScore > fakeScore

            Pair(isReal, realScore)
        } catch (e: Exception) {
            Log.e(TAG, "Spoof detection failed", e)
            Pair(true, 1f) // fail open
        }
    }

    /**
     * Crop face region at a given scale factor relative to the bounding box.
     * Larger scale = more context around the face.
     */
    private fun cropFaceAtScale(bitmap: Bitmap, faceBox: Rect, scale: Float): Bitmap {
        val centerX = faceBox.centerX()
        val centerY = faceBox.centerY()
        val halfSize = (max(faceBox.width(), faceBox.height()) * scale / 2f).toInt()

        val cropRect = Rect(
            max(0, centerX - halfSize),
            max(0, centerY - halfSize),
            min(bitmap.width, centerX + halfSize),
            min(bitmap.height, centerY + halfSize)
        )

        val cropped = Bitmap.createBitmap(
            bitmap,
            cropRect.left,
            cropRect.top,
            cropRect.width().coerceAtLeast(1),
            cropRect.height().coerceAtLeast(1)
        )

        return cropped.scale(INPUT_SIZE, INPUT_SIZE)
    }

    /**
     * Run a single model on a cropped face bitmap.
     * Feeds pixels in RGB order as raw float32 (0-255, no normalization).
     */
    private fun runModel(interpreter: Interpreter, bitmap: Bitmap): FloatArray {
        val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())
        inputBuffer.rewind()

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            // RGB order, cast to float32 (0-255 range, no normalization)
            val r = ((pixel shr 16) and 0xFF).toFloat()
            val g = ((pixel shr 8) and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()
            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        val output = Array(1) { FloatArray(3) }
        interpreter.run(inputBuffer, output)
        return output[0]
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.max()
        val exps = logits.map { exp((it - maxLogit).toDouble()).toFloat() }
        val sum = exps.sum()
        return exps.map { it / sum }.toFloatArray()
    }

    @Synchronized
    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
