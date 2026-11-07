// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0
package com.android.customization.picker.color.ui.viewmodel

import androidx.annotation.StringRes
import com.android.customization.picker.color.shared.model.ColorStyle
import com.android.themepicker.R

/** The name of the style as the Colors sheet and the color editor show it. */
@StringRes
fun ColorStyle.label(): Int =
    when (this) {
        ColorStyle.NEUTRAL -> R.string.color_variant_option_label_neutral
        ColorStyle.SOFT -> R.string.color_variant_option_label_soft
        ColorStyle.BRIGHT -> R.string.color_variant_option_label_bright
        ColorStyle.BOLD -> R.string.color_variant_option_label_bold
        ColorStyle.TRUE -> R.string.color_variant_option_label_true
    }
