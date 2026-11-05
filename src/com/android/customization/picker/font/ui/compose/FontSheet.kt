// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0
package com.android.customization.picker.font.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.theme.PlatformTheme
import com.android.customization.picker.common.ui.compose.LabeledOption
import com.android.customization.picker.common.ui.compose.SectionedSheet
import com.android.customization.picker.common.ui.compose.SectionedSheetFooter
import com.android.customization.picker.common.ui.compose.SectionedSheetHeader
import com.android.customization.picker.common.ui.compose.sectionTitle
import com.android.themepicker.R
import com.android.wallpaper.customization.ui.viewmodel.FontPickerViewModel
import com.android.wallpaper.customization.ui.viewmodel.FontSectionViewModel
import com.android.wallpaper.customization.ui.viewmodel.FontViewModel
import com.android.wallpaper.customization.ui.viewmodel.label

private val FontTileSize = 64.dp
private val FontTileCornerRadius = 20.dp

/**
 * The Font sheet: fonts in sections, the header with the previewed font and the row that installs
 * fonts. [onInstallFont] is `null` where the picker cannot open files.
 */
@Composable
fun FontSheet(
    viewModel: FontPickerViewModel,
    onInstallFont: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val sections: List<FontSectionViewModel> by
        viewModel.sections.collectAsStateWithLifecycle(initialValue = emptyList())
    val previewing: FontViewModel? by
        viewModel.previewingFont.collectAsStateWithLifecycle(initialValue = null)

    PlatformTheme {
        SectionedSheet(
            optionSize = FontTileSize,
            header = previewing?.let { font -> @Composable { FontSheetHeader(font) } },
            footer = { InstallFontRow(onInstallFont) },
            modifier = modifier,
        ) {
            sections.forEach { section ->
                sectionTitle(key = section.origin) { stringResource(section.origin.label()) }
                items(section.fonts) { font ->
                    FontTile(
                        font = font,
                        isSelected = previewing?.let { it.family == font.family } == true,
                        onClick = { viewModel.previewFont(font.family) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FontSheetHeader(font: FontViewModel) {
    SectionedSheetHeader(
        name = font.name.asComposeString(),
        details = stringResource(font.origin.label()),
        sample = { FontSample(font = font, fontSize = 30.sp) },
    )
}

@Composable
private fun FontTile(font: FontViewModel, isSelected: Boolean, onClick: () -> Unit) {
    LabeledOption(
        label = font.name.asComposeString(),
        isSelected = isSelected,
        onClick = onClick,
        size = FontTileSize,
        cornerRadius = FontTileCornerRadius,
    ) {
        Box(
            modifier = it.background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            FontSample(font = font, fontSize = 28.sp)
        }
    }
}

/** A short text in [font]. Screen readers skip it, since the name of the font comes with it. */
@Composable
private fun FontSample(font: FontViewModel, fontSize: TextUnit) {
    val fontFamily = remember(font.typefaceFamily) { FontFamily(font.createTypeface()) }
    Text(
        text = stringResource(R.string.font_sample),
        modifier = Modifier.clearAndSetSemantics {},
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = fontSize,
        fontFamily = fontFamily,
        maxLines = 1,
    )
}

@Composable
private fun InstallFontRow(onInstallFont: (() -> Unit)?) {
    val colorScheme = MaterialTheme.colorScheme
    SectionedSheetFooter(
        modifier =
            Modifier.clickable(
                enabled = onInstallFont != null,
                role = Role.Button,
                onClick = { onInstallFont?.invoke() },
            )
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_file_upload_24px),
            contentDescription = null,
            tint = colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.font_picker_install_label),
            color = colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
