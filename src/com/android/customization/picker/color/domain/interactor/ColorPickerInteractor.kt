/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.customization.picker.color.domain.interactor

import android.content.theming.ThemeStyle
import com.android.customization.model.color.ColorOption
import com.android.customization.picker.color.data.repository.ColorPickerRepository
import com.android.customization.picker.color.shared.model.ColorStyle
import com.android.customization.picker.color.shared.model.ColorType
import com.android.customization.picker.color.shared.model.SavedColor
import com.android.customization.picker.color.shared.model.SavedColorId
import com.android.customization.picker.color.shared.model.SeedColor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Single entry-point for all application state and business logic related to system color. */
@Singleton
class ColorPickerInteractor @Inject constructor(private val repository: ColorPickerRepository) {
    val selectedColorOption = repository.selectedColorOption

    /** List of wallpaper and preset color options on the device, categorized by Color Type */
    val colorOptions: Flow<List<Pair<ColorType, List<ColorOption>>>> = repository.colorOptions

    val styleList = repository.styleList

    val selectedStyle = repository.selectedStyle

    suspend fun apply(colorOption: ColorOption): Boolean = repository.apply(colorOption)

    suspend fun apply(colorOption: ColorOption, @ThemeStyle.Type style: Int): Boolean =
        repository.apply(colorOption, style)

    val savedColors = repository.savedColors

    suspend fun saveColor(color: SavedColor): Result<Unit> = repository.saveColor(color)

    suspend fun deleteColor(id: SavedColorId): Result<Unit> = repository.deleteColor(id)

    fun buildPreviewOption(name: String, seed: SeedColor, style: ColorStyle): ColorOption =
        repository.buildPreviewOption(name, seed, style)
}
