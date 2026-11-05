// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0
package com.android.customization.picker.color.shared.model

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils.setAlphaComponent
import com.android.customization.model.color.ColorUtils

/** An opaque color that a palette is built from. */
@JvmInline
value class SeedColor(@ColorInt val argb: Int) {
    init {
        require(Color.alpha(argb) == OPAQUE_ALPHA) { "A seed color is opaque" }
    }

    /** The six hex digits of the color, the form people type it in. */
    val hexDigits: String
        get() = ColorUtils.toColorString(argb)

    companion object {
        private const val OPAQUE_ALPHA = 0xFF
        private const val HEX_DIGITS = 6
        private const val HEX_RADIX = 16

        /** Returns [argb] as a seed, or `null` when it is translucent. */
        fun ofOrNull(@ColorInt argb: Int): SeedColor? =
            argb.takeIf { Color.alpha(it) == OPAQUE_ALPHA }?.let(::SeedColor)

        /** Reads six hex digits as a seed, or returns `null` for any other text. */
        fun parseHex(digits: String): SeedColor? =
            digits
                .takeIf {
                    it.length == HEX_DIGITS &&
                        it.all { char -> char.digitToIntOrNull(HEX_RADIX) != null }
                }
                ?.toInt(HEX_RADIX)
                ?.let { SeedColor(setAlphaComponent(it, OPAQUE_ALPHA)) }

        /** Keeps the hex digits of [text], up to the six a seed has. */
        fun filterHexDigits(text: String): String =
            text.filter { it.digitToIntOrNull(HEX_RADIX) != null }.take(HEX_DIGITS).uppercase()
    }
}
