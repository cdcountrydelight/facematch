package com.countrydelight.facematch.engine

/**
 * Centralized face validation messages for consistent messaging across the app
 */
object FaceMessages {
    
    // Face Detection Messages
    const val NO_FACE_DETECTED = "No face detected"
    const val MULTIPLE_FACES = "Multiple faces detected"
    
    // Positioning Messages  
    const val MOVE_LEFT = "Move left"
    const val MOVE_RIGHT = "Move right"
    const val MOVE_UP = "Move up"
    const val MOVE_DOWN = "Move down"
    const val MOVE_CLOSER = "Move closer"
    const val MOVE_BACK = "Move back"
    
    // Head Position Messages
    const val LOOK_STRAIGHT = "Look straight"
    const val KEEP_HEAD_STRAIGHT = "Keep head straight"
    
    // Lighting Messages
    const val TOO_DARK = "Too dark"
    const val TOO_BRIGHT = "Too bright"
    
    // Occlusion Messages
    const val KEEP_EYES_OPEN = "Keep eyes open"
    const val REMOVE_MASK = "Remove mask"
    const val FACE_COVERED = "Face covered"
    const val FACE_TOO_TILTED = "Face too tilted"
    
    // Liveness Messages
    const val PLEASE_BLINK = "Please blink once"
    
    // Anti-spoofing Messages
    const val SPOOF_DETECTED = "Spoof detected"

    // Status Messages
    const val ALIGNED = "aligned"
    const val CAMERA_ERROR = "Camera error"
    
    /**
     * Get user-friendly display message from internal message
     */
    fun getUserDisplayMessage(internalMessage: String?): String? {
        return when (internalMessage) {
            NO_FACE_DETECTED -> "Position your face in front of the camera"
            MULTIPLE_FACES -> "Only one person should be visible"
            MOVE_LEFT -> "Move your face left"
            MOVE_RIGHT -> "Move your face right"
            MOVE_UP -> "Move your face up"
            MOVE_DOWN -> "Move your face down"
            MOVE_CLOSER -> "Move closer to the camera"
            MOVE_BACK -> "Move back from the camera"
            LOOK_STRAIGHT -> "Look straight at the camera"
            KEEP_HEAD_STRAIGHT -> "Keep your head straight"
            TOO_DARK -> "Move to a brighter area"
            TOO_BRIGHT -> "Avoid direct bright light"
            KEEP_EYES_OPEN -> "Keep both eyes open"
            REMOVE_MASK -> "Remove any face covering"
            FACE_COVERED -> "Ensure your face is clearly visible"
            FACE_TOO_TILTED -> "Keep your head straight"
            PLEASE_BLINK -> "Look at camera and blink slowly"
            SPOOF_DETECTED -> "Make sure your face is clearly visible"
            CAMERA_ERROR -> "Camera error, please try again"
            ALIGNED -> null // No message needed when aligned
            else -> internalMessage // Fallback to original message
        }
    }
}