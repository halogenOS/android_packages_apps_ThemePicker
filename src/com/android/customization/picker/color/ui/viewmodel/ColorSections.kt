// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0
package com.android.customization.picker.color.ui.viewmodel

import androidx.annotation.StringRes
import com.android.customization.model.color.ColorOption
import com.android.customization.picker.color.shared.model.ColorType
import com.android.customization.picker.color.shared.model.SavedColorOption
import com.android.themepicker.R
import com.android.wallpaper.picker.common.text.ui.viewmodel.Text

/** Builds the sections of the Colors sheet in the order they are shown, leaving out empty ones. */
internal fun buildColorSections(
    colorOptions: List<Pair<ColorType, List<ColorOption>>>,
    savedColors: List<SavedColorOption>,
    selected: ColorOption?,
): List<ColorSectionViewModel> =
    buildList {
            colorOptions.forEach { (type, options) ->
                when (type) {
                    ColorType.WALLPAPER_COLOR -> add(buildWallpaperSection(options, selected))
                    ColorType.BRAND_COLOR,
                    ColorType.PRESET_COLOR -> add(buildPresetSection(type, options))
                    // The repository lists them as savedColors, together with their names.
                    ColorType.SAVED_COLOR -> Unit
                }
            }
            add(buildSavedColorSection(savedColors))
        }
        .filter { it.swatches.isNotEmpty() || it.canAddColor }

/** The swatch with [key], or `null` when no section shows it any more. */
internal fun List<ColorSectionViewModel>.findSwatch(key: SwatchKey): ColorSwatchViewModel? =
    asSequence().flatMap { it.swatches }.firstOrNull { it.key == key }

/**
 * The first swatch that applies [selected]. Without one, a swatch made from [selected] itself, so a
 * color applied elsewhere or deleted since still shows.
 */
internal fun List<ColorSectionViewModel>.findApplied(selected: ColorOption): ColorSwatchViewModel =
    asSequence().flatMap { it.swatches }.firstOrNull { it.option.isEquivalent(selected) }
        ?: ColorSwatchViewModel(
            key = SwatchKey.Applied,
            name = Text.Resource(R.string.applied_color_name),
            option = selected,
        )

/**
 * One swatch per wallpaper seed. Of the options a seed has in each style, the applied one is shown,
 * otherwise the first.
 */
private fun buildWallpaperSection(
    options: List<ColorOption>,
    selected: ColorOption?,
): ColorSectionViewModel =
    ColorSectionViewModel(
        type = ColorType.WALLPAPER_COLOR,
        title = Text.Resource(ColorType.WALLPAPER_COLOR.sectionTitle),
        swatches =
            options
                .groupBy { it.seedColor }
                .values
                .mapIndexed { index, seedOptions ->
                    ColorSwatchViewModel(
                        key = SwatchKey.Wallpaper(index),
                        name = Text.Formatted(R.string.wallpaper_color_name, listOf(index + 1)),
                        option =
                            seedOptions.firstOrNull { it.isEquivalent(selected) }
                                ?: seedOptions.first(),
                    )
                },
    )

private fun buildPresetSection(type: ColorType, options: List<ColorOption>) =
    ColorSectionViewModel(
        type = type,
        title = Text.Resource(type.sectionTitle),
        swatches =
            options.mapIndexed { index, option ->
                ColorSwatchViewModel(
                    key = SwatchKey.Preset(type, index),
                    name = Text.Loaded(option.title.orEmpty()),
                    option = option,
                )
            },
    )

private fun buildSavedColorSection(savedColors: List<SavedColorOption>) =
    ColorSectionViewModel(
        type = ColorType.SAVED_COLOR,
        title = Text.Resource(ColorType.SAVED_COLOR.sectionTitle),
        swatches =
            savedColors.map { (color, option) ->
                ColorSwatchViewModel(
                    key = SwatchKey.Saved(color.id),
                    name = Text.Loaded(color.name),
                    option = option,
                    savedColor = color,
                )
            },
        canAddColor = true,
    )

@get:StringRes
private val ColorType.sectionTitle: Int
    get() =
        when (this) {
            ColorType.WALLPAPER_COLOR -> R.string.wallpaper_color_tab
            ColorType.BRAND_COLOR -> R.string.brand_color_section_title
            ColorType.PRESET_COLOR -> R.string.preset_color_section_title
            ColorType.SAVED_COLOR -> R.string.freeform_color_title
        }
