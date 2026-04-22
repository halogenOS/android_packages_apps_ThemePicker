/*
 * Copyright (C) 2026 The halogenOS Project
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

package com.android.wallpaper.customization.ui.viewmodel

import android.content.Context
import android.net.Uri
import com.android.customization.picker.font.domain.interactor.FontInteractor
import com.android.themepicker.R
import com.android.wallpaper.picker.common.text.ui.viewmodel.Text
import com.android.wallpaper.picker.option.ui.viewmodel.OptionItemViewModel2
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

class FontPickerViewModel
@AssistedInject
constructor(
    @ApplicationContext private val context: Context,
    private val interactor: FontInteractor,
    @Assisted private val viewModelScope: CoroutineScope,
) {

    private val installedFamilies = interactor.installedFamilies
    private val activeFamily = interactor.activeFamily

    /** Non-null = user has picked something, awaiting Apply. Null = mirror current active. */
    private val overridingFamily = MutableStateFlow<Selection?>(null)

    /** Emits when a font change was successfully applied and SystemUI should be restarted. */
    val restartSystemUiRequired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** The family that should be previewed (user pick if pending, otherwise current active). */
    val previewingFamily: Flow<String?> =
        combine(overridingFamily, activeFamily) { overriding, active ->
                when (overriding) {
                    null -> active
                    Selection.Stock -> null
                    is Selection.Family -> overriding.name
                }
            }
            .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    val options: Flow<List<OptionItemViewModel2<String?>>> =
        combine(installedFamilies, activeFamily) { families, _ -> families }
            .map { families ->
                buildList {
                    add(buildOption(key = STOCK_KEY, family = null))
                    families.forEach { family ->
                        add(buildOption(key = family, family = family))
                    }
                }
            }
            .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    val onApply: Flow<(suspend () -> Unit)?> =
        combine(overridingFamily, activeFamily) { overriding, active ->
            val target = when (overriding) {
                null -> return@combine null
                Selection.Stock -> null
                is Selection.Family -> overriding.name
            }
            if (target == active) {
                null
            } else {
                {
                    interactor.setPending(target)
                    val code = interactor.applyPending()
                    overridingFamily.value = null
                    if (code == android.graphics.fonts.FontManager.RESULT_SUCCESS) {
                        restartSystemUiRequired.tryEmit(Unit)
                    }
                }
            }
        }

    suspend fun refresh() = interactor.refresh()

    suspend fun remove(familyName: String): Int = interactor.remove(familyName)

    suspend fun installFromUri(uri: Uri): Result<String> = interactor.installFromUri(uri)

    fun resetPreview() {
        overridingFamily.value = null
        interactor.clearPending()
    }

    private fun buildOption(key: String, family: String?): OptionItemViewModel2<String?> {
        val isSelected =
            previewingFamily
                .map { it == family }
                .stateIn(viewModelScope, SharingStarted.Lazily, initialValue = false)
        return OptionItemViewModel2(
            key = MutableStateFlow(key),
            payload = family,
            text =
                if (family == null) Text.Resource(R.string.font_picker_stock_option)
                else Text.Loaded(family),
            isSelected = isSelected,
            onClicked =
                isSelected.map { selected ->
                    if (selected) null
                    else {
                        {
                            overridingFamily.value =
                                if (family == null) Selection.Stock else Selection.Family(family)
                            interactor.setPending(family)
                        }
                    }
                },
            onLongClicked = null,
        )
    }

    private sealed class Selection {
        object Stock : Selection()
        data class Family(val name: String) : Selection()
    }

    @ViewModelScoped
    @AssistedFactory
    interface Factory {
        fun create(viewModelScope: CoroutineScope): FontPickerViewModel
    }

    companion object {
        private const val STOCK_KEY = "__stock__"
    }
}
