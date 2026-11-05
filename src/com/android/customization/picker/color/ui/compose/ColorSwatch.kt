// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0
package com.android.customization.picker.color.ui.compose

import androidx.annotation.ColorInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.android.customization.picker.color.ui.viewmodel.SwatchFill
import com.android.customization.picker.color.ui.viewmodel.colors
import com.android.customization.picker.color.ui.viewmodel.toIconViewModel
import kotlin.math.ceil

@Composable
internal fun Swatch(fill: SwatchFill, isDarkMode: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.clip(CircleShape)) {
        drawQuadrants(fill.toIconViewModel().colors(isDarkMode))
    }
}

/** Draws four colors in the order top left, top right, bottom left, bottom right. */
internal fun DrawScope.drawQuadrants(@ColorInt colors: List<Int>) {
    // Rounded up, so no pixel is left empty between the quadrants.
    val quadrant = Size(ceil(size.width / 2f), ceil(size.height / 2f))
    colors.forEachIndexed { index, color ->
        drawRect(
            color = Color(color),
            topLeft = Offset(x = (index % 2) * size.width / 2f, y = (index / 2) * size.height / 2f),
            size = quadrant,
        )
    }
}
