// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0
package com.android.customization.picker.color.ui.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.theme.PlatformTheme
import com.android.customization.model.color.ColorOption
import com.android.customization.picker.color.ui.viewmodel.ColorDetails
import com.android.customization.picker.color.ui.viewmodel.ColorDraft
import com.android.customization.picker.color.ui.viewmodel.ColorPickerViewModel
import com.android.customization.picker.color.ui.viewmodel.ColorSectionViewModel
import com.android.customization.picker.color.ui.viewmodel.ColorSwatchViewModel
import com.android.customization.picker.color.ui.viewmodel.SwatchEditAction
import com.android.customization.picker.color.ui.viewmodel.SwatchKey
import com.android.customization.picker.color.ui.viewmodel.label
import com.android.customization.picker.common.ui.compose.LabeledOption
import com.android.customization.picker.common.ui.compose.SectionedSheet
import com.android.customization.picker.common.ui.compose.SectionedSheetFooter
import com.android.customization.picker.common.ui.compose.SectionedSheetHeader
import com.android.customization.picker.common.ui.compose.sectionTitle
import com.android.customization.picker.mode.ui.viewmodel.DarkModeViewModel
import com.android.systemui.monet.ColorScheme
import com.android.themepicker.R

private val SwatchSize = 52.dp
private val HeaderSwatchSize = 36.dp

/**
 * The Colors sheet: named swatches in sections, the header with the previewed color and the dark
 * theme switch. The editor for colors of the user opens in place of the sheet.
 */
@Composable
fun ColorsSheet(
    darkModeViewModel: DarkModeViewModel,
    colorPickerViewModel: ColorPickerViewModel,
    modifier: Modifier = Modifier,
) {
    val previewingColorOption: ColorOption? by
        colorPickerViewModel.tempPreviewingColorOption.collectAsStateWithLifecycle(
            initialValue = null
        )
    val previewingIsDarkMode: Boolean by
        darkModeViewModel.previewingIsDarkMode.collectAsStateWithLifecycle(initialValue = false)
    val draft: ColorDraft? by colorPickerViewModel.editor.draft.collectAsStateWithLifecycle()

    PlatformTheme {
        val scheme =
            remember(previewingColorOption, previewingIsDarkMode) {
                previewingColorOption?.let {
                    ColorScheme(it.seedColor, previewingIsDarkMode, it.style).materialScheme
                }
            }
        ColorPreviewTheme(scheme) {
            AnimatedContent(
                targetState = draft != null,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { editing ->
                if (editing) {
                    ColorEditor(
                        viewModel = colorPickerViewModel.editor,
                        isDarkMode = previewingIsDarkMode,
                        modifier = modifier,
                    )
                } else {
                    ColorsLanding(
                        colorPickerViewModel = colorPickerViewModel,
                        darkModeViewModel = darkModeViewModel,
                        isDarkMode = previewingIsDarkMode,
                        modifier = modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorsLanding(
    colorPickerViewModel: ColorPickerViewModel,
    darkModeViewModel: DarkModeViewModel,
    isDarkMode: Boolean,
    modifier: Modifier,
) {
    val sections: List<ColorSectionViewModel> by
        colorPickerViewModel.colorSections.collectAsStateWithLifecycle(initialValue = emptyList())
    val previewing: ColorSwatchViewModel? by
        colorPickerViewModel.previewingSwatch.collectAsStateWithLifecycle(initialValue = null)

    SectionedSheet(
        optionSize = SwatchSize,
        header =
            previewing?.let { swatch ->
                @Composable {
                    ColorsSheetHeader(
                        swatch = swatch,
                        isDarkMode = isDarkMode,
                        onEdit = colorPickerViewModel::startEditor,
                    )
                }
            },
        footer = { DarkThemeRow(darkModeViewModel = darkModeViewModel, isDarkMode = isDarkMode) },
        modifier = modifier,
    ) {
        sections.forEach { section ->
            sectionTitle(key = section.type) { section.title.asComposeString() }
            items(section.swatches, key = { it.key }) { swatch ->
                LabeledOption(
                    label = swatch.name.asComposeString(),
                    isSelected = swatch.key == previewing?.key,
                    onClick = { colorPickerViewModel.previewSwatch(swatch.key) },
                    size = SwatchSize,
                    cornerRadius = SwatchSize / 2,
                ) {
                    Swatch(fill = swatch.fill, isDarkMode = isDarkMode, modifier = it)
                }
            }
            if (section.canAddColor) {
                item(key = SwatchKey.NewColor) {
                    NewColorTile(onClick = colorPickerViewModel::startNewColor)
                }
            }
        }
    }
}

@Composable
private fun ColorsSheetHeader(
    swatch: ColorSwatchViewModel,
    isDarkMode: Boolean,
    onEdit: (SwatchEditAction) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    SectionedSheetHeader(
        name = swatch.name.asComposeString(),
        details = swatch.details?.let { colorDetailsText(it) },
        sample = {
            Swatch(
                fill = swatch.fill,
                isDarkMode = isDarkMode,
                modifier = Modifier.size(HeaderSwatchSize),
            )
        },
        action =
            swatch.editAction?.let { action ->
                @Composable {
                    IconButton(
                        onClick = { onEdit(action) },
                        modifier = Modifier.size(36.dp),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = colorScheme.surfaceContainer,
                                contentColor = colorScheme.onSurface,
                            ),
                    ) {
                        Icon(
                            painter = painterResource(com.android.wallpaper.R.drawable.ic_edit),
                            contentDescription = stringResource(action.description),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
    )
}

@Composable
private fun colorDetailsText(details: ColorDetails): String =
    stringResource(
        R.string.color_details,
        stringResource(details.style.label()),
        details.seed.hexDigits,
    )

@Composable
private fun NewColorTile(onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    LabeledOption(
        label = stringResource(R.string.new_color_title),
        isSelected = false,
        onClick = onClick,
        size = SwatchSize,
        cornerRadius = SwatchSize / 2,
    ) {
        Box(
            modifier = it.border(width = 1.5.dp, color = colorScheme.outline, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add_24px),
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DarkThemeRow(darkModeViewModel: DarkModeViewModel, isDarkMode: Boolean) {
    val toggleIsDarkMode: () -> Unit by
        darkModeViewModel.toggleDarkMode.collectAsStateWithLifecycle(initialValue = {})
    val isEnabled: Boolean by
        darkModeViewModel.isEnabled.collectAsStateWithLifecycle(initialValue = false)
    SectionedSheetFooter {
        Text(
            text = stringResource(R.string.mode_title),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        Switch(
            checked = isDarkMode,
            onCheckedChange = { toggleIsDarkMode() },
            enabled = isEnabled,
            thumbContent =
                if (isDarkMode) {
                    {
                        Icon(
                            painter =
                                painterResource(
                                    com.android.wallpaper.R.drawable.ic_check_wallpaper
                                ),
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
        )
    }
}
