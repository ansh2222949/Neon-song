package com.neonsong.app.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import com.neonsong.app.ui.theme.drawOutline
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.liquidGlassSurface
import com.neonsong.app.util.LocalHapticManager

enum class GroupPosition { SINGLE, TOP, MIDDLE, BOTTOM }

val GroupOuterRadius = 22.dp
val GroupInnerRadius = 6.dp
val GroupGap = 4.dp
private val GroupIconBadgeShape = SquircleShape(14.dp)

fun groupShape(
    position: GroupPosition,
    outerRadius: Dp = GroupOuterRadius,
    innerRadius: Dp = GroupInnerRadius,
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

fun groupPositionFor(index: Int, count: Int): GroupPosition = when {
    count <= 1 -> GroupPosition.SINGLE
    index == 0 -> GroupPosition.TOP
    index == count - 1 -> GroupPosition.BOTTOM
    else -> GroupPosition.MIDDLE
}

@Composable
fun ExpressiveGroup(
    rowCount: Int,
    modifier: Modifier = Modifier,
    gap: Dp = GroupGap,
    content: @Composable (index: Int, position: GroupPosition) -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        for (i in 0 until rowCount) {
            content(i, groupPositionFor(i, rowCount))
        }
    }
}

@Composable
fun rememberGroupPressScaleState(interactionSource: MutableInteractionSource): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    androidx.compose.runtime.LaunchedEffect(pressed) {
        if (pressed) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        }
    }
    return animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "groupPressScale",
    )
}

@Composable
fun rememberGroupPressScale(interactionSource: MutableInteractionSource): Float {
    return rememberGroupPressScaleState(interactionSource).value
}

@Composable
fun GroupIconBadge(
    icon: ImageVector,
    container: Color,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(GroupIconBadgeShape)
            .background(container)
            .drawWithCache {
                val outline = GroupIconBadgeShape.createOutline(this.size, layoutDirection, this)
                val rimBrush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.34f), Color.Transparent, Color.Black.copy(alpha = 0.18f)),
                )
                val stroke = Stroke(width = 0.8.dp.toPx())
                onDrawWithContent {
                    drawContent()
                    drawOutline(outline = outline, brush = rimBrush, style = stroke)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun ExpressiveGroupRow(
    icon: ImageVector,
    title: String,
    position: GroupPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scaleState = rememberGroupPressScaleState(interactionSource)
    val titleColor = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val shape = remember(position) { groupShape(position) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = scaleState.value
                scaleX = s
                scaleY = s
            }
            .liquidGlassSurface(
                shape = shape,
                borderWidth = 0.8.dp,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupIconBadge(icon, iconContainer, iconTint)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpressiveGroupTrackRow(
    title: String,
    subtitle: String,
    position: GroupPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scaleState = rememberGroupPressScaleState(interactionSource)
    val haptics = LocalHapticFeedback.current
    val shape = remember(position) { groupShape(position) }
    val playingAccent = ArtworkPaletteCache.activeSongPalette.value?.primary ?: MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = scaleState.value
                scaleX = s
                scaleY = s
            }
            .liquidGlassSurface(
                shape = shape,
                borderWidth = 0.8.dp,
                accentTint = if (isPlaying) playingAccent else Color.Unspecified,
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick?.let {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                },
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                    color = if (isPlaying) playingAccent
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPlaying) playingAccent.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun ExpressiveGroupToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: GroupPosition,
    modifier: Modifier = Modifier,
    iconContainer: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val hapticManager = LocalHapticManager.current
    ExpressiveGroupRow(
        icon = icon,
        title = title,
        subtitle = subtitle,
        position = position,
        onClick = {
            val next = !checked
            hapticManager.toggle(next)
            onCheckedChange(next)
        },
        iconContainer = iconContainer,
        iconTint = iconTint,
        modifier = modifier,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = { next ->
                    hapticManager.toggle(next)
                    onCheckedChange(next)
                },
                thumbContent = if (checked) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                } else null,
            )
        },
    )
}

@Composable
fun ExpressiveGroupSelectRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    position: GroupPosition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeContainer: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    badgeTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scaleState = rememberGroupPressScaleState(interactionSource)
    val shape = remember(position) { groupShape(position) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = scaleState.value
                scaleX = s
                scaleY = s
            }
            .liquidGlassSurface(
                shape = shape,
                borderWidth = 0.8.dp,
                accentTint = if (selected) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
            ),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingContent != null) {
                leadingContent()
            } else {
                GroupIconBadge(
                    icon = icon,
                    container = if (selected) MaterialTheme.colorScheme.primary else badgeContainer,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary else badgeTint,
                    size = 48.dp,
                    iconSize = 24.dp,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
