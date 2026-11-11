package com.neonsong.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import com.neonsong.app.ui.theme.GlassCircularProgressIndicator as CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.neonsong.app.ui.theme.GlassIconActionButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.neonsong.app.ui.theme.GlassOutlinedActionButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import com.neonsong.app.ui.theme.GlassTextActionButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neonsong.app.R
import com.neonsong.app.data.addon.AddonHealth
import com.neonsong.app.data.plugin.InstalledProviderModule
import com.neonsong.app.ui.common.ExpressiveGroup
import com.neonsong.app.ui.common.GroupIconBadge
import com.neonsong.app.ui.common.GroupPosition
import com.neonsong.app.ui.common.groupShape
import com.neonsong.app.ui.theme.LocalIsDarkTheme

/**
 * Settings -> Provider Modules & Addons Redesign.
 *
 * Implements:
 * 1. Song-Reactive Liquid Glass Scaffold.
 * 2. 3D Glass Bento Hero Card for personal / community streaming Addon.
 * 3. Continuous sculpted `ExpressiveGroup` list for local provider modules (.lwp packages).
 * 4. Liquid Glass Addon configuration and editing dialogs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModulesScreen(
    onBack: () -> Unit = {},
    viewModel: ModulesViewModel = hiltViewModel(),
) {
    val modules by viewModel.modules.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val preferModules by viewModel.preferModules.collectAsStateWithLifecycle()
    val addonUrl by viewModel.addonUrl.collectAsStateWithLifecycle()
    val addonName by viewModel.addonName.collectAsStateWithLifecycle()
    val addonSigningKey by viewModel.addonSigningKey.collectAsStateWithLifecycle()
    val addonEnabled by viewModel.addonEnabled.collectAsStateWithLifecycle()
    val addonHealth by viewModel.addonHealth.collectAsStateWithLifecycle()
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val listState = rememberLazyListState()

    var showAddonDialog by remember { mutableStateOf(false) }
    var addonInputText by remember { mutableStateOf("") }
    var addonKeyInputText by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.install(uri)
    }

    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            viewModel.clearNotice()
        }
    }

    if (showAddonDialog) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showAddonDialog = false },
            containerColor = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f) else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.98f),
            shape = RoundedCornerShape(26.dp),
            title = {
                Text(
                    text = if (addonUrl.isNullOrBlank()) "Add Streaming Addon" else "Edit Streaming Addon",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Column {
                    Text(
                        text = "Lossless FLAC and Hi-Res streaming requires a personal Addon URL or self-hosted gateway. Enter your custom gateway URL below:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = addonInputText,
                        onValueChange = { addonInputText = it },
                        label = { Text("Addon URL") },
                        placeholder = { Text("https://your-gateway.workers.dev/a/...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = addonKeyInputText,
                        onValueChange = { addonKeyInputText = it },
                        label = { Text("Signing Key / Client Secret (Optional)") },
                        placeholder = { Text("Leave blank if no signing key is required") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val toSave = addonInputText.trim()
                        val toSaveKey = addonKeyInputText.trim().takeIf { it.isNotBlank() }
                        if (toSave.isNotBlank()) {
                            viewModel.saveAddon(toSave, toSaveKey)
                            showAddonDialog = false
                        }
                    },
                ) {
                    Text(
                        text = "Connect",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddonDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }

    SettingsGlassScaffold(
        title = stringResource(R.string.settings_modules_title),
        subtitle = "Streaming providers and add-ons",
        onBack = onBack,
        listState = listState,
    ) { contentPadding, _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // --- Section 1: HTTP Addon Bento Card ---
                item(key = "addon_bento_card") {
                    val tertiary = MaterialTheme.colorScheme.tertiary
                    val heroBorder = remember(isDark, primary, tertiary) {
                        Brush.linearGradient(
                            listOf(
                                primary.copy(alpha = 0.65f),
                                Color.White.copy(alpha = 0.25f),
                                tertiary.copy(alpha = 0.45f),
                            ),
                        )
                    }

                    if (!addonUrl.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(26.dp),
                            color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.90f),
                            border = BorderStroke(1.2.dp, heroBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                // Top specular rim
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.2.dp)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    Color.White.copy(alpha = if (isDark) 0.55f else 0.75f),
                                                    primary.copy(alpha = 0.45f),
                                                    Color.Transparent,
                                                ),
                                            ),
                                        ),
                                )

                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        GroupIconBadge(
                                            icon = Icons.Filled.Cloud,
                                            container = if (addonHealth is AddonHealth.Unreachable) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                                            tint = if (addonHealth is AddonHealth.Unreachable) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                                            size = 46.dp,
                                            iconSize = 22.dp,
                                        )
                                        Spacer(Modifier.width(14.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                text = addonName?.ifBlank { "HTTP Addon" } ?: "HTTP Addon",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            val masked = addonUrl?.let { url ->
                                                if (url.length > 36) url.take(24) + "..." + url.takeLast(8) else url
                                            }.orEmpty()
                                            Text(
                                                text = masked,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            val keyLabel = if (!addonSigningKey.isNullOrBlank()) "Signing Key: Custom configured" else "Signing Key: None (Unsigned)"
                                            Text(
                                                text = keyLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (!addonSigningKey.isNullOrBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            )
                                        }
                                        Switch(
                                            checked = addonEnabled,
                                            onCheckedChange = viewModel::setAddonEnabled,
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            ),
                                        )
                                    }

                                    Spacer(Modifier.height(12.dp))

                                    val statusText = when (val h = addonHealth) {
                                        is AddonHealth.Ok -> h.info ?: "Connected • Operating normally"
                                        is AddonHealth.Unreachable -> "Unreachable: ${h.reason}"
                                        is AddonHealth.Rejected -> "Rejected: ${h.reason}"
                                        null -> if (addonEnabled) "Enabled" else "Disabled"
                                    }
                                    val statusColor = when (addonHealth) {
                                        is AddonHealth.Ok -> MaterialTheme.colorScheme.primary
                                        is AddonHealth.Unreachable, is AddonHealth.Rejected -> MaterialTheme.colorScheme.error
                                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = statusColor.copy(alpha = 0.14f),
                                        ) {
                                            Text(
                                                text = statusText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = statusColor,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { viewModel.testAddon() },
                                                enabled = !busy,
                                            ) {
                                                Icon(Icons.Filled.Refresh, contentDescription = "Test connection")
                                            }
                                            IconButton(
                                                onClick = {
                                                    addonInputText = addonUrl.orEmpty()
                                                    addonKeyInputText = addonSigningKey.orEmpty()
                                                    showAddonDialog = true
                                                },
                                            ) {
                                                Icon(Icons.Filled.Edit, contentDescription = "Edit Addon")
                                            }
                                            IconButton(
                                                onClick = { viewModel.removeAddon() },
                                            ) {
                                                Icon(
                                                    Icons.Filled.Delete,
                                                    contentDescription = "Remove Addon",
                                                    tint = MaterialTheme.colorScheme.error,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            onClick = {
                                addonInputText = ""
                                addonKeyInputText = ""
                                showAddonDialog = true
                            },
                            shape = RoundedCornerShape(26.dp),
                            color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.90f),
                            border = BorderStroke(1.2.dp, heroBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GroupIconBadge(
                                    icon = Icons.Filled.Cloud,
                                    container = MaterialTheme.colorScheme.primaryContainer,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    size = 46.dp,
                                    iconSize = 22.dp,
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = "Add Streaming Addon",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "Connect to personal or community Addon streaming services",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // --- Section 2: Local .lwp Provider Packages ---
                item(key = "local_modules_header_section") {
                    SettingsGlassSection(
                        title = "Local Provider Modules (.lwp)",
                        subtitle = "Installed plugins resolving lossless metadata and playback URLs",
                    ) {
                        val shape = RoundedCornerShape(18.dp)
                        val rowBorder = remember(isDark, primary) {
                            Brush.verticalGradient(
                                listOf(
                                    (if (isDark) Color.White else primary).copy(alpha = if (isDark) 0.12f else 0.10f),
                                    Color.Transparent,
                                ),
                            )
                        }

                        // Prefer Modules Switch Row
                        Surface(
                            shape = shape,
                            color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f),
                            border = BorderStroke(0.8.dp, rowBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_modules_query_title),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_modules_query_sub),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = preferModules,
                                    onCheckedChange = viewModel::setPreferModules,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    ),
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Add .lwp file action button
                        Surface(
                            onClick = { if (!busy) picker.launch(arrayOf("*/*")) },
                            shape = shape,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if (isDark) 0.45f else 0.75f),
                            border = BorderStroke(0.8.dp, rowBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (busy) {
                                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                                } else {
                                    GroupIconBadge(
                                        icon = Icons.Filled.Add,
                                        container = MaterialTheme.colorScheme.secondary,
                                        tint = MaterialTheme.colorScheme.onSecondary,
                                        size = 38.dp,
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.settings_modules_add),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = stringResource(R.string.settings_modules_add_sub),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                // Modules list or empty state
                if (modules.isEmpty()) {
                    item(key = "modules_empty_card") {
                        SettingsGlassSection {
                            Text(
                                text = stringResource(R.string.settings_modules_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                } else {
                    item(key = "installed_modules_list") {
                        SettingsGlassSection(
                            title = "Installed Packages (${modules.size})",
                        ) {
                            ExpressiveGroup(rowCount = modules.size) { index, position ->
                                val module = modules[index]
                                ModuleRow(
                                    module = module,
                                    position = position,
                                    onToggle = { viewModel.setEnabled(module.manifest.id, it) },
                                    onRemove = { viewModel.remove(module.manifest.id) },
                                )
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp),
            )
        }
    }
}

@Composable
private fun ModuleRow(
    module: InstalledProviderModule,
    position: GroupPosition,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    val manifest = module.manifest
    val isDark = LocalIsDarkTheme.current
    val primary = MaterialTheme.colorScheme.primary
    val shape = groupShape(position)

    val rowBorder = remember(isDark, primary) {
        Brush.verticalGradient(
            listOf(
                (if (isDark) Color.White else primary).copy(alpha = if (isDark) 0.12f else 0.10f),
                Color.Transparent,
            ),
        )
    }

    Surface(
        shape = shape,
        color = if (module.enabled) {
            if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f) else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = if (isDark) 0.35f else 0.50f)
        },
        border = BorderStroke(0.8.dp, rowBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupIconBadge(
                icon = Icons.Filled.Extension,
                container = if (module.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                tint = if (module.enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                size = 42.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = manifest.name.ifBlank { manifest.id },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${manifest.version.ifBlank { "v1.0" }} • ${manifest.capabilities.take(3).joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = module.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete module",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
