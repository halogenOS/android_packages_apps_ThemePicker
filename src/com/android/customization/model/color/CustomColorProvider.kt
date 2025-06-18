/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.customization.model.color

import android.content.Context
import android.graphics.Color
import com.android.customization.model.ResourceConstants
import com.android.customization.model.color.ColorOptionsProvider.COLOR_SOURCE_PRESET
import com.android.customization.picker.color.shared.model.ColorType
import com.android.systemui.monet.ColorScheme
import android.content.theming.ThemeStyle
import com.android.themepicker.R

class CustomColorProvider(private val context: Context) {
    fun getCustomColors(): List<ColorOptionImpl> {
        val colors = mutableListOf<ColorOptionImpl>()

        // Get custom colors from resources
        val colorMap = listOf(
            Pair(R.color.xos_blue, "XOS Blue"),
        )

        // Create color options for each custom color
        colorMap.forEachIndexed { index, (colorRes, name) ->
            val color = context.resources.getColor(colorRes, context.theme)
            val builder = ColorOptionImpl.Builder()
            builder.title = name
            builder.seedColor = color
            builder.source = COLOR_SOURCE_PRESET
            builder.type = ColorType.CUSTOM_COLOR
            builder.style = ThemeStyle.VIBRANT
            builder.index = index + 1

            // Set light and dark theme colors
            val lightColorScheme = ColorScheme(color, /* darkTheme= */ false, ThemeStyle.VIBRANT)
            val darkColorScheme = ColorScheme(color, /* darkTheme= */ true, ThemeStyle.VIBRANT)

            builder.lightColors = getLightColorPreview(lightColorScheme)
            builder.darkColors = getDarkColorPreview(darkColorScheme)

            // Add overlay packages
            builder.addOverlayPackage(ResourceConstants.OVERLAY_CATEGORY_COLOR, toColorString(color))
            builder.addOverlayPackage(ResourceConstants.OVERLAY_CATEGORY_SYSTEM_PALETTE, toColorString(color))

            colors.add(builder.build())
        }

        return colors
    }

    private fun getLightColorPreview(colorScheme: ColorScheme): IntArray {
        return intArrayOf(
            colorScheme.accentColor,
            colorScheme.accentColor,
            colorScheme.accentColor,
            colorScheme.accentColor
        )
    }

    private fun getDarkColorPreview(colorScheme: ColorScheme): IntArray {
        return intArrayOf(
            colorScheme.accentColor,
            colorScheme.accentColor,
            colorScheme.accentColor,
            colorScheme.accentColor
        )
    }

    private fun toColorString(color: Int): String {
        return String.format("#%08X", color)
    }
}