package com.countrydelight.facematch.api

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.countrydelight.facematch.engine.FaceMatchConfig
import com.countrydelight.facematch.engine.MobileFaceNetHelper
import com.countrydelight.facematch.ui.LiveFaceMatchView

class FaceMatchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val base64 = intent.getStringExtra(FaceMatchContract.EXTRA_REFERENCE_EMBEDDING)
        if (base64.isNullOrEmpty()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        val embedding = try {
            MobileFaceNetHelper.base64ToEmbedding(base64)
        } catch (_: Throwable) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val defaults = FaceMatchConfig()
        val config = FaceMatchConfig(
            matchThreshold = intent.getFloatExtra(
                FaceMatchContract.EXTRA_MATCH_THRESHOLD, defaults.matchThreshold
            ),
            triggerFrames = intent.getIntExtra(
                FaceMatchContract.EXTRA_TRIGGER_FRAMES, defaults.triggerFrames
            ),
            rejectFrames = intent.getIntExtra(
                FaceMatchContract.EXTRA_REJECT_FRAMES, defaults.rejectFrames
            ),
            skipFrames = intent.getIntExtra(
                FaceMatchContract.EXTRA_SKIP_FRAMES, defaults.skipFrames
            ),
            spoofRejectThreshold = intent.getIntExtra(
                FaceMatchContract.EXTRA_SPOOF_REJECT_THRESHOLD, defaults.spoofRejectThreshold
            ),
            spoofEnabled = intent.getBooleanExtra(
                FaceMatchContract.EXTRA_SPOOF_ENABLED, defaults.spoofEnabled
            ),
            maxRetries = intent.getIntExtra(
                FaceMatchContract.EXTRA_MAX_RETRIES, defaults.maxRetries
            ),
        )

        setContent {
            LiveFaceMatchView(
                referenceEmbedding = embedding,
                config = config,
                onMatched = { path -> finishWithResult(FaceMatchContract.TYPE_MATCHED, path) },
                onRejected = { path -> finishWithResult(FaceMatchContract.TYPE_REJECTED, path) },
                onCancelled = {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                },
            )
        }
    }

    private fun finishWithResult(type: String, path: String?) {
        val data = Intent()
            .putExtra(FaceMatchContract.EXTRA_OUTCOME_TYPE, type)
            .putExtra(FaceMatchContract.EXTRA_OUTCOME_PATH, path)
        setResult(Activity.RESULT_OK, data)
        finish()
    }
}
