package com.neonsong.app.features.settings.appearance

data class AccentPreset(val name: String, val hex: String)

val ACCENT_PRESETS = listOf(
    AccentPreset("Neon Cyan", "#00E5FF"),
    AccentPreset("Neon Teal", "#18FFFF"),
    AccentPreset("Cyber Lime", "#76FF03"),
    AccentPreset("Electric Violet", "#B388FF"),
    AccentPreset("Hot Pink", "#FF4081"),
    AccentPreset("Sunset Amber", "#FFB300"),
    AccentPreset("Deep Ocean", "#00B0FF"),
)

