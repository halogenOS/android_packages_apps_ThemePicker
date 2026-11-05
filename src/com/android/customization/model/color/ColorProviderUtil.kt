/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.customization.model.color

import android.content.Context
import android.content.res.ColorStateList
import android.content.theming.ThemeStyle
import androidx.annotation.ColorInt
import androidx.annotation.StringDef
import androidx.core.graphics.ColorUtils.setAlphaComponent
import com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_COLOR
import com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_SYSTEM_PALETTE
import com.android.customization.model.color.ColorUtils.toColorString
import com.android.customization.picker.color.shared.model.ColorType
import com.android.systemui.monet.ColorScheme
import com.android.themepicker.R

object ColorProviderUtil {
    private const val ALPHA_MASK = 0xFF

    /**
     * Extra setting indicating the source of the color overlays (it can be one of
     * COLOR_SOURCE_PRESET, COLOR_SOURCE_HOME or COLOR_SOURCE_LOCK)
     */
    const val OVERLAY_COLOR_SOURCE: String = "android.theme.customization.color_source"

    /** Extra setting indicating the style of the color overlays (it can be one of [ThemeStyle]). */
    const val OVERLAY_THEME_STYLE: String = "android.theme.customization.theme_style"

    /** Users selected color option, its value starts from 1 (which means first option). */
    const val OVERLAY_COLOR_INDEX: String = "android.theme.customization.color_index"

    /**
     * Users selected color from both home and lock screen. Example value: 0 means home or lock
     * screen, 1 means both.
     */
    const val OVERLAY_COLOR_BOTH: String = "android.theme.customization.color_both"

    const val COLOR_SOURCE_PRESET = "preset"
    const val COLOR_SOURCE_HOME = "home_wallpaper"
    const val COLOR_SOURCE_LOCK = "lock_wallpaper"

    @StringDef(COLOR_SOURCE_PRESET, COLOR_SOURCE_HOME, COLOR_SOURCE_LOCK)
    annotation class ColorSource

    /**
     * Builds a [ColorOptionImpl] for a wallpaper-based color with a specified color and style. Only
     * use this when Theme Service cannot be used. Otherwise use
     * [ColorOptionImpl.buildSimplifiedSeedOption].
     *
     * @param isDefault Whether the colorInt provided is the primary seed of the WallpaperColors it
     *   is generated from.
     * @param isColorPickerUpdateEnabled Whether the Color Picker update flag is enabled. When the
     *   flag is enabled, the color option preview is generated differently.
     * @param isThemeServiceEnabled Whether the Theme Service flag is enabled. It may be the case
     *   that the Theme Service flag is on, but Theme Service is not in use in the
     *   ColorPickerRepositoryImpl. When the Theme Service flag is enabled, the system palette will
     *   always be set in the package. Otherwise, the system palette is not set for the default
     *   seed.
     */
    fun buildBundle(
        context: Context,
        colorInt: Int,
        index: Int,
        @ThemeStyle.Type style: Int,
        isDefault: Boolean,
        isColorPickerUpdateEnabled: Boolean,
        isThemeServiceEnabled: Boolean,
    ): ColorOptionImpl {
        val lightColorScheme = ColorScheme(colorInt, /* darkTheme= */ false, style)
        val darkColorScheme = ColorScheme(colorInt, /* darkTheme= */ true, style)
        val builder = ColorOptionImpl.Builder()
        builder.source = COLOR_SOURCE_HOME
        builder.style = style
        builder.seedColor = colorInt
        builder.lightColors =
            getColorPreview(
                colorScheme = lightColorScheme,
                colorSource = builder.source,
                darkTheme = false,
                isColorPickerUpdateEnabled = isColorPickerUpdateEnabled,
            )
        builder.darkColors =
            getColorPreview(
                colorScheme = darkColorScheme,
                colorSource = builder.source,
                darkTheme = true,
                isColorPickerUpdateEnabled = isColorPickerUpdateEnabled,
            )
        builder.isColorPickerUpdateEnabled = isColorPickerUpdateEnabled
        builder.isThemeServiceEnabled = isThemeServiceEnabled
        // Always set system palette color if theme service is enabled
        if (!isDefault || isThemeServiceEnabled) {
            builder.addOverlayPackage(OVERLAY_CATEGORY_SYSTEM_PALETTE, toColorString(colorInt))
        }
        builder.title =
            when (style) {
                ThemeStyle.TONAL_SPOT ->
                    context.getString(R.string.content_description_dynamic_color_option)
                ThemeStyle.SPRITZ ->
                    context.getString(R.string.content_description_neutral_color_option)
                ThemeStyle.VIBRANT ->
                    context.getString(R.string.content_description_vibrant_color_option)
                ThemeStyle.EXPRESSIVE ->
                    context.getString(R.string.content_description_expressive_color_option)
                else -> context.getString(R.string.content_description_dynamic_color_option)
            }
        builder.index = index
        builder.isDefault = isDefault
        builder.type = ColorType.WALLPAPER_COLOR
        return builder.build()
    }

