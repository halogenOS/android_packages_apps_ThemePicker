/*
 * Copyright (C) 2023 The Android Open Source Project
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
 *
 */
package com.android.customization.model.color

import android.content.Context
import android.content.theming.ThemeStyle
import android.stats.style.StyleEnums
import android.view.View
import androidx.annotation.ColorInt
import com.android.customization.picker.color.shared.model.ColorType
import com.android.systemui.monet.ColorScheme
import com.android.themepicker.R

/**
 * Represents a color option in the revamped UI, it can be used for both wallpaper and preset colors
 */
open class ColorOptionImpl(
    title: String?,
    private val source: String?,
    seedColor: Int,
    @ThemeStyle.Type style: Int,
    isThemeServiceEnabled: Boolean = false,
    isColorPickerUpdateEnabled: Boolean = false,
    overlayPackages: Map<String, String?> = emptyMap(),
    isDefault: Boolean = false,
    index: Int = -1,
    private val previewInfo: PreviewInfo = PreviewInfo(intArrayOf(), intArrayOf()),
    type: ColorType = ColorType.WALLPAPER_COLOR,
) :
    ColorOption(
        title,
        overlayPackages,
        isDefault,
        seedColor,
        style,
        index,
        isThemeServiceEnabled,
        isColorPickerUpdateEnabled,
        type,
    ) {

    class PreviewInfo(@ColorInt val lightColors: IntArray, @ColorInt val darkColors: IntArray) :
        ColorOption.PreviewInfo {
        @ColorInt
        override fun resolveColors(darkTheme: Boolean): IntArray {
            return if (darkTheme) darkColors else lightColors
        }
    }

    override fun bindThumbnailTile(view: View?) {
        // Do nothing. This function will no longer be used in the Revamped UI
    }

    override fun getLayoutResId(): Int {
        return R.layout.color_option
    }

    override fun getPreviewInfo(): PreviewInfo {
        return previewInfo
    }

    override fun getContentDescription(context: Context): CharSequence? {
        return title
    }

    override fun getSource(): String? {
        return source
    }

    override fun getSourceForLogging(): Int {
        return when (getSource()) {
            ColorProviderUtil.COLOR_SOURCE_PRESET -> StyleEnums.COLOR_SOURCE_PRESET_COLOR
            ColorProviderUtil.COLOR_SOURCE_HOME -> StyleEnums.COLOR_SOURCE_HOME_SCREEN_WALLPAPER
            ColorProviderUtil.COLOR_SOURCE_LOCK -> StyleEnums.COLOR_SOURCE_LOCK_SCREEN_WALLPAPER
            else -> StyleEnums.COLOR_SOURCE_UNSPECIFIED
        }
    }

    override fun getStyleForLogging(): Int = ThemeStyle.toString(style).hashCode()

    companion object {
        /**
         * Use this to build a simplified color seed option for use with Theme Service. It creates
         * an instance of [ColorOptionImpl] for backwards code compatibility.
         *
         * Two color seed options are equivalent if they have the same seed color, source and style,
         * since the picker offers the same seed in several styles.
         */
        // TODO (b/440146498): after fully deprecating ColorCustomizationManager, simplify this
        //  to output a standalone data class without dependence on ColorOptionImpl
        fun buildSimplifiedSeedOption(
            title: String?,
            source: String?,
            seedColor: Int,
            @ThemeStyle.Type defaultStyle: Int,
            type: ColorType = ColorType.WALLPAPER_COLOR,
        ): ColorOptionImpl {
            val lightColors =
                ColorProviderUtil.getColorPreview(
                    colorScheme = ColorScheme(seedColor, /* darkTheme= */ false, defaultStyle),
                    colorSource = source,
                    darkTheme = false,
                    isColorPickerUpdateEnabled = true,
                )
            val darkColors =
                ColorProviderUtil.getColorPreview(
                    colorScheme = ColorScheme(seedColor, /* darkTheme= */ true, defaultStyle),
                    colorSource = source,
                    darkTheme = true,
                    isColorPickerUpdateEnabled = true,
                )
            return object :
                ColorOptionImpl(
                    title = title,
                    source = source,
                    seedColor = seedColor,
                    style = defaultStyle,
                    previewInfo = PreviewInfo(lightColors, darkColors),
                    type = type,
                    isThemeServiceEnabled = true,
                ) {
                override fun isEquivalent(other: ColorOption?): Boolean {
                    return other is ColorOptionImpl &&
                        this.source == other.source &&
                        this.seedColor == other.seedColor &&
                        this.style == other.style
                }
            }
        }
    }

    class Builder {
        var title: String? = null

        @ColorInt var lightColors: IntArray = intArrayOf()

        @ColorInt var darkColors: IntArray = intArrayOf()

        @ColorProviderUtil.ColorSource var source: String? = null
        var isDefault = false
        @ColorInt var seedColor = 0
        @ThemeStyle.Type var style = ThemeStyle.TONAL_SPOT
        var index = 0
        var packages: MutableMap<String, String?> = HashMap()
        var type = ColorType.WALLPAPER_COLOR
        var isThemeServiceEnabled = false
        var isColorPickerUpdateEnabled = false

        fun build(): ColorOptionImpl {
            return ColorOptionImpl(
                title = title,
                source = source,
                seedColor = seedColor,
                style = style,
                isThemeServiceEnabled = isThemeServiceEnabled,
                isColorPickerUpdateEnabled = isColorPickerUpdateEnabled,
                overlayPackages = packages,
                isDefault = isDefault,
                index = index,
                previewInfo = createPreviewInfo(),
                type = type,
            )
        }

        private fun createPreviewInfo(): PreviewInfo {
            return PreviewInfo(lightColors, darkColors)
        }

        fun addOverlayPackage(category: String?, packageName: String?): ColorOptionImpl.Builder {
            category?.let { packages[category] = packageName }
            return this
        }
    }
}
