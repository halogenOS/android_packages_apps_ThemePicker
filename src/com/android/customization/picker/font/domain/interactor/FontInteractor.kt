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

package com.android.customization.picker.font.domain.interactor

import android.graphics.fonts.FontManager
import android.net.Uri
import com.android.customization.picker.font.data.repository.FontRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Domain entry point for font customization. Holds the user's _pending_ selection while the
 * apply button is showing, and commits it through [FontRepository] when apply is invoked.
 */
@Singleton
class FontInteractor @Inject constructor(private val repository: FontRepository) {

    val installedFamilies = repository.installedFamilies
    val activeFamily = repository.activeFamily

    private val _pendingSelection = MutableStateFlow<Selection>(Selection.Unset)
    val pendingSelection: StateFlow<Selection> = _pendingSelection.asStateFlow()

    suspend fun refresh() = repository.refresh()

    fun setPending(familyName: String?) {
        _pendingSelection.value = Selection.Value(familyName)
    }

    fun clearPending() {
        _pendingSelection.value = Selection.Unset
    }

    /** Commits the pending selection, returns the framework result code or `RESULT_SUCCESS` no-op. */
    suspend fun applyPending(): Int {
        val pending = _pendingSelection.value
        if (pending !is Selection.Value) return FontManager.RESULT_SUCCESS
        val code = repository.setActive(pending.familyName)
        if (code == FontManager.RESULT_SUCCESS) {
            _pendingSelection.value = Selection.Unset
        }
        return code
    }

    suspend fun remove(familyName: String): Int {
        if ((_pendingSelection.value as? Selection.Value)?.familyName == familyName) {
            _pendingSelection.value = Selection.Unset
        }
        return repository.remove(familyName)
    }

    suspend fun installFromUri(uri: Uri): Result<String> = repository.installFromUri(uri)

    sealed class Selection {
        object Unset : Selection()

        data class Value(val familyName: String?) : Selection()
    }
}
