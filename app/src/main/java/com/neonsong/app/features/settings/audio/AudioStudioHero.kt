package com.neonsong.app.features.settings.audio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neonsong.app.core.designsystem.components.StatusPill

@Composable
fun AudioStudioHero(
    quality: String,
    bitPerfectEnabled: Boolean,
    autoDjEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val shape = RoundedCornerShape(28.dp)

    val isDark = com.neonsong.app.ui.theme.LocalIsDarkTheme.current
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) {
                Color(0xFF16161A).copy(alpha = 0.88f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        ),
        border = BorderStroke(
            width = 1.2.dp,
            brush = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = if (isDark) 0.42f else 0.80f),
                    primary.copy(alpha = 0.45f),
                    secondary.copy(alpha = 0.25f),
                ),
            ),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            primary.copy(alpha = 0.20f),
                            secondary.copy(alpha = 0.10f),
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.18f),
                        ),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = primary.copy(alpha = 0.20f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.30f)),
                        modifier = Modifier.size(52.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Audio Studio",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "$quality streaming",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(
                        label = if (bitPerfectEnabled) "Bit-perfect on" else "Bit-perfect off",
                        tint = if (bitPerfectEnabled) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatusPill(
                        label = if (autoDjEnabled) "Auto DJ on" else "Auto DJ off",
                        tint = if (autoDjEnabled) secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

