package com.countrydelight.facematch.api

sealed interface FaceMatchOutcome {
    data class Matched(val croppedFacePath: String) : FaceMatchOutcome
    data class Rejected(val croppedFacePath: String?) : FaceMatchOutcome
    data object Cancelled : FaceMatchOutcome
}
