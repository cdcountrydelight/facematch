package com.countrydelight.facematch.engine

import com.google.mlkit.vision.face.Face
import kotlin.math.abs

/**
 * Simplified blink detector using ML Kit eye open probabilities with hysteresis
 * to prevent jitter and improve accuracy. Uses minimum probability of both eyes
 * for better blink sensitivity.
 */
class BlinkDetectorV2(private val fastMode: Boolean = false) {
    
    companion object {
        private const val TAG = "BlinkDetectorV2"
        
        // Probability thresholds with hysteresis
        private const val EYE_CLOSED_THRESH = 0.20f // <= closed (real blinks drop to ~0.05)
        private const val EYE_OPEN_THRESH = 0.55f   // >= open

        // Smoothing parameters (frames, not time)
        private const val MIN_CLOSED_FRAMES_DEFAULT = 2    // ~100-150ms at 30fps
        private const val MIN_CLOSED_FRAMES_FAST = 1     // single frame for live match at ~15fps
        private const val MAX_RECOVERY_FRAMES = 10 // Recovery window

        // Movement threshold — reject blinks during face movement (anti-photo spoofing)
        private const val MAX_MOVEMENT_RATIO = 0.12f // 12% of face width
    }
    
    private val minClosedFrames = if (fastMode) MIN_CLOSED_FRAMES_FAST else MIN_CLOSED_FRAMES_DEFAULT

    private var closedFrames = 0
    private var sinceClosed = 0
    private var currentlyClosed = false

    // Face position tracking for movement detection
    private var lastFaceCenterX = -1f
    private var lastFaceCenterY = -1f
    
    /**
     * Update blink detection with new face data
     * @return true if a blink was just completed
     */
    fun update(face: Face?): Boolean {
        if (face == null) return false
        
        // Get ML Kit eye probabilities
        val leftProb = face.leftEyeOpenProbability
        val rightProb = face.rightEyeOpenProbability
        
        // Need both eye probabilities
        if (leftProb == null || rightProb == null || leftProb.isNaN() || rightProb.isNaN()) {
            return false
        }

        // Reject blinks during face movement (photo moved around triggers false blinks)
        val faceCenterX = face.boundingBox.centerX().toFloat()
        val faceCenterY = face.boundingBox.centerY().toFloat()
        val faceWidth = face.boundingBox.width().toFloat()

        val isMoving = if (lastFaceCenterX >= 0f && faceWidth > 0f) {
            val dx = abs(faceCenterX - lastFaceCenterX)
            val dy = abs(faceCenterY - lastFaceCenterY)
            (dx + dy) / faceWidth > MAX_MOVEMENT_RATIO
        } else false

        lastFaceCenterX = faceCenterX
        lastFaceCenterY = faceCenterY

        if (isMoving) {
            // Face is moving — reset state, don't count blinks
            closedFrames = 0
            currentlyClosed = false
            return false
        }

        // Use average of both eyes — real blinks close BOTH eyes,
        // photo glare/movement only affects one eye at a time
        val avgProb = (leftProb + rightProb) / 2f

        // Apply hysteresis to prevent jitter
        val closedNow = if (!currentlyClosed) {
            // Need to go below closed threshold to close
            avgProb <= EYE_CLOSED_THRESH
        } else {
            // Stay closed until probability goes above open threshold
            avgProb < EYE_OPEN_THRESH
        }
        
        // State machine with frame smoothing
        return when {
            closedNow -> {
                closedFrames++
                sinceClosed = 0
                
                if (!currentlyClosed) {
                    if (closedFrames >= minClosedFrames) {
                        // Transition to closed state
                        currentlyClosed = true
                    }
                }
                false // No blink yet, eyes just closed
            }
            
            else -> {
                // Eyes are open
                if (currentlyClosed) {
                    sinceClosed++
                    
                    if (sinceClosed <= MAX_RECOVERY_FRAMES) {
                        // Valid blink detected!
                        currentlyClosed = false
                        closedFrames = 0
                        sinceClosed = 0
                        true // Blink detected!
                    } else {
                        // Timeout - reset state without counting as blink
                        currentlyClosed = false
                        closedFrames = 0
                        sinceClosed = 0
                        false
                    }
                } else {
                    // Eyes remain open
                    closedFrames = 0
                    false
                }
            }
        }
    }
    
    
    /**
     * Reset the detector state
     */
    fun reset() {
        closedFrames = 0
        sinceClosed = 0
        currentlyClosed = false
        lastFaceCenterX = -1f
        lastFaceCenterY = -1f
    }
}