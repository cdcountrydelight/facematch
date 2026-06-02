package com.countrydelight.facematch.engine

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import kotlin.math.max
import kotlin.math.min

data class LiveMatchResult(
    val matchPercent: Float,
    val shouldTriggerAttendance: Boolean,
    val shouldReject: Boolean
)

/**
 * Orchestrates real-time face matching against a pre-cached profile embedding.
 * Called from LiveMatchAnalyzer on frames where liveness is confirmed.
 */
class LiveFaceMatchManager(
    private val faceNetHelper: MobileFaceNetHelper,
    private val profileEmbedding: FloatArray,
    private val config: FaceMatchConfig = FaceMatchConfig(),
) {
    companion object {
        private const val TAG = "LiveFaceMatchManager"
    }

    @Volatile
    private var consecutiveMatchCount = 0

    @Volatile
    private var consecutiveRejectCount = 0

    @Volatile
    private var lastMatchPercent = 0f

    @Volatile
    private var qualifiedFrameCount = 0

    @Volatile
    private var lastFaceBitmap: Bitmap? = null

    /**
     * Peek whether the next call to [processFrame] will actually run the MobileFaceNet model.
     * Lets the caller decide whether to allocate a rotated full-frame bitmap this frame.
     */
    fun willRunNextFrame(): Boolean = (qualifiedFrameCount + 1) % config.skipFrames == 0

    /**
     * Process a frame for face matching.
     * Only runs MobileFaceNet every [config.skipFrames] qualified frames.
     * [rotatedFullBitmap] must be in the same coordinate space as [faceBoundingBox].
     * The caller owns [rotatedFullBitmap]'s lifetime — this method does NOT recycle it.
     */
    fun processFrame(
        rotatedFullBitmap: Bitmap?,
        faceBoundingBox: Rect
    ): LiveMatchResult {
        qualifiedFrameCount++

        if (qualifiedFrameCount % config.skipFrames != 0 || rotatedFullBitmap == null) {
            return LiveMatchResult(
                matchPercent = lastMatchPercent,
                shouldTriggerAttendance = consecutiveMatchCount >= config.triggerFrames,
                shouldReject = consecutiveRejectCount >= config.rejectFrames
            )
        }

        val faceBitmap = cropFaceFromBitmap(rotatedFullBitmap, faceBoundingBox)
        if (faceBitmap == null) {
            return LiveMatchResult(
                matchPercent = lastMatchPercent,
                shouldTriggerAttendance = false,
                shouldReject = false
            )
        }

        val matchPercent = faceNetHelper.compareWithEmbedding(faceBitmap, profileEmbedding)
        lastFaceBitmap?.recycle()
        lastFaceBitmap = faceBitmap

        if (matchPercent == null) {
            // Count repeated null embeddings as rejections so the user is not
            // stuck at 0% forever when the model fails on every frame. Keep
            // consecutiveMatchCount untouched — a transient null shouldn't
            // wipe genuine match progress.
            Log.e(TAG, "Embedding extraction failed")
            consecutiveRejectCount++
            return LiveMatchResult(
                matchPercent = lastMatchPercent,
                shouldTriggerAttendance = false,
                shouldReject = consecutiveRejectCount >= config.rejectFrames
            )
        }

        lastMatchPercent = matchPercent

        if (matchPercent >= config.matchThreshold) {
            // Strong match - count toward success, clear reject counter
            consecutiveMatchCount++
            consecutiveRejectCount = 0
        } else {
            // Below match threshold - reset success counter
            consecutiveMatchCount = 0
            consecutiveRejectCount++
        }

        val shouldTrigger = consecutiveMatchCount >= config.triggerFrames
        val shouldReject = consecutiveRejectCount >= config.rejectFrames

        return LiveMatchResult(
            matchPercent = matchPercent,
            shouldTriggerAttendance = shouldTrigger,
            shouldReject = shouldReject
        )
    }

    /**
     * Crop the face region from an already-rotated full-frame bitmap.
     * Caller owns [fullBitmap]'s lifetime — this function does NOT recycle it.
     * Matches PostCaptureValidator's calculateFaceCropRect logic so the live
     * embedding is in the same crop style as the profile embedding.
     */
    private fun cropFaceFromBitmap(
        fullBitmap: Bitmap,
        faceBoundingBox: Rect
    ): Bitmap? {
        return try {
            var size = (max(faceBoundingBox.width(), faceBoundingBox.height()) * 1.2f).toInt()
            val maxSize = min(fullBitmap.width, fullBitmap.height)
            size = size.coerceAtMost(maxSize)
            val halfSize = size / 2

            // Shift center up by 6% of face height (forehead room)
            val centerX = faceBoundingBox.centerX()
            val centerY = faceBoundingBox.centerY() - (faceBoundingBox.height() * 0.06f).toInt()

            val safeCenterX = centerX.coerceIn(halfSize, fullBitmap.width - halfSize)
            val safeCenterY = centerY.coerceIn(halfSize, fullBitmap.height - halfSize)

            val cropRect = Rect(
                safeCenterX - halfSize,
                safeCenterY - halfSize,
                safeCenterX + halfSize,
                safeCenterY + halfSize
            )

            if (cropRect.width() <= 0 || cropRect.height() <= 0) return null

            Bitmap.createBitmap(
                fullBitmap,
                cropRect.left,
                cropRect.top,
                cropRect.width(),
                cropRect.height()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop face from bitmap", e)
            null
        }
    }

    fun getLastFaceBitmap(): Bitmap? = lastFaceBitmap

    fun reset() {
        consecutiveMatchCount = 0
        consecutiveRejectCount = 0
        lastMatchPercent = 0f
        qualifiedFrameCount = 0
        lastFaceBitmap?.recycle()
        lastFaceBitmap = null
    }
}
