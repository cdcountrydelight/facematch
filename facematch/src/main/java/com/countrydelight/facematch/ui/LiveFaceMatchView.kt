package com.countrydelight.facematch.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.FaceRetouchingNatural
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.countrydelight.facematch.R
import com.countrydelight.facematch.engine.FaceMatchConfig
import com.countrydelight.facematch.engine.FaceMessages
import com.countrydelight.facematch.engine.LiveMatchAnalyzer
import com.countrydelight.facematch.ui.theme.poppins_regular
import com.countrydelight.facematch.ui.theme.poppins_semiBold
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

data class ValidationState(
    val currentStep: ValidationStep,
    val completedSteps: Set<ValidationStep>,
    val errorMessage: String? = null,
    val progressPercentage: Float = 0f,
    val faceCoverage: Float = 0f
)

/**
 * Live face-match camera view. Front camera, 4:3, square guideline overlay.
 *
 * Terminal callbacks (the composable does not finish itself — the host decides):
 *  - [onMatched] match confirmed by user via the success dialog
 *  - [onRejected] user gave up after retries (covers both mismatch and spoof)
 *  - [onCancelled] user pressed back
 */
@Composable
fun LiveFaceMatchView(
    referenceEmbedding: FloatArray,
    config: FaceMatchConfig = FaceMatchConfig(),
    onMatched: (croppedFacePath: String) -> Unit,
    onRejected: (croppedFacePath: String?) -> Unit,
    onCancelled: () -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val isInForeground = remember {
        mutableStateOf(
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var cam: Camera? by remember { mutableStateOf(null) }

    val now = remember { Date() }
    val date = remember(now) { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(now) }
    val time = remember(now) { SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now) }

    var liveMatchAnalyzerRef by remember { mutableStateOf<LiveMatchAnalyzer?>(null) }

    fun tryUnbindCameraIfReady() {
        if (!cameraProviderFuture.isDone) return
        try {
            cameraProviderFuture.get()?.unbindAll()
        } catch (_: Exception) {
        }
    }

    DisposableEffect(Unit) {
        val lifecycle = ProcessLifecycleOwner.get().lifecycle
        val observer = LifecycleEventObserver { _, _ ->
            val wasInForeground = isInForeground.value
            isInForeground.value = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (wasInForeground && !isInForeground.value) {
                tryUnbindCameraIfReady()
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            tryUnbindCameraIfReady()
            liveMatchAnalyzerRef?.close()
            cameraExecutor.shutdown()
        }
    }

    var faceAlignedState by remember { mutableStateOf(false) }
    var faceDetectedState by remember { mutableStateOf(false) }
    var validationState by remember {
        mutableStateOf(
            ValidationState(
                currentStep = ValidationStep.FACE_DETECTION,
                completedSteps = emptySet(),
            )
        )
    }

    var attendanceTriggered by remember { mutableStateOf(false) }
    var matchRejected by remember { mutableStateOf(false) }
    var liveMatchPercent by remember { mutableFloatStateOf(0f) }
    var sessionRetriesLeft by remember { mutableIntStateOf(config.maxRetries) }

    fun updateValidationState(isAligned: Boolean, errorMsg: String?) {
        val completedSteps = mutableSetOf<ValidationStep>()
        val currentStep: ValidationStep
        var errorMessage: String? = null

        when {
            errorMsg == null && isAligned -> {
                completedSteps.addAll(ValidationStep.entries)
                currentStep = ValidationStep.FINAL_VALIDATION
            }

            errorMsg?.startsWith("Hold steady") == true -> {
                completedSteps.addAll(ValidationStep.entries)
                currentStep = ValidationStep.FINAL_VALIDATION
                errorMessage = errorMsg
            }

            errorMsg == FaceMessages.NO_FACE_DETECTED -> {
                currentStep = ValidationStep.FACE_DETECTION
                errorMessage = errorMsg
            }

            errorMsg == FaceMessages.MULTIPLE_FACES -> {
                completedSteps.add(ValidationStep.FACE_DETECTION)
                currentStep = ValidationStep.SINGLE_FACE
                errorMessage = errorMsg
            }

            errorMsg == FaceMessages.TOO_DARK || errorMsg == FaceMessages.TOO_BRIGHT -> {
                completedSteps.addAll(
                    listOf(
                        ValidationStep.FACE_DETECTION,
                        ValidationStep.SINGLE_FACE
                    )
                )
                currentStep = ValidationStep.BRIGHTNESS_CHECK
                errorMessage = errorMsg
            }

            errorMsg == FaceMessages.MOVE_LEFT || errorMsg == FaceMessages.MOVE_RIGHT ||
                    errorMsg == FaceMessages.MOVE_UP || errorMsg == FaceMessages.MOVE_DOWN -> {
                completedSteps.addAll(
                    listOf(
                        ValidationStep.FACE_DETECTION, ValidationStep.SINGLE_FACE,
                        ValidationStep.BRIGHTNESS_CHECK
                    )
                )
                currentStep = ValidationStep.FACE_ALIGNMENT
                errorMessage = errorMsg
            }

            errorMsg == FaceMessages.MOVE_CLOSER || errorMsg == FaceMessages.MOVE_BACK -> {
                completedSteps.addAll(
                    listOf(
                        ValidationStep.FACE_DETECTION, ValidationStep.SINGLE_FACE,
                        ValidationStep.BRIGHTNESS_CHECK, ValidationStep.FACE_ALIGNMENT
                    )
                )
                currentStep = ValidationStep.FACE_COVERAGE
                errorMessage = errorMsg
            }

            errorMsg == FaceMessages.LOOK_STRAIGHT || errorMsg == FaceMessages.KEEP_HEAD_STRAIGHT ||
                    errorMsg == FaceMessages.FACE_TOO_TILTED -> {
                completedSteps.addAll(
                    listOf(
                        ValidationStep.FACE_DETECTION, ValidationStep.SINGLE_FACE,
                        ValidationStep.BRIGHTNESS_CHECK, ValidationStep.FACE_ALIGNMENT,
                        ValidationStep.FACE_COVERAGE
                    )
                )
                currentStep = ValidationStep.HEAD_ANGLES
                errorMessage = errorMsg
            }

            errorMsg == FaceMessages.KEEP_EYES_OPEN || errorMsg == FaceMessages.REMOVE_MASK ||
                    errorMsg == FaceMessages.FACE_COVERED -> {
                completedSteps.addAll(
                    listOf(
                        ValidationStep.FACE_DETECTION, ValidationStep.SINGLE_FACE,
                        ValidationStep.BRIGHTNESS_CHECK, ValidationStep.FACE_ALIGNMENT,
                        ValidationStep.FACE_COVERAGE, ValidationStep.HEAD_ANGLES
                    )
                )
                currentStep = ValidationStep.OCCLUSION_CHECK
                errorMessage = errorMsg
            }

            errorMsg == FaceMessages.PLEASE_BLINK -> {
                completedSteps.addAll(
                    listOf(
                        ValidationStep.FACE_DETECTION, ValidationStep.SINGLE_FACE,
                        ValidationStep.BRIGHTNESS_CHECK, ValidationStep.FACE_ALIGNMENT,
                        ValidationStep.FACE_COVERAGE, ValidationStep.HEAD_ANGLES,
                        ValidationStep.OCCLUSION_CHECK
                    )
                )
                currentStep = ValidationStep.LIVENESS_DETECTION
                errorMessage = errorMsg
            }

            else -> {
                currentStep = ValidationStep.FACE_DETECTION
                errorMessage = errorMsg
            }
        }

        validationState = ValidationState(
            currentStep = currentStep,
            completedSteps = completedSteps,
            errorMessage = FaceMessages.getUserDisplayMessage(errorMessage),
            progressPercentage = calculateValidationProgress(completedSteps),
        )
    }

    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
    }

    val haptic = LocalHapticFeedback.current
    LaunchedEffect(faceAlignedState) {
        if (faceAlignedState) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {},
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF222934))
                    .padding(horizontal = 32.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .size(60.dp)
                )
            }
        },
        content = { padding ->
            if (isInForeground.value) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            val previewView = PreviewView(context).apply {
                                scaleType = PreviewView.ScaleType.FIT_CENTER
                                implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                            }
                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    if (cameraProvider.availableCameraInfos.isEmpty()) return@addListener

                                    val cameraSelector = CameraSelector.Builder()
                                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                                        .build()

                                    val lensAvailable = try {
                                        cameraSelector.filter(cameraProvider.availableCameraInfos)
                                            .isNotEmpty()
                                    } catch (_: IllegalArgumentException) {
                                        false
                                    }
                                    if (!lensAvailable) return@addListener

                                    val preview = Preview.Builder()
                                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                                        .build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }

                                    cameraProvider.unbindAll()
                                    cam = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageAnalysis
                                    )

                                    previewView.post {
                                        val liveAnalyzer = LiveMatchAnalyzer(
                                            context = context,
                                            callbackExecutor = cameraExecutor,
                                            isAnalyzerPaused = { attendanceTriggered || matchRejected },
                                            embedding192 = referenceEmbedding,
                                            config = config,
                                            onMatchSuccess = { attendanceTriggered = true },
                                            onUpdate = { matchPercent, shouldReject, status ->
                                                liveMatchPercent = matchPercent / 100f
                                                if (shouldReject && !matchRejected) {
                                                    matchRejected = true
                                                }
                                                faceDetectedState = status != null &&
                                                        status != FaceMessages.NO_FACE_DETECTED &&
                                                        status != FaceMessages.MULTIPLE_FACES &&
                                                        status != FaceMessages.CAMERA_ERROR
                                                faceAlignedState = status == FaceMessages.ALIGNED
                                                updateValidationState(
                                                    status == FaceMessages.ALIGNED,
                                                    status
                                                )
                                                validationState = validationState.copy(
                                                    progressPercentage = liveMatchPercent
                                                )
                                            }
                                        )
                                        liveMatchAnalyzerRef = liveAnalyzer
                                        imageAnalysis.setAnalyzer(cameraExecutor, liveAnalyzer)
                                    }
                                } catch (_: Exception) {
                                }
                            }, ContextCompat.getMainExecutor(context))
                            previewView
                        },
                        onRelease = {
                            imageAnalysis.clearAnalyzer()
                            tryUnbindCameraIfReady()
                        }
                    )

                    FaceGuidelineOverlay(
                        modifier = Modifier.fillMaxSize(),
                        isAligned = faceAlignedState,
                        isFaceDetected = faceDetectedState,
                        showOval = false,
                    )

                    FaceVerificationTopBar { onCancelled() }

                    FaceValidationProgress(
                        validationState = validationState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp),
                        showStepTitle = false
                    )
                }
            } else {
                Box(contentAlignment = Alignment.Center) {}
            }
        }
    )

    if (attendanceTriggered || matchRejected) {
        val retriesExhausted = !attendanceTriggered && sessionRetriesLeft <= 0
        val faceBitmap = liveMatchAnalyzerRef?.getLastFaceBitmap()
        MatchResultDialog(
            isSuccess = attendanceTriggered,
            retriesExhausted = retriesExhausted,
            matchPercent = (liveMatchPercent * 100).toInt(),
            faceBitmap = faceBitmap,
            date = date,
            time = time,
            onConfirm = {
                val bitmap = liveMatchAnalyzerRef?.getLastFaceBitmap()
                if (bitmap != null) {
                    coroutineScope.launch {
                        val path = compressAndSave(context, bitmap)
                        onMatched(path ?: "")
                    }
                } else {
                    onMatched("")
                }
            },
            onRetry = {
                if (matchRejected) sessionRetriesLeft--
                attendanceTriggered = false
                matchRejected = false
                liveMatchPercent = 0f
                liveMatchAnalyzerRef?.reset()
            },
            onForceRetry = {
                val bitmap = liveMatchAnalyzerRef?.getLastFaceBitmap()
                coroutineScope.launch {
                    val path = bitmap?.let { compressAndSave(context, it) }
                    onRejected(path)
                }
            }
        )
    }
}

