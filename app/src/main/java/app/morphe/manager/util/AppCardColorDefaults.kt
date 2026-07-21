/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-manager
 */

package app.morphe.manager.util

import androidx.compose.ui.graphics.Color

enum class AppCardColorMode {
    DEFAULT,
    GRADIENT,
    SOLID
}

enum class AppCardColorStop {
    START,
    MIDDLE,
    END,
    SOLID
}

object AppCardColorDefaults {
    val defaultGradientColors: List<Color>
        get() = KnownApps.DEFAULT_COLORS

    val defaultSolidColor: Color
        get() = defaultGradientColors.firstOrNull() ?: Color.White

    fun colorsOrNull(
        mode: AppCardColorMode,
        startHex: String,
        middleHex: String,
        endHex: String,
        solidHex: String
    ): List<Color>? = when (mode) {
        AppCardColorMode.DEFAULT -> null
        AppCardColorMode.GRADIENT -> gradientColors(startHex, middleHex, endHex)
        AppCardColorMode.SOLID -> solidColors(solidHex)
    }

    fun previewColors(
        mode: AppCardColorMode,
        startHex: String,
        middleHex: String,
        endHex: String,
        solidHex: String
    ): List<Color> = colorsOrNull(
        mode = mode,
        startHex = startHex,
        middleHex = middleHex,
        endHex = endHex,
        solidHex = solidHex
    ) ?: defaultGradientColors

    fun gradientColors(
        startHex: String,
        middleHex: String,
        endHex: String
    ): List<Color> {
        val fallbackColors = defaultGradientColors
        return listOf(
            startHex.toColorOrNull() ?: fallbackColors.getOrElse(0) { Color.White },
            middleHex.toColorOrNull() ?: fallbackColors.getOrElse(1) {
                fallbackColors.firstOrNull() ?: Color.White
            },
            endHex.toColorOrNull() ?: fallbackColors.getOrElse(2) {
                fallbackColors.lastOrNull() ?: Color.White
            }
        )
    }

    fun solidColors(colorHex: String): List<Color> {
        val color = colorHex.toColorOrNull() ?: defaultSolidColor
        return listOf(color, color, color)
    }
}
