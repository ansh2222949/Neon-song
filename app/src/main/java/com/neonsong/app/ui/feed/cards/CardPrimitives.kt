/*
 * Shared primitives for the native shelf card system (liquid-glass style).
 * Press scale, haptics, crossfade artwork with soft placeholder, info line.
 */
package com.neonsong.app.ui.feed.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neonsong.app.ui.common.ArtworkImage
import com.neonsong.app.ui.common.ExpressiveMotion
import com.neonsong.app.ui.theme.SquircleShape

private val DarkCardRimBorder = BorderStroke(
    0.85.dp,
    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.05f))),
)
private val LightCardRimBorder = BorderStroke(
    0.85.dp,
    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.05f))),
)

/** Modifier that adds the shared press-scale + haptic + click/long-click. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Modifier.cardPressable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    contentDescription: String? = null,
): Modifier {
    val hapticManager = com.neonsong.app.util.LocalHapticManager.current
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scaleState = animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = if (pressed) ExpressiveMotion.FloatPressSpring else ExpressiveMotion.FloatReleaseSpring,
        label = "cardPressScale",
    )
    return this
        .graphicsLayer {
            val s = scaleState.value
            scaleX = s
            scaleY = s
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = {
                hapticManager.click()
                onClick()
            },
            onLongClick = onLongClick?.let {
                {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    it()
                }
            },
        )
}

/** Rounded-corner artwork surface with the app's crossfade loader + fallback. */
@Composable
internal fun CardArtwork(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    fallbackIcon: ImageVector,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CardTokens.coverCornerRadius,
    decodeSizePx: Int? = 320,
) {
    val coverShape = remember(cornerRadius) { SquircleShape(cornerRadius) }
    val isDark = com.neonsong.app.ui.theme.LocalIsDarkTheme.current
    val fallbackPrimary = MaterialTheme.colorScheme.primary
    val shadowPx = with(LocalDensity.current) { (if (isDark) 8.dp else 5.dp).toPx() }
    val spotColor = Color.Black.copy(alpha = if (isDark) 0.42f else 0.18f)

    Surface(
        shape = coverShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (isDark) DarkCardRimBorder else LightCardRimBorder,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier.graphicsLayer {
            shadowElevation = shadowPx
            shape = coverShape
            clip = false
            val accent = com.neonsong.app.ui.common.ArtworkPaletteCache.activeSongPalette.value?.primary
                ?: fallbackPrimary
            ambientShadowColor = accent.copy(alpha = 0.22f)
            spotShadowColor = spotColor
        },
    ) {
        ArtworkImage(
            name = title,
            artist = subtitle,
            embeddedUrl = artworkUrl,
            fallbackIcon = fallbackIcon,
            decodeSizePx = decodeSizePx,
            crossfade = false,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Exactly two text lines per card, both limited to the cover width:
 * Title (1 line, 14sp medium, ellipsis) and Info line (1 line, 12sp muted, ellipsis).
 * Fixed container height ensures cards with short, long, or missing info lines
 * maintain identical total height without shifting or clipping.
 */
@Composable
internal fun CardTextArea(
    title: String,
    infoLine: String? = "",
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(CardTokens.textAreaHeight)
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = CardTokens.titleFontSize,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                lineHeight = CardTokens.titleLineHeight,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!infoLine.isNullOrBlank()) {
            Spacer(Modifier.height(CardTokens.titleToInfoGap))
            Text(
                text = infoLine,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = CardTokens.infoFontSize,
                    lineHeight = CardTokens.infoLineHeight,
                    fontFeatureSettings = "tnum",
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Bold card title, ellipsized. */
@Composable
internal fun CardTitle(text: String, maxLines: Int = 1, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Bold,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/** One-line muted info line (artist, type, plays/views). */
@Composable
internal fun CardInfoLine(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = modifier,
    )
}

private val PlayBadgeBorder = BorderStroke(
    0.85.dp,
    Brush.verticalGradient(
        listOf(Color.White.copy(alpha = 0.50f), Color.White.copy(alpha = 0.14f)),
    ),
)

@Composable
internal fun ShelfPlayBadge(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val bgColor = remember(primary) {
        androidx.compose.ui.graphics.lerp(Color(0xB8101218), primary, 0.34f)
    }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bgColor,
        border = PlayBadgeBorder,
        shadowElevation = 4.dp,
        modifier = modifier.size(34.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
