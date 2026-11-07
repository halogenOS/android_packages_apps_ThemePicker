// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0
package com.android.customization.picker.color.ui.viewmodel

import android.content.theming.ThemeStyle
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import com.android.customization.model.color.ColorOption
import com.android.customization.picker.color.shared.model.ColorStyle
import com.android.customization.picker.color.shared.model.ColorType
import com.android.customization.picker.color.shared.model.SavedColor
import com.android.customization.picker.color.shared.model.SavedColorId
import com.android.customization.picker.color.shared.model.SeedColor
import com.android.themepicker.R
import com.android.wallpaper.picker.common.text.ui.viewmodel.Text
import java.io.Serializable

/**
 * Identity of an item in the grid of the Colors sheet. Serializable, as the grid saves its keys.
 */
sealed interface SwatchKey : Serializable {
    data class Wallpaper(val index: Int) : SwatchKey

    data class Preset(val type: ColorType, val index: Int) : SwatchKey

    data class Saved(val id: SavedColorId) : SwatchKey

    /** The applied color when none of the swatches applies it */
    data object Applied : SwatchKey

    /** The tile that opens the editor on a new color */
    data object NewColor : SwatchKey
}

sealed interface SwatchFill {
    /** The seed color itself. */
    data class Seed(@ColorInt val color: Int) : SwatchFill

    /** The colors of the palette, drawn in quadrants, as wallpaper colors show them. */
    data class Palette(val icon: ColorOptionIconViewModel) : SwatchFill

    /** The dark upper half and the light lower half of the monochromatic palette. */
    data class Monochrome(val icon: ColorOptionIconViewModel) : SwatchFill
}

/** Style and seed of a color, shown under its name in the header of the Colors sheet. */
data class ColorDetails(val style: ColorStyle, val seed: SeedColor)

/** What the edit button in the header of the Colors sheet does for the previewed swatch. */
sealed interface SwatchEditAction {
    @get:StringRes val description: Int

    data class Edit(val color: SavedColor) : SwatchEditAction {
        override val description = R.string.edit_color_title
    }

    data class NewFromThis(val details: ColorDetails) : SwatchEditAction {
        override val description = R.string.new_color_from_this_description
    }
}

data class ColorSwatchViewModel(
    val key: SwatchKey,
    val name: Text,
    val option: ColorOption,
    /** Set for colors saved by the user. The user can edit and delete them. */
    val savedColor: SavedColor? = null,
) {
    val fill: SwatchFill = option.toSwatchFill()

    /** `null` for palettes that do not come from a seed and a [ColorStyle]. */
    val details: ColorDetails? =
        ColorStyle.of(option.style)?.let { style ->
            SeedColor.ofOrNull(option.seedColor)?.let { seed -> ColorDetails(style, seed) }
        }

    val editAction: SwatchEditAction? =
        savedColor?.let(SwatchEditAction::Edit) ?: details?.let(SwatchEditAction::NewFromThis)
}

data class ColorSectionViewModel(
    val type: ColorType,
    val title: Text,
    val swatches: List<ColorSwatchViewModel>,
    /** Whether the section ends with the tile that adds a color of the user */
    val canAddColor: Boolean = false,
)

private fun ColorOption.toSwatchFill(): SwatchFill {
    val icon = ColorOptionIconViewModel.fromColorOption(this)
    return when {
        type == ColorType.WALLPAPER_COLOR -> SwatchFill.Palette(icon)
        style == ThemeStyle.MONOCHROMATIC -> SwatchFill.Monochrome(icon)
        else -> SwatchFill.Seed(seedColor)
    }
}

/** Icon of the colors entry on the main screen and of the swatch, painted in four quadrants. */
fun SwatchFill.toIconViewModel(): ColorOptionIconViewModel =
    when (this) {
        is SwatchFill.Seed ->
            ColorOptionIconViewModel(color, color, color, color, color, color, color, color)
        is SwatchFill.Palette -> icon
        is SwatchFill.Monochrome ->
            with(icon) {
                ColorOptionIconViewModel(
                    lightThemeColor0 = darkThemeColor0,
                    lightThemeColor1 = darkThemeColor0,
                    lightThemeColor2 = lightThemeColor0,
                    lightThemeColor3 = lightThemeColor0,
                    darkThemeColor0 = darkThemeColor0,
                    darkThemeColor1 = darkThemeColor0,
                    darkThemeColor2 = lightThemeColor0,
                    darkThemeColor3 = lightThemeColor0,
                )
            }
    }
