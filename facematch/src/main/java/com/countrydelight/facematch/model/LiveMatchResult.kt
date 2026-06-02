package com.countrydelight.facematch.model

data class LiveMatchResult(
    val matchPercent: Float,
    val shouldTriggerAttendance: Boolean,
    val shouldReject: Boolean
)
