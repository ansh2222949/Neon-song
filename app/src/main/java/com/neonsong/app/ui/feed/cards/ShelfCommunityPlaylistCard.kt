package com.neonsong.app.ui.feed.cards

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import com.neonsong.app.ui.theme.GlassIconActionButton as IconButton
import com.neonsong.app.ui.theme.LiquidGlassIconButton
import com.neonsong.app.ui.theme.SquircleShape
import com.neonsong.app.ui.theme.liquidGlassSurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.neonsong.app.data.music.YouTubeMusicTrack

/** Fixed slots keep the carousel still while the one-page preview arrives. */
@Composable
fun ShelfCommunityPlaylistCard(
    title: String,
    subtitle: String,
    collageUrls: List<String>,
    songs: List<YouTubeMusicTrack>,
    artworkUrl: String?,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onSongClick: (YouTubeMusicTrack) -> Unit,
    modifier: Modifier = Modifier,
    onSongMenuClick: ((YouTubeMusicTrack) -> Unit)? = null,
    cardWidth: Dp = 330.dp,
    isLoading: Boolean = false,
    previewFailed: Boolean = false,
    songPlayCounts: Map<String, String> = emptyMap(),
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val rowHeight = 56.dp * fontScale
    val cardShape = remember { SquircleShape(24.dp) }

    Box(
        modifier = modifier
            .width(cardWidth)
            .liquidGlassSurface(
                shape = cardShape,
                borderWidth = 0.9.dp,
                alphaScale = 1.05f,
            ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Column(
                Modifier.fillMaxWidth().cardPressable(onClick = onClick)
                    .semantics { contentDescription = "Open playlist $title" },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CollageCover(collageUrls, artworkUrl, Modifier.size(cardWidth * 0.37f))
                Spacer(Modifier.height(14.dp))
            }
            Row(Modifier.fillMaxWidth().height(48.dp * fontScale), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).cardPressable(onClick = onClick)
                        .semantics { contentDescription = "Open playlist $title" },
                )
                Spacer(Modifier.width(8.dp))
                LiquidGlassIconButton(
                    onClick = onPlayClick,
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    accentTint = MaterialTheme.colorScheme.primary,
                    contentDescription = "Play playlist $title",
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().height(22.dp * fontScale).cardPressable(onClick = onClick),
            )
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().height(rowHeight * 3 + 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    isLoading -> CommunityLoadingRows(rowHeight)
                    !previewFailed -> repeat(3) { index ->
                        val song = songs.getOrNull(index)
                        if (song == null) Spacer(Modifier.height(rowHeight)) else CommunitySongRow(
                            song, songPlayCounts[song.videoId], rowHeight,
                            onClick = { onSongClick(song) },
                            onMenuClick = onSongMenuClick?.let { { it(song) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollageCover(urls: List<String>, fallback: String?, modifier: Modifier) {
    val covers = urls.filter(String::isNotBlank).distinct().take(4)
    if (covers.isEmpty()) {
        PreviewArtwork(fallback, Icons.AutoMirrored.Filled.QueueMusic,
            modifier.clip(SquircleShape(CardTokens.coverCornerRadius)))
        return
    }
    val tiles = List(4) { index ->
        covers.getOrNull(index) ?: covers.getOrNull(index % covers.size.coerceAtLeast(1)) ?: fallback
    }
    Column(modifier.clip(SquircleShape(CardTokens.coverCornerRadius))) {
        repeat(2) { row ->
            Row(Modifier.weight(1f)) {
                repeat(2) { column ->
                    PreviewArtwork(tiles[row * 2 + column], Icons.AutoMirrored.Filled.QueueMusic,
                        Modifier.weight(1f).fillMaxSize(), fallback)
                }
            }
        }
    }
}

/** Supplied YouTube art only; preview cards do not need additional artwork-provider requests. */
@Composable
internal fun PreviewArtwork(url: String?, fallbackIcon: ImageVector, modifier: Modifier, fallbackUrl: String? = null) {
    val context = LocalContext.current
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Icon(fallbackIcon, null, Modifier.fillMaxSize(0.4f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!fallbackUrl.isNullOrBlank() && fallbackUrl != url) {
            AsyncImage(fallbackUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        AsyncImage(
            model = ImageRequest.Builder(context).data(url).crossfade(180).build(),
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun CommunitySongRow(song: YouTubeMusicTrack, plays: String?, height: Dp, onClick: () -> Unit, onMenuClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().height(height).clip(SquircleShape(12.dp))
            .cardPressable(onClick = onClick).semantics { contentDescription = "Play ${song.title}, ${song.artist}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewArtwork(song.artworkUrl, Icons.Filled.MusicNote,
            Modifier.size(44.dp).clip(SquircleShape(CardTokens.thumbCornerRadius)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(song.artist.takeIf(String::isNotBlank), plays).joinToString(" • "),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onMenuClick != null) IconButton(onClick = onMenuClick, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.MoreVert, "More options for ${song.title}", Modifier.size(21.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CommunityLoadingRows(height: Dp) {
    val transition = rememberInfiniteTransition(label = "playlist preview shimmer")
    val phase = transition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "shimmer phase")
    val base = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    repeat(3) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(height)
                .drawWithCache {
                    val sheen = Brush.linearGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.08f), Color.Transparent),
                        start = Offset(0f, 0f),
                        end = Offset(size.width * 0.55f, size.height),
                    )
                    onDrawWithContent {
                        drawContent()
                        val dx = size.width * (phase.value * 2f - 0.8f)
                        translate(left = dx) {
                            drawRect(brush = sheen)
                        }
                    }
                }
                .semantics { contentDescription = "Loading playlist preview" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).clip(SquircleShape(CardTokens.thumbCornerRadius)).background(base))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth(0.8f).height(13.dp).clip(CircleShape).background(base))
                Box(Modifier.fillMaxWidth(0.6f).height(11.dp).clip(CircleShape).background(base))
            }
        }
    }
}