@Composable
private fun MatchResultDialog(
    isSuccess: Boolean,
    retriesExhausted: Boolean,
    matchPercent: Int,
    date: String,
    time: String,
    faceBitmap: Bitmap? = null,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onForceRetry: () -> Unit
) {
    val title = if (isSuccess) "Attendance marked successfully!" else "Attendance marking failed"

    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.anim_success)
    )
    val progress by animateLottieCompositionAsState(composition = composition)

    androidx.compose.ui.window.Dialog(
        onDismissRequest = {},
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, shape = RoundedCornerShape(24.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isSuccess)
                    LottieAnimation(
                        composition = composition,
                        progress = { progress },
                        modifier = Modifier.size(90.dp),
                        contentScale = ContentScale.Crop
                    )
                else FailureIcon()

                Spacer(Modifier.height(10.dp))

                Text(
                    text = title,
                    fontSize = 18.sp,
                    color = Color.Black.copy(alpha = 0.9f),
                    fontFamily = poppins_semiBold,
                    textAlign = TextAlign.Center
                )

                if (!isSuccess)
                    Text(
                        text = "We couldn't verify your face. Please try again",
                        fontSize = 13.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        fontFamily = poppins_regular,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )

                Spacer(Modifier.height(16.dp))

                if (isSuccess) AttendanceDateTimeRow(date, time)
                else FaceVerificationTipsCard()

                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    if (isSuccess) {
                        androidx.compose.material3.Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF52B0E9)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = "Done",
                                fontFamily = poppins_semiBold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        androidx.compose.material3.Button(
                            onClick = if (retriesExhausted) onForceRetry else onRetry,
                            modifier = Modifier.weight(1f),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF52B0E9)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = "Retry", fontFamily = poppins_semiBold,
                                color = Color.White, fontSize = 14.sp
                            )
                        }
                    }
                }
            }
            if (isSuccess) {
                ConfettiAnimation(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(24.dp)),
                    iterations = 1,
                    speed = 1f
                )
            }
        }
    }
}

