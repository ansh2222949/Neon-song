package com.neonsong.app.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.neonsong.app.R

/**
 * Single source of truth for the app's brand identity (logo, icon, app name).
 * Changing [NeonBrand.AppIconRes] or using [NeonAppLogo] updates the brand icon
 * consistently across the entire application (Settings, Auth, About, Navigation, LaunchGate).
 */
object NeonBrand {
    const val AppName = "Neon Song"

    @DrawableRes
    var AppIconRes: Int = R.drawable.beru_icon_3d

    @DrawableRes
    val LauncherIconRes: Int = R.drawable.ic_launcher_beru_foreground

    @DrawableRes
    val NotificationIconRes: Int = R.drawable.ic_launcher_logo
}

/**
 * Centralized, unified app logo component for Neon Song.
 */
@Composable
fun NeonAppLogo(
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    shape: Shape = CircleShape,
    contentDescription: String = NeonBrand.AppName,
    contentScale: ContentScale = ContentScale.Crop,
) {
    Image(
        painter = painterResource(NeonBrand.AppIconRes),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
            .size(size)
            .clip(shape),
    )
}
