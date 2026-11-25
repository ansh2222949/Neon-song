package com.neonsong.app.core.designsystem.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neonsong.app.ui.theme.SquircleShape

/**
 * Geometric position within a grouped list of cards (Apple / iOS grouped table style).
 */
enum class GroupPosition {
    SINGLE,
    TOP,
    MIDDLE,
    BOTTOM
}

/**
 * Returns a [SquircleShape] appropriately contoured for the given [GroupPosition].
 */
fun groupShape(
    position: GroupPosition,
    outerRadius: Dp = 22.dp,
    innerRadius: Dp = 6.dp
): SquircleShape = when (position) {
    GroupPosition.SINGLE -> SquircleShape(outerRadius)
    GroupPosition.TOP -> SquircleShape(
        topStart = outerRadius, topEnd = outerRadius,
        bottomStart = innerRadius, bottomEnd = innerRadius,
    )
    GroupPosition.MIDDLE -> SquircleShape(innerRadius)
    GroupPosition.BOTTOM -> SquircleShape(
        topStart = innerRadius, topEnd = innerRadius,
        bottomStart = outerRadius, bottomEnd = outerRadius,
    )
}

