@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.neonsong.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.neonsong.app.R

/**
 * Google Sans Flex, bundled as a real variable font (res/font/google_sans_flex.ttf)
 * rather than looked up by device font name — every device gets the same
 * look now, not just ones that happen to ship it as a system font.
 *
 * The file exposes 6 axes (checked via fonttools): wght 1-1000 (default
 * 400), wdth 25-151 (default 100), opsz 6-144 (default 18), ROND 0-100,
 * GRAD 0-100, slnt -10-0. Each text style below dials in its own
 * wght/wdth/ROND/opsz combination instead of reusing one instance for the
 * whole type scale - headlines lean heavier, a touch wider, and rounder
 * (a confident, friendly "premium app" mark), body text stays close to the
 * font's own default (readable, unobtrusive), and labels/buttons pick up
 * extra roundness so pill-shaped chips and the text inside them read as
 * one continuous shape language instead of a sharp glyph inside a soft
 * container. Optical size is set per style to that style's own
 * approximate font size, exactly what opsz exists for.
 */
private fun gsFlexFamily(
    width: Float = 100f,
    round: Float = 16f,
    opticalSize: Float = 16f,
    grade: Float = 0f,
): FontFamily = FontFamily(
    listOf(
        FontWeight.Normal to 420,
        FontWeight.Medium to 520,
        FontWeight.SemiBold to 620,
        FontWeight.Bold to 720,
        FontWeight.ExtraBold to 800,
        FontWeight.Black to 860,
    ).map { (fw, wght) ->
        Font(
            R.font.google_sans_flex,
            weight = fw,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(wght),
                FontVariation.width(width),
                FontVariation.Setting("ROND", round),
                FontVariation.Setting("opsz", opticalSize),
                FontVariation.Setting("GRAD", grade),
            ),
        )
    },
)

private val DisplayFont = gsFlexFamily(width = 105f, round = 20f, opticalSize = 36f, grade = 10f)
private val TitleFont = gsFlexFamily(width = 103f, round = 18f, opticalSize = 20f, grade = 5f)
private val BodyFont = gsFlexFamily(width = 100f, round = 12f, opticalSize = 15f, grade = 0f)
private val LabelFont = gsFlexFamily(width = 102f, round = 24f, opticalSize = 13f, grade = 15f)

val NeonSongTypography = Typography(
    displayLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 54.sp, lineHeight = 60.sp, letterSpacing = (-1.4).sp),
    displayMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 42.sp, lineHeight = 48.sp, letterSpacing = (-1.0).sp),
    displaySmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp),

    headlineLarge = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.75).sp),
    headlineMedium = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.55).sp),
    headlineSmall = TextStyle(fontFamily = DisplayFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp),

    titleLarge = TextStyle(fontFamily = TitleFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.35).sp),
    titleMedium = TextStyle(fontFamily = TitleFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp),
    titleSmall = TextStyle(fontFamily = TitleFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = (-0.1).sp),

    bodyLarge = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 15.5.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.05.sp),
    bodySmall = TextStyle(fontFamily = BodyFont, fontWeight = FontWeight.Normal, fontSize = 12.5.sp, lineHeight = 17.sp, letterSpacing = 0.1.sp, fontFeatureSettings = "tnum"),

    labelLarge = TextStyle(fontFamily = LabelFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp, fontFeatureSettings = "tnum"),
    labelMedium = TextStyle(fontFamily = LabelFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.25.sp, fontFeatureSettings = "tnum"),
    labelSmall = TextStyle(fontFamily = LabelFont, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp, fontFeatureSettings = "tnum"),
)

/**
 * The "Use Application Font" toggle's off-state: plain platform default
 * (FontFamily.Default), same sizes/weights as [NeonSongTypography] so
 * turning the toggle off changes ONLY the typeface, not the whole scale.
 */
val SystemTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),
)
