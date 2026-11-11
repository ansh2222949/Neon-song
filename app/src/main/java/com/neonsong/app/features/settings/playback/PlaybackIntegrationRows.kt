package com.neonsong.app.features.settings.playback

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.neonsong.app.core.designsystem.components.GroupPosition
import com.neonsong.app.core.designsystem.components.IconBadge
import com.neonsong.app.core.designsystem.components.groupShape
import com.neonsong.app.core.designsystem.components.settingHighlightGlow
import com.neonsong.app.ui.common.ExpressiveLoadingIndicator
import com.neonsong.app.ui.theme.ExpressivePillShape
import com.neonsong.app.util.LocalHapticManager

const val LAST_FM_CREATE_KEY_URL = "https://www.last.fm/api/account/create"

private val CardOuterShape = RoundedCornerShape(22.dp)

fun lastSyncSuffix(lastSyncAtMillis: Long): String {
    if (lastSyncAtMillis <= 0L) return ""
    return " \u2022 synced ${relativeTime(lastSyncAtMillis)}"
}

fun relativeTime(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return "never"
    val delta = System.currentTimeMillis() - timestampMillis
    val minutes = delta / 60_000L
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 60 * 24 -> "${minutes / 60}h ago"
        else -> "${minutes / (60 * 24)}d ago"
    }
}

@Composable
fun YouTubeAccountRow(
    accountName: String,
    channelHandle: String?,
    onDisconnect: () -> Unit,
    position: GroupPosition,
    isHighlighted: Boolean = false,
) {
    val hapticManager = LocalHapticManager.current
    val shape = groupShape(position)
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .settingHighlightGlow(isHighlighted = isHighlighted, shape = shape),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                Icons.Filled.SmartDisplay,
                MaterialTheme.colorScheme.errorContainer,
                MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "YouTube Music Account",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    accountName.ifBlank { "Connected" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!channelHandle.isNullOrBlank()) {
                    Text(
                        channelHandle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = {
                    hapticManager.click()
                    onDisconnect()
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(Icons.Filled.Logout, contentDescription = "Disconnect")
            }
        }
    }
}

@Composable
fun LastFmIntegrationCard(
    isConnected: Boolean,
    username: String,
    avatarUrl: String?,
    connecting: Boolean,
    awaitingApproval: Boolean,
    hasApiKey: Boolean,
    onConnect: () -> Unit,
    onCancel: () -> Unit,
    onDisconnect: () -> Unit,
    onSaveKeys: (String, String) -> Unit,
    onRemoveKey: () -> Unit,
    onOpenCreateKeyPage: () -> Unit,
    isHighlighted: Boolean = false,
) {
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var showKeyForm by remember(hasApiKey) { mutableStateOf(!hasApiKey) }
    var keyInput by remember { mutableStateOf("") }
    var secretInput by remember { mutableStateOf("") }

    Card(
        shape = CardOuterShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.20f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .settingHighlightGlow(isHighlighted = isHighlighted, shape = CardOuterShape),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = if (isConnected) 14.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isConnected) {
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        IconBadge(
                            Icons.Filled.CloudSync,
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                } else {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (isConnected && username.isNotBlank()) username.take(1).uppercase() else "L",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Last.fm",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (isConnected && username.isNotBlank()) username else "Not connected",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!isConnected) {
                        Text(
                            "Optional • Stats use local listening when disconnected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isConnected) {
                    Spacer(Modifier.width(8.dp))
                    FilledTonalIconButton(
                        onClick = { showDisconnectConfirm = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(Icons.Filled.Logout, contentDescription = "Disconnect")
                    }
                }
            }
            if (!isConnected) {
                when {
                    awaitingApproval || connecting -> {
                        ExpressiveLoadingIndicator(
                            message = if (connecting) "Connecting to Last.fm…" else "Waiting for approval in the browser…",
                        )
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = onCancel) { Text("Cancel") }
                    }
                    else -> {
                        Button(
                            onClick = onConnect,
                            enabled = hasApiKey,
                            shape = ExpressivePillShape,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Connect Last.fm") }
                        Text(
                            if (hasApiKey) "Approve in your browser. You can disconnect anytime — Stats keep working locally."
                            else "Add your API key below first, then connect.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!isConnected) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "API key",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                if (hasApiKey) "Your key is saved"
                                else "Required — get one free, then paste it here",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { showKeyForm = !showKeyForm }) {
                            Text(if (showKeyForm) "Hide" else if (hasApiKey) "Change" else "Add key")
                        }
                    }
                    if (showKeyForm) {
                        OutlinedTextField(
                            value = keyInput,
                            onValueChange = { keyInput = it.trim() },
                            label = { Text("API key (32 chars)") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = secretInput,
                            onValueChange = { secretInput = it.trim() },
                            label = { Text("Shared secret") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    onSaveKeys(keyInput, secretInput)
                                    keyInput = ""
                                    secretInput = ""
                                    showKeyForm = false
                                },
                                enabled = keyInput.length >= 16 && secretInput.length >= 16,
                                shape = ExpressivePillShape,
                                modifier = Modifier.weight(1f),
                            ) { Text("Save key") }
                            if (hasApiKey) {
                                OutlinedButton(
                                    onClick = onRemoveKey,
                                    shape = ExpressivePillShape,
                                ) { Text("Remove") }
                            }
                        }
                        TextButton(onClick = onOpenCreateKeyPage) {
                            Text("Get a free key at last.fm/api →")
                        }
                    } else {
                        TextButton(onClick = onOpenCreateKeyPage) {
                            Text("How to get a free key →")
                        }
                    }
                }
            }
        }
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            title = { Text("Disconnect Last.fm?") },
            text = { Text("Global scrobbles pause. Your Stats switch to local listening history — nothing is deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectConfirm = false
                    onDisconnect()
                }) { Text("Disconnect") }
            },
            dismissButton = { TextButton(onClick = { showDisconnectConfirm = false }) { Text("Cancel") } },
        )
    }
}

