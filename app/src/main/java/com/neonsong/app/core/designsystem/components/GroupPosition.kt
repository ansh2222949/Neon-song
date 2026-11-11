package com.neonsong.app.core.designsystem.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * Returns a [RoundedCornerShape] appropriately contoured for the given [GroupPosition].
 */
fun groupShape(
    position: GroupPosition,
    outerRadius: Dp = 12.dp,
    innerRadius: Dp = 0.dp
): RoundedCornerShape = RoundedCornerShape(
    topStart = if (position == GroupPosition.TOP || position == GroupPosition.SINGLE) outerRadius else innerRadius,
    topEnd = if (position == GroupPosition.TOP || position == GroupPosition.SINGLE) outerRadius else innerRadius,
    bottomStart = if (position == GroupPosition.BOTTOM || position == GroupPosition.SINGLE) outerRadius else innerRadius,
    bottomEnd = if (position == GroupPosition.BOTTOM || position == GroupPosition.SINGLE) outerRadius else innerRadius,
)

