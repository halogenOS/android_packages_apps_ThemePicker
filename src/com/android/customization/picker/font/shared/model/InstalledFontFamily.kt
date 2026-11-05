// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0
package com.android.customization.picker.font.shared.model

enum class FontOrigin(
    /** Position of the section of fonts of this origin in the Font sheet, counted from the top */
    val sectionRank: Int
) {
    INSTALLED_BY_USER(sectionRank = 0),

    /** Shipped in the system image, like the default font */
    INCLUDED(sectionRank = 1),
}

/** A font family the framework can apply, with the name it is shown under. */
data class InstalledFontFamily(val name: String, val displayName: String, val origin: FontOrigin)
