package com.countrydelight.facematch.ui

import android.graphics.BlurMaskFilter
import android.graphics.Rect
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.countrydelight.facematch.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

fun getCenterSquareRect(width: Float, height: Float): Rect {
    // For portrait, use minimum dimension
    val size = min(width, height)
    val left = ((width - size) / 2f).toInt()
    val top = ((height - size) / 2f).toInt()
    return Rect(left, top, (left + size).toInt(), (top + size).toInt())
}

@Composable
fun FaceGuidelineOverlay(
    modifier: Modifier = Modifier,
    isAligned: Boolean,
    showOval: Boolean = true,
    isFaceDetected: Boolean = false,
) {
    BoxWithConstraints(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {

            // Use the full canvas size directly
            val squareRect = getCenterSquareRect(size.width, size.height)
            val squareSize = squareRect.width().toFloat()
            val squareLeft = squareRect.left.toFloat()
            val squareTop = squareRect.top.toFloat()
            val squareRight = squareRect.right.toFloat()
            val squareBottom = squareRect.bottom.toFloat()

            // Define corner radius for rounded square
            val cornerRadius = 16.dp.toPx()

            // 1. Draw dark background everywhere
            drawRect(Color(0xFF222934))

            // 2. Add padding only to left/right of square
            val squarePadding = 8.dp.toPx()
            val paddedSquareLeft = squareLeft + squarePadding
            val paddedSquareRight = squareRight - squarePadding

            // 3. Clear the padded square area (camera visible with rounded corners)
            val roundedSquarePath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = paddedSquareLeft,
                        top = squareTop,
                        right = paddedSquareRight,
                        bottom = squareBottom,
                        radiusX = cornerRadius,
                        radiusY = cornerRadius
                    )
                )
            }

            drawPath(
                path = roundedSquarePath,
                color = Color.Transparent,
                blendMode = BlendMode.Clear
            )

            if (!showOval) {
                // Live match mode: replace the rounded-rect cutout with a circle.
                drawRect(Color(0xFF222934))
                val circlePadding = 30.dp.toPx()
                val diameter = minOf(size.width, size.height) - 2f * circlePadding
                drawCircle(
                    color = Color.Transparent,
                    radius = diameter / 2f,
                    center = Offset(size.width / 2f, size.height / 2f),
                    blendMode = BlendMode.Clear,
                )
            }

            if (showOval) {
                // Oval is 85% of square size
                val ovalWidth = squareSize * 0.70f
                val ovalHeight = ovalWidth * 1.3f

                // Center the oval within the square (already offset adjusted)
                val ovalLeft = squareLeft + (squareSize - ovalWidth) / 2f
                val ovalTop = squareTop + (squareSize - ovalHeight) / 2f

                // Add dim overlay to square area (except oval)
                drawPath(
                    path = roundedSquarePath,
                    color = Color.Black.copy(alpha = 0.7f)
                )

                // Clear oval area for 100% visibility
                drawOval(
                    color = Color.Transparent,
                    topLeft = Offset(ovalLeft, ovalTop),
                    size = Size(ovalWidth, ovalHeight),
                    blendMode = BlendMode.Clear
                )


                val ovalColor = if (isAligned) Color(0xFF3EBD60) else Color.White

                // Layer 1: Glow effect using native canvas blur
                drawIntoCanvas { canvas ->
                    val paint = Paint().asFrameworkPaint().apply {
                        color = ovalColor.copy(alpha = 0.3f).toArgb()
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 12f
                        maskFilter = BlurMaskFilter(
                            25f,
                            BlurMaskFilter.Blur.NORMAL
                        )
                    }

                    canvas.nativeCanvas.drawOval(
                        ovalLeft,
                        ovalTop,
                        ovalLeft + ovalWidth,
                        ovalTop + ovalHeight,
                        paint
                    )
                }

                // Layer 2: Outer soft glow
                drawOval(
                    color = ovalColor.copy(alpha = 0.1f),
                    topLeft = Offset(ovalLeft, ovalTop),
                    size = Size(ovalWidth, ovalHeight),
                    style = Stroke(width = 6.dp.toPx()),
                    blendMode = BlendMode.Plus
                )

                // Layer 3: Mid glow
                drawOval(
                    color = ovalColor.copy(alpha = 0.3f),
                    topLeft = Offset(ovalLeft, ovalTop),
                    size = Size(ovalWidth, ovalHeight),
                    style = Stroke(width = 3.dp.toPx())
                )

                val strokeStyle = if (isAligned) {
                    Stroke(width = 2.dp.toPx())
                } else {
                    Stroke(
                        width = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 15f), 0f)
                    )
                }

                // Layer 4: Main oval border (top layer)
                drawOval(
                    color = ovalColor,
                    topLeft = Offset(ovalLeft, ovalTop),
                    size = Size(ovalWidth, ovalHeight),
                    style = strokeStyle
                )
            }
        }

        if (!showOval) {
            val infiniteTransition = rememberInfiniteTransition(label = "scanRing")
            val sweep by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "sweepAngle",
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val circlePaddingPx = 38.dp.toPx()
                val radius = (minOf(size.width, size.height) - 2f * circlePaddingPx) / 2f
                val cx = size.width / 2f
                val cy = size.height / 2f

                val tickCount = 200
                val baseLength = 5.dp.toPx()
                val extraLength = 7.dp.toPx()
                val strokeWidth = 2.dp.toPx()
                val highlightHalfSpan = 12
                val radialOffset = 10.dp.toPx()
                val greyColor = Color.White.copy(alpha = 0.35f)

                val degPerTick = 360f / tickCount

                if (isAligned) {
                    for (i in 0 until tickCount) {
                        val angle = i * degPerTick

                        var diff = abs(angle - sweep) % 360f
                        if (diff > 180f) diff = 360f - diff
                        val tickDistance = diff / degPerTick
                        val highlight = (1f - tickDistance / highlightHalfSpan).coerceIn(0f, 1f)
                        val length = baseLength + extraLength * highlight
                        val color = lerp(greyColor, Color(0xFF3EBD60), highlight)

                        val rad = (angle * PI / 180.0).toFloat()
                        val cosA = cos(rad)
                        val sinA = sin(rad)
                        val inner = radius + radialOffset
                        val outer = inner + length
                        drawLine(
                            color = color,
                            start = Offset(cx + inner * cosA, cy + inner * sinA),
                            end = Offset(cx + outer * cosA, cy + outer * sinA),
                            strokeWidth = strokeWidth,
                            cap = StrokeCap.Round,
                        )
                    }
                }

                // Corner brackets inside the circle
                val bracketHalfSide = radius * 0.62f
                val armLength = 18.dp.toPx()
                val armStroke = 3.dp.toPx()
                val bracketColor = when {
                    isAligned -> Color(0xFF3EBD60)
                    isFaceDetected -> Color.White
                    else -> greyColor
                }

                val left = cx - bracketHalfSide
                val right = cx + bracketHalfSide
                val top = cy - bracketHalfSide
                val bottom = cy + bracketHalfSide

                // top-left
                drawLine(
                    bracketColor,
                    Offset(left, top),
                    Offset(left + armLength, top),
                    armStroke,
                    StrokeCap.Round
                )
                drawLine(
                    bracketColor,
                    Offset(left, top),
                    Offset(left, top + armLength),
                    armStroke,
                    StrokeCap.Round
                )
                // top-right
                drawLine(
                    bracketColor,
                    Offset(right, top),
                    Offset(right - armLength, top),
                    armStroke,
                    StrokeCap.Round
                )
                drawLine(
                    bracketColor,
                    Offset(right, top),
                    Offset(right, top + armLength),
                    armStroke,
                    StrokeCap.Round
                )
                // bottom-left
                drawLine(
                    bracketColor,
                    Offset(left, bottom),
                    Offset(left + armLength, bottom),
                    armStroke,
                    StrokeCap.Round
                )
                drawLine(
                    bracketColor,
                    Offset(left, bottom),
                    Offset(left, bottom - armLength),
                    armStroke,
                    StrokeCap.Round
                )
                // bottom-right
                drawLine(
                    bracketColor,
                    Offset(right, bottom),
                    Offset(right - armLength, bottom),
                    armStroke,
                    StrokeCap.Round
                )
                drawLine(
                    bracketColor,
                    Offset(right, bottom),
                    Offset(right, bottom - armLength),
                    armStroke,
                    StrokeCap.Round
                )
            }

            if (!isFaceDetected) {
                val placeholderSide = (minOf(maxWidth, maxHeight) - 72.dp) * 0.62f - 24.dp
                Image(
                    painter = painterResource(id = R.drawable.img_face_placeholder),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(placeholderSide),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}