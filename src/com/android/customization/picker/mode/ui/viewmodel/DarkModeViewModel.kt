/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.customization.picker.mode.ui.viewmodel

import android.util.Log
import com.android.customization.module.logging.ThemesUserEventLogger
import com.android.customization.picker.mode.domain.interactor.DarkModeInteractor
import com.android.wallpaper.picker.customization.ui.viewmodel.ColorUpdateViewModel
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch

@ViewModelScoped
class DarkModeViewModel
@Inject
constructor(
    private val colorUpdateViewModel: ColorUpdateViewModel,
    private val interactor: DarkModeInteractor,
    private val logger: ThemesUserEventLogger,
) {
    /** Whether the dark theme is applied to the system */
    val isDarkMode = interactor.isDarkMode
    val isEnabled = interactor.isEnabled

    private val _overridingIsDarkMode = MutableStateFlow<Boolean?>(null)
    val overridingIsDarkMode = _overridingIsDarkMode.asStateFlow()
    val previewingIsDarkMode =
        combine(overridingIsDarkMode, isDarkMode, isEnabled) { override, current, isEnabled ->
            if (isEnabled) {
                override ?: current
            } else current
        }

    val toggleDarkMode =
        combine(overridingIsDarkMode, isDarkMode) { override, current ->
            // Only set override if its value is different from current, else set to null
            {
                _overridingIsDarkMode.value =
                    if (override == null || override == current) !current else null
            }
        }

    val onApply: Flow<(suspend () -> Unit)?> =
        combine(overridingIsDarkMode, isDarkMode, isEnabled) { override, current, isEnabled ->
            if (override != null && override != current && isEnabled) {
                {
                    // A failure is logged, and the preview remains, so Apply can be tried again.
                    applyAndLog(override)
                }
            } else null
        }

    fun resetPreview() {
        _overridingIsDarkMode.value = null
    }

    /**
     * Applies the dark theme right away, for the switch on the main screen of the picker. Returns
     * whether the system applied it.
     */
    suspend fun applyDarkMode(isActive: Boolean): Boolean =
        isEnabled.first() && applyAndLog(isActive)

    private suspend fun applyAndLog(isActive: Boolean): Boolean = coroutineScope {
        // Dark mode change also invokes a color update. Suspend until both dark mode and color are
        // updated.
        val waitForUpdate = launch {
            combine(
                    // Omit the first value which is emitted on subscribe.
                    isDarkMode.drop(1).take(1),
                    colorUpdateViewModel.systemColorsUpdatedNoReplay.take(1),
                    ::Pair,
                )
                .collect { (_, _) ->
                    return@collect
                }
        }
        val isApplied = interactor.setIsDarkMode(isActive)
        if (isApplied) {
            waitForUpdate.join()
            logger.logDarkThemeApplied(isActive)
        } else {
            waitForUpdate.cancel()
            Log.w(TAG, "The system did not apply the dark theme (requested: $isActive)")
        }
        isApplied
    }

    private companion object {
        const val TAG = "DarkModeViewModel"
    }
}
