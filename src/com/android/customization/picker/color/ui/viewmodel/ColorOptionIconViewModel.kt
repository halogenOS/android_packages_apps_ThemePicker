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

package com.android.customization.picker.color.ui.viewmodel

import android.annotation.ColorInt
import com.android.customization.model.color.ColorOption

data class ColorOptionIconViewModel(
    @ColorInt val lightThemeColor0: Int,
    @ColorInt val lightThemeColor1: Int,
    @ColorInt val lightThemeColor2: Int,
    @ColorInt val lightThemeColor3: Int,
    @ColorInt val darkThemeColor0: Int,
    @ColorInt val darkThemeColor1: Int,
    @ColorInt val darkThemeColor2: Int,
    @ColorInt val darkThemeColor3: Int,
) {
    companion object {
        fun fromColorOption(colorOption: ColorOption): ColorOptionIconViewModel {
            val lightThemeColors = colorOption.previewInfo.resolveColors(/* darkTheme= */ false)
            val darkThemeColors = colorOption.previewInfo.resolveColors(/* darkTheme= */ true)
            return ColorOptionIconViewModel(
                lightThemeColor0 = lightThemeColors[0],
                lightThemeColor1 = lightThemeColors[1],
                lightThemeColor2 = lightThemeColors[2],
                lightThemeColor3 = lightThemeColors[3],
                darkThemeColor0 = darkThemeColors[0],
                darkThemeColor1 = darkThemeColors[1],
                darkThemeColor2 = darkThemeColors[2],
                darkThemeColor3 = darkThemeColors[3],
            )
        }
    }
}

/** The four quadrant colors of the icon in the dark or the light theme. */
fun ColorOptionIconViewModel.colors(isDarkMode: Boolean): List<Int> =
    if (isDarkMode) {
        listOf(darkThemeColor0, darkThemeColor1, darkThemeColor2, darkThemeColor3)
    } else {
        listOf(lightThemeColor0, lightThemeColor1, lightThemeColor2, lightThemeColor3)
    }
