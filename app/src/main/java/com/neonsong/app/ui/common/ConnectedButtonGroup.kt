package com.neonsong.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neonsong.app.ui.theme.LocalIsDarkTheme
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.liquidGlassSurface

data class ConnectedButtonItem(
    val label: String,
    val icon: ImageVector? = null,
)

/**
 * Material 3 Expressive Connected Button Group with sliding Liquid Glass lens indicator.
 */
@Composable
fun ConnectedButtonGroup(
    items: List<ConnectedButtonItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val outerShape = remember { SquircleShape(20.dp) }
    val innerShape = remember { SquircleShape(16.dp) }

    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.coerceIn(0, items.lastIndex).toFloat(),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 460f),
        label = "connectedSlideIndex",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .liquidGlassSurface(
                shape = outerShape,
                borderWidth = 0.85.dp,
                alphaScale = 0.92f,
            )
            .padding(4.dp),
    ) {
        val slotWidth = maxWidth / items.size
        val slotWidthPx = with(density) { slotWidth.toPx() }

        // Smooth sliding glass lens indicator
        Box(
            modifier = Modifier
                .width(slotWidth)
                .defaultMinSize(minHeight = 40.dp)
                .matchParentSize()
        ) {
            Box(
                modifier = Modifier
                    .width(slotWidth)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationX = animatedIndex * slotWidthPx
                    }
                    .clip(innerShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                primary.copy(alpha = if (isDark) 0.30f else 0.20f),
                                primary.copy(alpha = if (isDark) 0.14f else 0.10f),
                            ),
                        ),
                    )
                    .border(
                        width = 0.85.dp,
                        color = primary.copy(alpha = 0.48f),
                        shape = innerShape,
                    ),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (isPressed) 0.94f else 1f,
                    animationSpec = ExpressiveMotion.spatialFast(),
                    label = "connectedItemScale_$index",
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = ExpressiveMotion.effectsDefault(),
                    label = "connectedItemFg_$index",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 40.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(innerShape)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(index)
                            },
                        )
                        .padding(horizontal = 6.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        if (item.icon != null) {
                            Icon(item.icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = contentColor)
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = contentColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
