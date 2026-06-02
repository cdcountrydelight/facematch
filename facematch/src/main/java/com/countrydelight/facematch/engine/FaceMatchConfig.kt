package com.countrydelight.facematch.engine

data class FaceMatchConfig(
    val matchThreshold: Float = 72f,
    val triggerFrames: Int = 8,
    val rejectFrames: Int = 20,
    val skipFrames: Int = 2,
    val spoofRejectThreshold: Int = 25,
    val spoofEnabled: Boolean = true,
    val maxRetries: Int = Int.MAX_VALUE,
)
