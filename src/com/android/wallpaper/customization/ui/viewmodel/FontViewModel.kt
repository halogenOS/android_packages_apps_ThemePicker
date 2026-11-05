// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0
package com.android.wallpaper.customization.ui.viewmodel

import android.graphics.Typeface
import androidx.annotation.StringRes
import com.android.customization.picker.font.shared.model.FontOrigin
import com.android.themepicker.R
import com.android.wallpaper.picker.common.text.ui.viewmodel.Text

data class FontViewModel(
    /** The family to apply, or `null` for the default font. */
    val family: String?,
    /** The family that draws the sample of the font. */
    val typefaceFamily: String,
    val name: Text,
    val origin: FontOrigin,
) {
    fun createTypeface(): Typeface = Typeface.create(typefaceFamily, Typeface.NORMAL)
}

data class FontSectionViewModel(val origin: FontOrigin, val fonts: List<FontViewModel>)

/** The title of the section of fonts of this origin, also shown under the name of each font. */
@StringRes
fun FontOrigin.label(): Int =
    when (this) {
        FontOrigin.INSTALLED_BY_USER -> R.string.font_origin_installed_by_user
        FontOrigin.INCLUDED -> R.string.font_origin_included
    }
