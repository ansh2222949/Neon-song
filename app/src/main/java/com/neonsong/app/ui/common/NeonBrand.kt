package com.neonsong.app.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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
 * Backed by [mutableIntStateOf] so changing [NeonBrand.AppIconRes] at runtime
 * automatically recomposes [NeonAppLogo] across the entire application.
 */
object NeonBrand {
    const val AppName = "Neon Song"
    const val KEY_BERU_3D = "beru_3d"
    const val KEY_MONOCHROME_GLASS = "monochrome_glass"

    @get:DrawableRes
    @setparam:DrawableRes
    var AppIconRes: Int by mutableIntStateOf(R.drawable.beru_icon_3d)

    @DrawableRes
    fun resForKey(key: String?): Int = when (key) {
        KEY_MONOCHROME_GLASS -> R.drawable.ic_launcher_beru_foreground
        else -> R.drawable.beru_icon_3d
    }

    fun keyForRes(@DrawableRes resId: Int): String = when (resId) {
        R.drawable.ic_launcher_beru_foreground -> KEY_MONOCHROME_GLASS
        else -> KEY_BERU_3D
    }

    fun applyKey(key: String?) {
        val target = resForKey(key)
        if (AppIconRes != target) {
            AppIconRes = target
        }
    }

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