@Composable
fun FaceVerificationTipsCard(modifier: Modifier = Modifier) {
    val tips = listOf(
        "Use good lighting for better face detection",
        "Keep your face centered and hold still",
        "Remove anything covering your face"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFFDF2F2))
            .padding(vertical = 8.dp)
    ) {
        tips.forEachIndexed { index, tip ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFE6E8)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (index) {
                            0 -> Icons.Outlined.WbSunny
                            1 -> Icons.Outlined.CenterFocusStrong
                            else -> Icons.Outlined.FaceRetouchingNatural
                        },
                        contentDescription = null,
                        tint = Color(0xFFFF5A6E),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(18.dp))

                Text(
                    text = tip,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    fontFamily = poppins_regular,
                    color = Color(0xFF202124),
                    modifier = Modifier.weight(1f)
                )
            }

            if (index != tips.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = Color(0xFFE6E6E6)
                )
            }
        }
    }
}

@Composable
fun AttendanceDateTimeRow(date: String, time: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Green.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = Color.Green.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = date,
                fontSize = 13.sp,
                fontFamily = poppins_regular,
                color = Color.Black.copy(alpha = 0.7f)
            )
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .height(24.dp)
                .width(1.dp)
                .background(Color.Black.copy(alpha = 0.5f))
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.AccessTime,
                contentDescription = null,
                tint = Color.Green.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = time,
                fontSize = 13.sp, fontFamily = poppins_regular,

                color = Color.Black.copy(alpha = 0.7f)
            )
        }
    }
}

