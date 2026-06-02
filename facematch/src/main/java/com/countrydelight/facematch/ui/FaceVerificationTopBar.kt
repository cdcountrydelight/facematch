package com.countrydelight.facematch.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.countrydelight.facematch.ui.theme.poppins_regular
import com.countrydelight.facematch.ui.theme.poppins_semiBold

@Composable
fun FaceVerificationTopBar(onClick: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .height(56.dp)
        ) {
            // Icon on the left
            Icon(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(24.dp)
                    .clickable { onClick("Back") },
                imageVector = Icons.AutoMirrored.Default.ArrowBack,
                contentDescription = "Back Button",
                tint = Color.White
            )

            // Text centered horizontally
            Text(
                text = "Mark Attendance",
                modifier = Modifier.align(Alignment.Center),
                fontSize = 15.sp,
                fontFamily = poppins_regular,
                color = Color.White,
            )
        }

        Spacer(Modifier.height(10.dp))

        val helpText =
            "Position your face clearly in front of the camera and hold still until it’s verified"

        Text(
            text = helpText,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.8f),
            fontFamily = poppins_regular,
            modifier = Modifier.padding(horizontal = 20.dp),
            textAlign = TextAlign.Center
        )
    }
}