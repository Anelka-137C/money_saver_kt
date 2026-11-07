package com.example.money_saver_android.ui.components.expenses

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.money_saver_android.ui.theme.BorderColor
import com.example.money_saver_android.ui.theme.ErrorRed
import com.example.money_saver_android.ui.theme.FieldBackground
import com.example.money_saver_android.ui.theme.TextSecondary
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun SlideToConfirmButton(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    val thumbSizeDp = 48.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }
    val thumbPaddingDp = 4.dp
    val thumbPaddingPx = with(density) { thumbPaddingDp.toPx() }
    val maxDragPx = (trackWidthPx - thumbSizePx - (thumbPaddingPx * 2)).coerceAtLeast(0f)

    val dragOffset = remember { Animatable(0f) }
    var confirmed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(FieldBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(28.dp))
            .onSizeChanged { size ->
                trackWidthPx = size.width.toFloat()
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val progress = if (maxDragPx > 0f) (dragOffset.value / maxDragPx).coerceIn(0f, 1f) else 0f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ErrorRed.copy(alpha = 0.15f * progress))
        )

        Text(
            text = if (progress > 0.8f) "Soltar para eliminar" else "Desliza para eliminar >>",
            color = if (progress > 0.8f) ErrorRed else TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center)
        )

        Box(
            modifier = Modifier
                .padding(start = thumbPaddingDp)
                .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                .size(thumbSizeDp)
                .clip(CircleShape)
                .background(ErrorRed)
                .pointerInput(maxDragPx, confirmed) {
                    if (confirmed) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (maxDragPx > 0f && dragOffset.value >= maxDragPx * 0.8f) {
                                confirmed = true
                                coroutineScope.launch {
                                    dragOffset.animateTo(maxDragPx)
                                    onConfirm()
                                }
                            } else {
                                coroutineScope.launch {
                                    dragOffset.animateTo(
                                        0f,
                                        animationSpec = spring(
                                            dampingRatio = 0.6f,
                                            stiffness = 400f
                                        )
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                dragOffset.animateTo(
                                    0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.6f,
                                        stiffness = 400f
                                    )
                                )
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!confirmed && maxDragPx > 0f) {
                                coroutineScope.launch {
                                    dragOffset.snapTo(
                                        (dragOffset.value + dragAmount).coerceIn(0f, maxDragPx)
                                    )
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Deslizar para eliminar",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