enum class ValidationStep {
    FACE_DETECTION,
    SINGLE_FACE,
    BRIGHTNESS_CHECK,
    FACE_ALIGNMENT,
    FACE_COVERAGE,
    HEAD_ANGLES,
    OCCLUSION_CHECK,
    LIVENESS_DETECTION,
    FINAL_VALIDATION
}

fun compressAndSave(context: Context, bitmap: Bitmap): String? {
    return try {
        val maxBytes = 300 * 1024
        val pixels = bitmap.width * bitmap.height
        var quality = when {
            pixels > 2_000_000 -> 50
            pixels > 1_000_000 -> 60
            pixels > 500_000 -> 70
            else -> 85
        }

        var bytes: ByteArray
        do {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            bytes = stream.toByteArray()
            quality -= 5
        } while (bytes.size > maxBytes && quality > 10)

        val imageFile = File(context.cacheDir, "cropped_face_${System.currentTimeMillis()}.jpg")
        imageFile.writeBytes(bytes)
        imageFile.absolutePath
    } catch (_: Exception) {
        null
    }
}

@Composable
fun FailureIcon(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(color = Color(0xFFFF4D5E).copy(alpha = 0.12f), shape = CircleShape)
        )
        Box(
            modifier = Modifier
                .size(70.dp)
                .background(color = Color(0xFFFF4D5E).copy(alpha = 0.18f), shape = CircleShape)
        )
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF5A6E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(38.dp)
            )
        }
    }
}
