package com.countrydelight.facematch.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.countrydelight.facematch.ui.theme.kyc_bg
import com.countrydelight.facematch.ui.theme.poppins_regular
import com.countrydelight.facematch.ui.theme.poppins_semiBold

@Composable
fun FaceValidationProgress(
    validationState: ValidationState,
    modifier: Modifier = Modifier,
    showStepTitle: Boolean = true
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        
        // Error Message (if any)
        validationState.errorMessage?.let { message ->
                Text(
                    text = message,
                    modifier = Modifier.padding(top = 10.dp),
                    color = Color.Yellow.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontFamily = poppins_regular,
                    textAlign = TextAlign.Center
                )

            Spacer(modifier = Modifier.height(38.dp))
        }
        
        // Overall Progress Bar
        val animatedProgress by animateFloatAsState(
            targetValue = validationState.progressPercentage,
            animationSpec = tween(durationMillis = 300),
            label = "overall_progress"
        )
        
        Column {
            Text(
                text = "${(validationState.progressPercentage * 100).toInt()}%",
                fontSize = 32.sp,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                fontFamily = poppins_regular,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 80.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .background(
                            color = kyc_bg,
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
        }
        
        if (showStepTitle) {
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = getStepTitle(validationState.currentStep),
                fontSize = 13.sp,
                color = Color.White,
                fontFamily = poppins_semiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun getStepTitle(step: ValidationStep): String {
    return when (step) {
        ValidationStep.FACE_DETECTION -> "Detecting Face"
        ValidationStep.SINGLE_FACE -> "Checking Single Face"
        ValidationStep.BRIGHTNESS_CHECK -> "Checking Lighting"
        ValidationStep.FACE_ALIGNMENT -> "Aligning Face"
        ValidationStep.FACE_COVERAGE -> "Checking Face Size"
        ValidationStep.HEAD_ANGLES -> "Checking Head Position"
        ValidationStep.OCCLUSION_CHECK -> "Checking Face Visibility"
        ValidationStep.LIVENESS_DETECTION -> "Verifying Liveness"
        ValidationStep.FINAL_VALIDATION -> "Ready to Capture"
    }
}

private fun getStepDescription(step: ValidationStep): String {
    return when (step) {
        ValidationStep.FACE_DETECTION -> "Position your face in the oval frame"
        ValidationStep.SINGLE_FACE -> "Ensure only one person is visible"
        ValidationStep.BRIGHTNESS_CHECK -> "Adjust lighting if needed"
        ValidationStep.FACE_ALIGNMENT -> "Center your face in the oval"
        ValidationStep.FACE_COVERAGE -> "Move closer or farther as needed"
        ValidationStep.HEAD_ANGLES -> "Look straight and keep head level"
        ValidationStep.OCCLUSION_CHECK -> "Keep eyes, nose and mouth visible"
        ValidationStep.LIVENESS_DETECTION -> "Please blink once naturally"
        ValidationStep.FINAL_VALIDATION -> "Hold steady for capture"
    }
}

// Helper function to calculate progress based on completed steps
fun calculateValidationProgress(completedSteps: Set<ValidationStep>): Float {
    return completedSteps.size.toFloat() / ValidationStep.entries.size
}