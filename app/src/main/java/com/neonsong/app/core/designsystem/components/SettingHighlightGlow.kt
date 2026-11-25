package com.neonsong.app.core.designsystem.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.neonsong.app.ui.theme.SquircleShape
import kotlinx.coroutines.delay

fun Modifier.settingHighlightGlow(
    isHighlighted: Boolean,
    shape: Shape = SquircleShape(20.dp),
): Modifier = composed {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val primaryColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            delay(160L)
            bringIntoViewRequester.bringIntoView()
        }
    }

    if (!isHighlighted) {
        return@composed this.bringIntoViewRequester(bringIntoViewRequester)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "settingHighlightGlow")
    val glowProgress by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowProgress",
    )

    this
        .bringIntoViewRequester(bringIntoViewRequester)
        .border(
            width = 2.dp,
            color = primaryColor.copy(alpha = glowProgress),
            shape = shape,
        )
}

