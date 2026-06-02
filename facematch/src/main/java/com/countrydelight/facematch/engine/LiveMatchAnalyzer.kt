package com.countrydelight.facematch.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.countrydelight.facematch.internal.ImageUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions.*
import java.util.concurrent.Executor

/**
 * Lightweight analyzer for live face matching.
 * Only checks: face detection, single face, basic positioning, liveness (blink).
 * Skips: brightness, occlusion/mask, strict coverage, head angles, countdown.
 *
 * [callbackExecutor] dispatches the ML Kit success/failure/complete listeners off the main
 * thread. onUpdate / onMatchSuccess are therefore invoked on that executor — callers must
 * not touch main-thread-only APIs (haptic, View) directly inside those callbacks.
 * Compose MutableState writes are safe since Snapshot state is thread-safe.
 */
class LiveMatchAnalyzer(
    private val context: Context,
    private val callbackExecutor: Executor,
    private val isAnalyzerPaused: () -> Boolean,
    private val embedding192: FloatArray,
    private val onMatchSuccess: () -> Unit = {},
    private val onUpdate: (matchPercent: Float, shouldReject: Boolean, faceStatus: String?) -> Unit,
    private val config: FaceMatchConfig = FaceMatchConfig(),
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "LiveMatchAnalyzer"
    }

    @Volatile
    private var isClosed = false

    private val detector by lazy {
        val options = Builder()
            .setPerformanceMode(PERFORMANCE_MODE_FAST)
            .setClassificationMode(CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(LANDMARK_MODE_NONE)
            .setContourMode(CONTOUR_MODE_NONE)
            .enableTracking()
            .build()
        FaceDetection.getClient(options)
    }

    private var helperInitialized = false
    private val helper by lazy {
        helperInitialized = true
        MobileFaceNetHelper(context)
    }
    private val manager by lazy { LiveFaceMatchManager(helper, embedding192, config) }

    private val livenessDetector = LivenessDetector(fastMode = true, blinkHoldMs = 5000L)
    private var spoofDetectorInitialized = false
    private val spoofDetector by lazy {
        spoofDetectorInitialized = true
        FaceSpoofDetector(context)
    }

    private var frameCount = 0
    private val analyzeEveryNthFrame = 2

    // True while the user is currently being classified as a spoof.
    // Flips on a FAKE verdict, clears on the next REAL verdict. Keeps the UI
    // steady across match-skip frames instead of flickering to ALIGNED.
    private var spoofActive = false

    // Lives outside the match manager (which we reset on every spoof frame),
    // so if the spoof model false-flags every frame the count still climbs
    // and shouldReject eventually signals the OTP fallback.
    private var consecutiveSpoofFrames = 0

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (isClosed || isAnalyzerPaused()) {
            imageProxy.close()
            return
        }

        frameCount++
        if (frameCount % analyzeEveryNthFrame != 0) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        try {
            detector.process(image)
                .addOnSuccessListener(callbackExecutor) { faces ->
                    if (isClosed) return@addOnSuccessListener

                    when {
                        faces.isEmpty() -> {
                            livenessDetector.expireBlink()
                            onUpdate(0f, false, FaceMessages.NO_FACE_DETECTED)
                        }

                        faces.size > 1 -> {
                            livenessDetector.expireBlink()
                            onUpdate(0f, false, FaceMessages.MULTIPLE_FACES)
                        }

                        else -> {
                            val face = faces[0]

                            val faceArea = face.boundingBox.width() * face.boundingBox.height()
                            val frameArea = imageProxy.width * imageProxy.height
                            val coverage = faceArea.toFloat() / frameArea.toFloat()

                            if (coverage < 0.10f) {
                                onUpdate(0f, false, FaceMessages.MOVE_CLOSER)
                                return@addOnSuccessListener
                            }

                            if (coverage > 0.85f) {
                                onUpdate(0f, false, FaceMessages.MOVE_BACK)
                                return@addOnSuccessListener
                            }

                            // Spoof runs on every match-model frame so each match decision has
                            // a same-frame spoof verdict. Shared bitmap covers match's crop too.
                            val willRunMatch = manager.willRunNextFrame()
                            val rotatedBitmap: Bitmap? = if (willRunMatch) {
                                ImageUtils().rotateImageProxyToBitmap(imageProxy)
                            } else null

                            if (config.spoofEnabled && willRunMatch && rotatedBitmap != null) {
                                val (isReal, _) = spoofDetector.detectSpoof(rotatedBitmap, face.boundingBox)
                                spoofActive = !isReal
                                if (spoofActive) {
                                    consecutiveSpoofFrames++
                                    if (helperInitialized) manager.reset()
                                } else {
                                    consecutiveSpoofFrames = 0
                                }
                            }

                            if (config.spoofEnabled && spoofActive) {
                                rotatedBitmap?.recycle()
                                if (!willRunMatch) manager.processFrame(null, face.boundingBox)
                                val shouldReject = consecutiveSpoofFrames >= config.spoofRejectThreshold
                                onUpdate(0f, shouldReject, FaceMessages.SPOOF_DETECTED)
                                return@addOnSuccessListener
                            }

                            // isLive is true for BLINK_HOLD_MS after any detected blink.
//                        val (recentBlink, _) = livenessDetector.checkLiveness(face)

                            val matchResult = manager.processFrame(
                                rotatedBitmap,
                                face.boundingBox
                            )
                            rotatedBitmap?.recycle()

                            //  val waitingForBlink = matchResult.shouldTriggerAttendance && !recentBlink

//                        val status = if (waitingForBlink) {
//                            FaceMessages.PLEASE_BLINK
//                        } else {
//                            FaceMessages.ALIGNED
//                        }

                            onUpdate(matchResult.matchPercent, matchResult.shouldReject, FaceMessages.ALIGNED)

                            // Success condition
                            if (matchResult.shouldTriggerAttendance) {
                                onMatchSuccess()
                            }
                        }
                    }
                }
                .addOnFailureListener(callbackExecutor) {
                    Log.e(TAG, "Face detection failed", it)
                    onUpdate(0f, false, FaceMessages.CAMERA_ERROR)
                }
                .addOnCompleteListener(callbackExecutor) {
                    imageProxy.close()
                }
        } catch (e: Exception) {
            Log.e(TAG, "detector.process threw synchronously", e)
            imageProxy.close()
        }
    }

    fun getLastFaceBitmap(): Bitmap? = manager.getLastFaceBitmap()

    fun reset() {
        livenessDetector.fullReset()
        manager.reset()
        spoofActive = false
        consecutiveSpoofFrames = 0
    }

    fun close() {
        isClosed = true
        if (helperInitialized) helper.close()
        if (config.spoofEnabled && spoofDetectorInitialized) spoofDetector.close()
        detector.close()
    }
}
