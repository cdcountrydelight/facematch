package com.countrydelight.facematch.api

import com.countrydelight.facematch.engine.FaceMatchConfig

data class FaceMatchInput(
    val referenceEmbeddingBase64: String,
    val config: FaceMatchConfig = FaceMatchConfig(),
)
