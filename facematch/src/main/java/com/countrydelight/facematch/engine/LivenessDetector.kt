package com.countrydelight.facematch.engine

import android.os.SystemClock
import android.util.Log
import com.google.mlkit.vision.face.Face

/**
 * Manages liveness detection with a time-based hold system.
 * Once a blink is detected, it remains valid for [blinkHoldMs] milliseconds.
 */
class LivenessDetector(
    fastMode: Boolean = false,
    private val blinkHoldMs: Long = 3000L
) {

    companion object {
        private const val TAG = "LivenessDetector"
    }
    
    private val blinkDetector = BlinkDetectorV2(fastMode)
    
    @Volatile
    private var blinkHeld = false
    
    @Volatile
    private var blinkHeldUntilMs = 0L

    /**
     * method that returns boolean instead of using callback
     */
    fun checkLiveness(face: Face?): Pair<Boolean, String?> {
        if (face == null) {
            return Pair(false, "No face detected")
        }
        
        // Check if we have a valid held blink
        if (!hasValidBlink()) {
            // Try to detect a new blink
            val blinkedNow = blinkDetector.update(face)
            
            if (!blinkedNow) {
                // No blink yet
                return Pair(false, "Please blink once")
            }
            
            // First blink detected
            markBlinkDetected()
            Log.d(TAG, "Blink detected and held for ${blinkHoldMs}ms")
        }
        
        // Liveness confirmed
        val remainingMs = blinkHeldUntilMs - SystemClock.elapsedRealtime()
        Log.d(TAG, "Liveness confirmed (${remainingMs}ms remaining)")
        return Pair(true, null)
    }
    
    /**
     * Check if we have a valid blink within the hold period
     */
    private fun hasValidBlink(): Boolean {
        return blinkHeld && SystemClock.elapsedRealtime() < blinkHeldUntilMs
    }
    
    /**
     * Mark that a blink was detected and set hold timer
     */
    private fun markBlinkDetected() {
        blinkHeld = true
        blinkHeldUntilMs = SystemClock.elapsedRealtime() + blinkHoldMs
    }
    
    /**
     * Reset the blink hold (for new session)
     */
    private fun resetBlinkHold() {
        blinkHeld = false
        blinkHeldUntilMs = 0L
        Log.d(TAG, "Blink hold reset")
    }
    
    /**
     * Force expire the blink hold
     */
    fun expireBlink() {
        blinkHeld = false
        blinkHeldUntilMs = 0L
    }
    
    /**
     * Get remaining time for blink hold in milliseconds
     */
    fun getRemainingHoldTime(): Long {
        return if (hasValidBlink()) {
            blinkHeldUntilMs - SystemClock.elapsedRealtime()
        } else {
            0L
        }
    }
    
    /**
     * Reset everything including detector state
     */
    fun fullReset() {
        resetBlinkHold()
        blinkDetector.reset()
    }
}