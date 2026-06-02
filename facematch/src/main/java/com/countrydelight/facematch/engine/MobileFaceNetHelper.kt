package com.countrydelight.facematch.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt
import androidx.core.graphics.scale

/**
 * Helper class for MobileFaceNet face embedding extraction and comparison.
 * Uses mobilefacenet.tflite model to extract 192-dim face embeddings
 * and computes cosine similarity between two face images.
 */
class MobileFaceNetHelper(context: Context) {

    companion object {
        private const val TAG = "MobileFaceNetHelper"
        private const val MODEL_FILE = "mobilefacenet.tflite"
        private const val INPUT_SIZE = 112 // MobileFaceNet expects 112x112
        private const val EMBEDDING_SIZE = 192 // MobileFaceNet outputs 192-dim embedding
        private const val PIXEL_SIZE = 3 // RGB channels

        // Normalization: MobileFaceNet uses [-1, 1] range
        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 127.5f

        fun embeddingToBase64(embedding: FloatArray): String {
            val byteBuffer = ByteBuffer.allocate(embedding.size * 4)
                .order(ByteOrder.LITTLE_ENDIAN)
            embedding.forEach { byteBuffer.putFloat(it) }
            return Base64.encodeToString(byteBuffer.array(), Base64.NO_WRAP)
        }

        fun base64ToEmbedding(base64: String): FloatArray {
            val byteArray = Base64.decode(base64, Base64.NO_WRAP)
            val byteBuffer = ByteBuffer.wrap(byteArray)
                .order(ByteOrder.LITTLE_ENDIAN)
            val floatArray = FloatArray(byteArray.size / 4)
            byteBuffer.asFloatBuffer().get(floatArray)
            return floatArray
        }
    }

    private var interpreter: Interpreter? = null

    init {
        try {
            val model = loadModelFile(context)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(model, options)
            Log.d(TAG, "MobileFaceNet model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load MobileFaceNet model", e)
        }
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Extract face embedding from a bitmap.
     * The bitmap should be a cropped face image.
     * Returns a 192-dimensional float array embedding, or null on failure.
     */
    @Synchronized
    fun getEmbedding(faceBitmap: Bitmap): FloatArray? {
        val interpreter = this.interpreter ?: return null

        return try {
            // Resize to 112x112
            val resizedBitmap = faceBitmap.scale(INPUT_SIZE, INPUT_SIZE)

            // Convert to ByteBuffer
            val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * PIXEL_SIZE * 4)
            inputBuffer.order(ByteOrder.nativeOrder())
            inputBuffer.rewind()

            val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
            resizedBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

            for (pixel in pixels) {
                val r = ((pixel shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD
                val g = ((pixel shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD
                val b = ((pixel and 0xFF) - IMAGE_MEAN) / IMAGE_STD
                inputBuffer.putFloat(r)
                inputBuffer.putFloat(g)
                inputBuffer.putFloat(b)
            }

            if (resizedBitmap != faceBitmap) {
                resizedBitmap.recycle()
            }

            // Run inference
            val outputArray = Array(1) { FloatArray(EMBEDDING_SIZE) }
            interpreter.run(inputBuffer, outputArray)

            // L2 normalize the embedding
            val embedding = outputArray[0]
            l2Normalize(embedding)

            embedding
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract embedding", e)
            null
        }
    }

    /**
     * Compare a live face bitmap against a pre-computed reference embedding.
     * Avoids extracting the reference embedding every frame.
     */
    fun compareWithEmbedding(faceBitmap: Bitmap, referenceEmbedding: FloatArray): Float? {
        val liveEmbedding = getEmbedding(faceBitmap) ?: return null
        return cosineSimilarityPercent(liveEmbedding, referenceEmbedding)
    }

    /**
     * Compute cosine similarity between two embeddings and return as percentage (0-100).
     */
    private fun cosineSimilarityPercent(embedding1: FloatArray, embedding2: FloatArray): Float {
        val similarity = cosineSimilarity(embedding1, embedding2)
        // For L2-normalized face embeddings, cosine similarity is always [0, 1]
        // Map directly to percentage — no offset needed
        return (similarity * 100f).coerceIn(0f, 100f)
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) {
            Log.e(TAG, "Embedding size mismatch: ${a.size} vs ${b.size}")
            return 0f
        }
        var dotProduct = 0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
        }
        return dotProduct
    }

    private fun l2Normalize(embedding: FloatArray) {
        var sum = 0f
        for (value in embedding) {
            sum += value * value
        }
        val norm = sqrt(sum)
        if (norm > 0f) {
            for (i in embedding.indices) {
                embedding[i] /= norm
            }
        }
    }

    @Synchronized
    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