    /**
     * Builds the option that applies [seedColor] in [style]. With the theme service it is a
     * simplified seed option, otherwise a preset with the overlay packages of the seed.
     */
    fun buildSeedOption(
        title: String,
        @ColorInt seedColor: Int,
        @ThemeStyle.Type style: Int,
        type: ColorType,
        index: Int,
        isUsingThemeService: Boolean,
        isColorPickerUpdateEnabled: Boolean,
    ): ColorOptionImpl =
        if (isUsingThemeService) {
            ColorOptionImpl.buildSimplifiedSeedOption(
                title = title,
                source = COLOR_SOURCE_PRESET,
                seedColor = seedColor,
                defaultStyle = style,
                type = type,
            )
        } else {
            buildPreset(
                title = title,
                color = seedColor,
                index = index,
                style = style,
                type = type,
                isColorPickerUpdateEnabled = isColorPickerUpdateEnabled,
            )
        }

    /**
     * Builds a [ColorOptionImpl] for a preset color with a specified color and style Only use this
     * when Theme Service cannot be used. Otherwise use [ColorOptionImpl.buildSimplifiedSeedOption]
     */
    fun buildPreset(
        title: String,
        color: Int,
        index: Int,
        @ThemeStyle.Type style: Int? = null,
        type: ColorType = ColorType.PRESET_COLOR,
        isColorPickerUpdateEnabled: Boolean,
    ): ColorOptionImpl {
        val builder = ColorOptionImpl.Builder()
        builder.title = title
        builder.index = index
        builder.source = COLOR_SOURCE_PRESET
        builder.type = type
        var darkColorScheme = ColorScheme(color, /* darkTheme= */ true)
        var lightColorScheme = ColorScheme(color, /* darkTheme= */ false)
        val lightColor = lightColorScheme.accentColor
        val darkColor = darkColorScheme.accentColor
        var lightColors = intArrayOf(lightColor, lightColor, lightColor, lightColor)
        var darkColors = intArrayOf(darkColor, darkColor, darkColor, darkColor)
        builder.seedColor = color
        builder.addOverlayPackage(OVERLAY_CATEGORY_COLOR, toColorString(color))
        builder.addOverlayPackage(OVERLAY_CATEGORY_SYSTEM_PALETTE, toColorString(color))
        if (style != null) {
            builder.style = style

            lightColorScheme = ColorScheme(color, /* darkTheme= */ false, style)
            darkColorScheme = ColorScheme(color, /* darkTheme= */ true, style)

            lightColors =
                getColorPreview(
                    colorScheme = lightColorScheme,
                    colorSource = builder.source,
                    darkTheme = false,
                )
            darkColors =
                getColorPreview(
                    colorScheme = darkColorScheme,
                    colorSource = builder.source,
                    darkTheme = true,
                )
        }
        builder.lightColors = lightColors
        builder.darkColors = darkColors
        builder.isColorPickerUpdateEnabled = isColorPickerUpdateEnabled
        return builder.build()
    }

    fun getColorPreview(
        colorScheme: ColorScheme,
        colorSource: String?,
        darkTheme: Boolean,
        isColorPickerUpdateEnabled: Boolean = false,
    ): IntArray {
        return if (
            !isColorPickerUpdateEnabled && colorSource == COLOR_SOURCE_HOME ||
                colorSource == COLOR_SOURCE_LOCK
        ) {
            if (darkTheme) {
                getDarkThreeColorPreview(colorScheme)
            } else {
                getLightThreeColorPreview(colorScheme)
            }
        } else if (colorScheme.style == ThemeStyle.MONOCHROMATIC) {
            if (darkTheme) {
                getDarkMonochromePreview(colorScheme)
            } else {
                getLightMonochromePreview(colorScheme)
            }
        } else {
            if (darkTheme) {
                getDarkOneOrTwoColorPreview(colorScheme)
            } else {
                getLightOneOrTwoColorPreview(colorScheme)
            }
        }
    }

    /**
     * Returns the light theme preview of a dynamic ColorScheme based on this order: top left, top
     * right, bottom left, bottom right
     *
     * This color mapping corresponds to GM3 colors: Primary (light), Primary (light), Secondary
     * LStar 85, and Tertiary LStar 70
     */
    @ColorInt
    fun getLightThreeColorPreview(colorScheme: ColorScheme): IntArray {
        return intArrayOf(
            setAlphaComponent(colorScheme.accent1.s600, ALPHA_MASK),
            setAlphaComponent(colorScheme.accent1.s600, ALPHA_MASK),
            ColorStateList.valueOf(colorScheme.accent2.s500).withLStar(85f).colors[0],
            setAlphaComponent(colorScheme.accent3.s300, ALPHA_MASK),
        )
    }

