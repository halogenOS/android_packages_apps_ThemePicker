/*
 * Copyright (C) 2025 The Android Open Source Project
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
 */
package com.android.customization.picker.color.ui.viewmodel

import android.content.theming.ThemeStyle
import android.util.Log
import com.android.customization.model.color.ColorOption
import com.android.customization.module.logging.ThemesUserEventLogger
import com.android.customization.picker.color.domain.interactor.ColorPickerInteractor
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.picker.customization.ui.viewmodel.ColorUpdateViewModel
import com.android.wallpaper.picker.di.modules.BackgroundDispatcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Models UI state for a color picker experience. */
class ColorPickerViewModel
@AssistedInject
constructor(
    private val colorUpdateViewModel: ColorUpdateViewModel,
    private val interactor: ColorPickerInteractor,
    private val logger: ThemesUserEventLogger,
    baseFlags: BaseFlags,
    @BackgroundDispatcher backgroundDispatcher: CoroutineDispatcher,
    @Assisted private val viewModelScope: CoroutineScope,
) {
    val selectedColorOption = interactor.selectedColorOption

    /** Sections of the Colors sheet in the order they are shown, leaving out empty ones */
    val colorSections: StateFlow<List<ColorSectionViewModel>> =
        combine(
                interactor.colorOptions,
                interactor.savedColors,
                selectedColorOption,
                ::buildColorSections,
            )
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // The swatch tapped last on the Colors sheet
    private val overridingSwatchKey = MutableStateFlow<SwatchKey?>(null)

    // The overriding color on the color picker landing screen
    val overridingColorOption: StateFlow<ColorOption?> =
        combine(colorSections, overridingSwatchKey) { sections, key ->
                key?.let { sections.findSwatch(it)?.option }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    // The color being previewed on the color picker landing screen
    val previewingColorOption =
        combine(overridingColorOption, selectedColorOption) { overriding, selected ->
                overriding ?: selected
            }
            .distinctUntilChanged()

    /** The swatch being previewed, otherwise the applied one */
    val previewingSwatch: StateFlow<ColorSwatchViewModel?> =
        combine(colorSections, overridingSwatchKey, selectedColorOption) { sections, key, selected
                ->
                key?.let { sections.findSwatch(it) } ?: selected?.let { sections.findApplied(it) }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val editor =
        ColorEditorViewModel(
            interactor = interactor,
            scope = viewModelScope,
            backgroundDispatcher = backgroundDispatcher,
            onSaved = { id -> previewSwatch(SwatchKey.Saved(id)) },
        )

    // The overriding color across screens, including the color editor
    val tempOverridingColorOption =
        combine(editor.previewOption, overridingColorOption) { draft, overriding ->
            draft ?: overriding
        }
    // The color being previewed across screens, including the color editor
    val tempPreviewingColorOption =
        combine(tempOverridingColorOption, selectedColorOption) { overriding, selected ->
                overriding ?: selected
            }
            .distinctUntilChanged()

    // Style options, used in variant picker
    val selectedStyle = interactor.selectedStyle.distinctUntilChanged()
    private val _overridingStyle = MutableStateFlow<Int?>(null)
    // Style selected but not yet confirmed by user, this is reset to null if the user cancels
    private val _tempOverridingStyle = MutableStateFlow<Int?>(null)
    val overridingStyle =
        combine(_overridingStyle, _tempOverridingStyle) { saved, temp -> temp ?: saved }
            .distinctUntilChanged()
    val previewingStyle =
        combine(selectedStyle, overridingStyle, selectedColorOption, tempOverridingColorOption) {
                selectedStyle,
                overridingStyle,
                selectedColor,
                overridingColor ->
                getPreviewingStyle(selectedStyle, overridingStyle, selectedColor, overridingColor)
            }
            .distinctUntilChanged()

    @ThemeStyle.Type
    fun getPreviewingStyle(
        @ThemeStyle.Type selectedStyle: Int?,
        @ThemeStyle.Type overridingStyle: Int?,
        selectedColorOption: ColorOption?,
        overridingColorOption: ColorOption?,
    ): Int? {
        // User selection takes precedence
        return overridingStyle
            // Otherwise, if the there is a different color option selected, use its style
            ?: if (
                overridingColorOption != null &&
                    !overridingColorOption.isEquivalent(selectedColorOption)
            ) {
                overridingColorOption.style
            } else {
                // Otherwise, use the system selected style
                selectedStyle
            }
    }

    fun onStyleOptionClick(@ThemeStyle.Type style: Int) {
        _tempOverridingStyle.value = style
    }

    fun confirmStyleOptionSelection() {
        _tempOverridingStyle.value?.let { _overridingStyle.value = it }
        _tempOverridingStyle.value = null
    }

    fun cancelStyleOptionSelection() {
        _tempOverridingStyle.value = null
    }

    fun resetStyleOptionSelection() {
        _overridingStyle.value = null
        _tempOverridingStyle.value = null
    }

    fun previewSwatch(key: SwatchKey) {
        overridingSwatchKey.value = key
        resetStyleOptionSelection()
    }

    /** Runs the edit button in the header of the Colors sheet */
    fun startEditor(action: SwatchEditAction) =
        when (action) {
            is SwatchEditAction.Edit -> editor.startEditing(action.color)
            is SwatchEditAction.NewFromThis -> editor.startNew(action.details)
        }

    /** Opens the editor on a new color that starts from the previewed one, or else the first */
    fun startNewColor() {
        val swatches =
            listOfNotNull(previewingSwatch.value) + colorSections.value.flatMap { it.swatches }
        swatches.firstNotNullOfOrNull { it.details }?.let(editor::startNew)
    }

    /**
     * This function suspends until onApplyComplete is called to accommodate for configuration
     * change updates, which are applied with a latency.
     */
    val onApply: Flow<(suspend () -> Unit)?> =
        if (baseFlags.isColorPickerUpdateEnabled()) {
            combine(selectedColorOption, overridingColorOption, selectedStyle, overridingStyle) {
                selectedColor,
                overridingColor,
                selectedStyle,
                overridingStyle ->
                val colorNeedsUpdate =
                    overridingColor != null && !overridingColor.isEquivalent(selectedColor)
                val styleNeedsUpdate = overridingStyle != null && overridingStyle != selectedStyle
                val colorOption = overridingColor ?: selectedColor
                if ((colorNeedsUpdate || styleNeedsUpdate) && colorOption != null) {
                    val style = overridingStyle ?: colorOption.style
                    {
                        applyAndWaitForColorUpdate(
                            apply = { interactor.apply(colorOption, style) },
                            onSuccess = {
                                logger.logThemeColorApplied(
                                    colorOption.sourceForLogging,
                                    // TODO(b/473022455): centralize logging in
                                    //  ThemesUserEventLogger
                                    ThemeStyle.toString(style).hashCode(),
                                    colorOption.seedColor,
                                )
                            },
                        )
                    }
                } else null
            }
        } else {
            combine(overridingColorOption, selectedColorOption) { previewing, selected ->
                previewing?.let {
                    if (previewing.isEquivalent(selected)) {
                        null
                    } else {
                        {
                            applyAndWaitForColorUpdate(
                                apply = { interactor.apply(it) },
                                onSuccess = {
                                    logger.logThemeColorApplied(
                                        it.sourceForLogging,
                                        it.styleForLogging,
                                        it.seedColor,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

    private suspend fun applyAndWaitForColorUpdate(
        apply: suspend () -> Boolean,
        onSuccess: () -> Unit,
    ) {
        return coroutineScope {
            val waitForColorUpdateJob = launch {
                // Suspend until first color update, or time out after 3 seconds
                try {
                    withTimeout(COLOR_UPDATE_TIMEOUT_MILLIS) {
                        colorUpdateViewModel.systemColorsUpdatedNoReplay.take(1).collect {
                            return@collect
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.w(TAG, "Timed out waiting for color update", e)
                }
            }
            val success = apply()
            if (success) {
                onSuccess()
                waitForColorUpdateJob.join()
            } else {
                waitForColorUpdateJob.cancel()
            }
        }
    }

    fun resetPreview() {
        overridingSwatchKey.value = null
        editor.close()
        resetStyleOptionSelection()
    }

    @ViewModelScoped
    @AssistedFactory
    interface Factory {
        fun create(viewModelScope: CoroutineScope): ColorPickerViewModel
    }

    companion object {
        const val TAG = "ColorPickerViewModel"
        const val COLOR_UPDATE_TIMEOUT_MILLIS = 3000L
    }
}
