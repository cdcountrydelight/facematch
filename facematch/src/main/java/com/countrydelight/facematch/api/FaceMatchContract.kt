package com.countrydelight.facematch.api

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

class FaceMatchContract : ActivityResultContract<FaceMatchInput, FaceMatchOutcome>() {

    override fun createIntent(context: Context, input: FaceMatchInput): Intent =
        Intent(context, FaceMatchActivity::class.java)
            .putExtra(EXTRA_REFERENCE_EMBEDDING, input.referenceEmbeddingBase64)
            .putExtra(EXTRA_MATCH_THRESHOLD, input.config.matchThreshold)
            .putExtra(EXTRA_TRIGGER_FRAMES, input.config.triggerFrames)
            .putExtra(EXTRA_REJECT_FRAMES, input.config.rejectFrames)
            .putExtra(EXTRA_SKIP_FRAMES, input.config.skipFrames)
            .putExtra(EXTRA_SPOOF_REJECT_THRESHOLD, input.config.spoofRejectThreshold)
            .putExtra(EXTRA_SPOOF_ENABLED, input.config.spoofEnabled)
            .putExtra(EXTRA_MAX_RETRIES, input.config.maxRetries)

    override fun parseResult(resultCode: Int, intent: Intent?): FaceMatchOutcome {
        if (resultCode != Activity.RESULT_OK || intent == null) return FaceMatchOutcome.Cancelled
        val path = intent.getStringExtra(EXTRA_OUTCOME_PATH)
        return when (intent.getStringExtra(EXTRA_OUTCOME_TYPE)) {
            TYPE_MATCHED -> FaceMatchOutcome.Matched(path.orEmpty())
            TYPE_REJECTED -> FaceMatchOutcome.Rejected(path)
            else -> FaceMatchOutcome.Cancelled
        }
    }

    internal companion object {
        const val EXTRA_REFERENCE_EMBEDDING = "com.countrydelight.facematch.REFERENCE_EMBEDDING"
        const val EXTRA_MATCH_THRESHOLD = "com.countrydelight.facematch.MATCH_THRESHOLD"
        const val EXTRA_TRIGGER_FRAMES = "com.countrydelight.facematch.TRIGGER_FRAMES"
        const val EXTRA_REJECT_FRAMES = "com.countrydelight.facematch.REJECT_FRAMES"
        const val EXTRA_SKIP_FRAMES = "com.countrydelight.facematch.SKIP_FRAMES"
        const val EXTRA_SPOOF_REJECT_THRESHOLD = "com.countrydelight.facematch.SPOOF_REJECT_THRESHOLD"
        const val EXTRA_SPOOF_ENABLED = "com.countrydelight.facematch.SPOOF_ENABLED"
        const val EXTRA_MAX_RETRIES = "com.countrydelight.facematch.MAX_RETRIES"

        const val EXTRA_OUTCOME_TYPE = "com.countrydelight.facematch.OUTCOME_TYPE"
        const val EXTRA_OUTCOME_PATH = "com.countrydelight.facematch.OUTCOME_PATH"

        const val TYPE_MATCHED = "matched"
        const val TYPE_REJECTED = "rejected"
    }
}
