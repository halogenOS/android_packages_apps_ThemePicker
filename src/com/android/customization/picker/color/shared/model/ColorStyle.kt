// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0
package com.android.customization.picker.color.shared.model

import android.content.theming.ThemeStyle

enum class ColorStyle(@ThemeStyle.Type val themeStyle: Int) {
    NEUTRAL(ThemeStyle.SPRITZ),
    SOFT(ThemeStyle.TONAL_SPOT),
    BRIGHT(ThemeStyle.VIBRANT),
    BOLD(ThemeStyle.EXPRESSIVE),
    TRUE(ThemeStyle.CONTENT);

    companion object {
        /** Returns the style building palettes with [themeStyle], or `null` when none does. */
        fun of(@ThemeStyle.Type themeStyle: Int): ColorStyle? =
            entries.firstOrNull { it.themeStyle == themeStyle }
    }
}