    /**
     * Returns the dark theme preview of a dynamic ColorScheme based on this order: top left, top
     * right, bottom left, bottom right
     *
     * This color mapping corresponds to GM3 colors: Primary (dark), Primary (dark), Secondary LStar
     * 35, and Tertiary LStar 70
     */
    @ColorInt
    fun getDarkThreeColorPreview(colorScheme: ColorScheme): IntArray {
        return intArrayOf(
            setAlphaComponent(colorScheme.accent1.s200, ALPHA_MASK),
            setAlphaComponent(colorScheme.accent1.s200, ALPHA_MASK),
            ColorStateList.valueOf(colorScheme.accent2.s500).withLStar(35f).colors[0],
            setAlphaComponent(colorScheme.accent3.s300, ALPHA_MASK),
        )
    }

    /**
     * Returns the light theme preview of a monochrome ColorScheme based on this order: top left,
     * top right, bottom left, bottom right
     *
     * This color mapping corresponds to GM3 colors: Primary LStar 0, Primary LStar 0, Secondary
     * LStar 85, and Tertiary LStar 70
     */
    @ColorInt
    private fun getLightMonochromePreview(colorScheme: ColorScheme): IntArray {
        return intArrayOf(
            setAlphaComponent(colorScheme.accent1.s1000, ALPHA_MASK),
            setAlphaComponent(colorScheme.accent1.s1000, ALPHA_MASK),
            ColorStateList.valueOf(colorScheme.accent2.s500).withLStar(85f).colors[0],
            setAlphaComponent(colorScheme.accent3.s300, ALPHA_MASK),
        )
    }

    /**
     * Returns the dark theme preview of a monochrome ColorScheme based on this order: top left, top
     * right, bottom left, bottom right
     *
     * This color mapping corresponds to GM3 colors: Primary LStar 99, Primary LStar 99, Secondary
     * LStar 35, and Tertiary LStar 70
     */
    @ColorInt
    private fun getDarkMonochromePreview(colorScheme: ColorScheme): IntArray {
        return intArrayOf(
            setAlphaComponent(colorScheme.accent1.s10, ALPHA_MASK),
            setAlphaComponent(colorScheme.accent1.s10, ALPHA_MASK),
            ColorStateList.valueOf(colorScheme.accent2.s500).withLStar(35f).colors[0],
            setAlphaComponent(colorScheme.accent3.s300, ALPHA_MASK),
        )
    }

    /**
     * Returns the light theme contrast-adjusted preview of a preset ColorScheme, based on this
     * order: top left, top right, bottom left, bottom right
     */
    fun getDarkOneOrTwoColorPreview(colorScheme: ColorScheme): IntArray {
        val colors =
            when (colorScheme.style) {
                ThemeStyle.FRUIT_SALAD ->
                    intArrayOf(colorScheme.accent3.s100, colorScheme.accent1.s200)
                else -> intArrayOf(colorScheme.accent1.s200, colorScheme.accent1.s200)
            }
        return intArrayOf(colors[0], colors[1], colors[0], colors[1])
    }

    /**
     * Returns the light theme contrast-adjusted preview of a preset ColorScheme, based on this
     * order: top left, top right, bottom left, bottom right
     */
    fun getLightOneOrTwoColorPreview(colorScheme: ColorScheme): IntArray {
        val colors =
            when (colorScheme.style) {
                ThemeStyle.FRUIT_SALAD ->
                    intArrayOf(
                        colorScheme.accent3.getAtTone(450f),
                        colorScheme.accent1.getAtTone(550f),
                    )
                else ->
                    intArrayOf(
                        colorScheme.accent1.getAtTone(450f),
                        colorScheme.accent1.getAtTone(450f),
                    )
            }
        return intArrayOf(colors[0], colors[1], colors[0], colors[1])
    }

    @ThemeStyle.Type
    fun getStyleList(isColorPickerUpdateEnabled: Boolean): List<Int> {
        return if (isColorPickerUpdateEnabled) {
            listOf(
                ThemeStyle.SPRITZ,
                ThemeStyle.TONAL_SPOT,
                ThemeStyle.VIBRANT,
                ThemeStyle.EXPRESSIVE,
            )
        } else {
            listOf(
                ThemeStyle.TONAL_SPOT,
                ThemeStyle.SPRITZ,
                ThemeStyle.VIBRANT,
                ThemeStyle.EXPRESSIVE,
            )
        }
    }
}
