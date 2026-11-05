// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0
package com.android.customization.picker.color.shared.model

import com.android.customization.model.color.ColorOption
import java.time.Instant
import java.util.UUID

@JvmInline
value class SavedColorId(val value: String) {
    companion object {
        fun create() = SavedColorId(UUID.randomUUID().toString())
    }
}

data class SavedColor(
    val id: SavedColorId,
    val name: String,
    val seed: SeedColor,
    val style: ColorStyle,
    /** Saved colors are listed in the order they were added. */
    val addedAt: Instant,
) {
    init {
        require(isValidName(name)) { "A saved color needs a name" }
    }

    companion object {
        fun isValidName(name: String) = name.isNotBlank()
    }
}

data class SavedColorOption(val color: SavedColor, val option: ColorOption)
