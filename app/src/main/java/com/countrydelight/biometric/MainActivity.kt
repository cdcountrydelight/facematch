package com.countrydelight.biometric

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.countrydelight.biometric.ui.theme.BiometricAttendanceTheme
import com.countrydelight.facematch.api.FaceMatchContract
import com.countrydelight.facematch.api.FaceMatchInput
import com.countrydelight.facematch.api.FaceMatchOutcome
import com.countrydelight.facematch.engine.FaceMatchConfig
import com.countrydelight.facematch.engine.MobileFaceNetHelper

class MainActivity : ComponentActivity() {
    private val REAL_EMBEDDING = "9VksO3Wsmzs6trw69b0UvDeGI7ypYFa9j4l8vA98Cj6ZOiQ+J3CxvU9vjzxctLq7jAaUug7SsDx6uEC7pOw+vsEvAjx1DEo7JYmtuoAwR7zo+ru9segpPC1V4r2LpJq4AGuhPSXHE7uGxnC7LKBEvX1Y4j3E+I48jMEkPCp0Cr7Xveo8PQoLufnJLL0LmoY9EF1vPbTvgLxwS0q78Rb4vd9LzTp721a660i/uy9pMjy7tSc7uOmqPCruFr7ibGG9O330u6b2lzwPq8a8/B+QuD8Itb2OmgS7bvqGPDyMOzwEBZy9K9ZROe0r2rwLaZo6nAVnvL8F9ztWoX88RBXcvHkY1TvZVBG++H4zujfTDTwHhhU81wmEO7wKIbxVzZG+0RZ+PjtsUbtr2qk9O5unPPear7vCL3w7dMOZPfRoobz0G187VWZdPWsi5Loqbp47BSP5PevtbDp2Wv475l+fvOucuj1Nt0a+s3cfvegPBTyIXhk8C/8tPH+/ED0gqAC8qMI+vZBdSr7G45c7dTf4uxP+2LtLj6w6a4VMvLtuGrp6R/w7pVmMukI+pr0ulTk8k8oAPDPJ4DsUCg+++hNGOeySqTsfV24+B5p1O8wvPzwETbw7Jc4vPEkpgT669J69rtbRvUd04LlKcfK7e71MO3fGtTkGHpm6nzmxO7s0Hbx0F5o8UV0gPsj3lDrU71U8G84pO1fRn7zwqzO+nP7YO2sejL2ULjq9ZiQkvCiPW7znMgu74bmYORcOs7ur1Rg+SRbavL+GjLxq0RI8fuavucuPRrtcoEm8FkiUOfKe0r0jfSU+fu1FuogYkTsD4hs8j1/9O3i0FzwUh/M9PtsDuxBsRTkfVaw5XfrTO71/sbpRhlA73aWQu+fhyjvuUiw94NIZOagsIzxAwJo9oyUkPa4lr7p/Uaq9RwfVvAamtbsUfqG9zXkMvVKWcTp/n6y7cASpPG94uj0D+qC7yArOu4DWSL2mSi29KjOPvQkECb1MOMg+NznMvL9GVbyVY4S8"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BiometricAttendanceTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FaceMatchDemo(modifier = Modifier.padding(innerPadding), REAL_EMBEDDING)
                }
            }
        }
    }
}

@Composable
private fun FaceMatchDemo(modifier: Modifier = Modifier, embedding: String) {
    val context = LocalContext.current
    var outcome by remember { mutableStateOf<FaceMatchOutcome?>(null) }
    val demoEmbedding = remember { MobileFaceNetHelper.embeddingToBase64(FloatArray(192)) }

    val faceMatchLauncher = rememberLauncherForActivityResult(FaceMatchContract()) { result ->
        outcome = result
    }

    fun launch() {
        faceMatchLauncher.launch(
            FaceMatchInput(
                referenceEmbeddingBase64 = embedding,
                config = FaceMatchConfig(maxRetries = 1),
            )
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launch() }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Face match demo (dummy reference — will not match a real face)")
        Button(onClick = {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) launch() else permissionLauncher.launch(Manifest.permission.CAMERA)
        }) {
            Text("Launch face match")
        }
        outcome?.let {
            Text("Outcome: ${it.describe()}")
        }
    }
}

private fun FaceMatchOutcome.describe(): String = when (this) {
    is FaceMatchOutcome.Matched -> "Matched — path=$croppedFacePath"
    is FaceMatchOutcome.Rejected -> "Rejected — path=${croppedFacePath ?: "(none)"}"
    FaceMatchOutcome.Cancelled -> "Cancelled"
}
