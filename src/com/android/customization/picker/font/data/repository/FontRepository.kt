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

package com.android.customization.picker.font.data.repository

import android.content.Context
import android.graphics.fonts.FontManager
import android.net.Uri
import android.util.Log
import com.android.wallpaper.picker.di.modules.BackgroundDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Single source of truth for the user's installed custom font families and the family that is
 * currently applied to the system.
 *
 * The framework does not expose a reactive channel for overlay state, so mutations flow through
 * this class and update [installedFamilies] / [activeFamily] in-place.
 */
@Singleton
class FontRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    @BackgroundDispatcher private val bgDispatcher: CoroutineDispatcher,
) {

    private val fontManager: FontManager? = context.getSystemService(FontManager::class.java)

    private val _installedFamilies = MutableStateFlow<List<String>>(emptyList())
    val installedFamilies: StateFlow<List<String>> = _installedFamilies.asStateFlow()

    private val _activeFamily = MutableStateFlow<String?>(null)
    /** The family name currently applied via the fabricated overlay, or `null` for stock. */
    val activeFamily: StateFlow<String?> = _activeFamily.asStateFlow()

    suspend fun refresh() =
        withContext(bgDispatcher) {
            val manager = fontManager ?: return@withContext
            val families = runCatching { manager.customFontFamilyNames }.getOrDefault(emptyList())
            _installedFamilies.value = families

            val active = runCatching { manager.activeCustomFontFamily }.getOrNull()
            _activeFamily.value = active?.takeIf { families.contains(it) }
        }

    suspend fun setActive(familyName: String?): Int =
        withContext(bgDispatcher) {
            val manager = fontManager ?: return@withContext FontManager.RESULT_ERROR_FONT_UPDATER_DISABLED
            val code = manager.setActiveCustomFontFamily(familyName)
            if (code == FontManager.RESULT_SUCCESS) {
                _activeFamily.value = familyName
            }
            code
        }

    suspend fun remove(familyName: String): Int =
        withContext(bgDispatcher) {
            val manager = fontManager ?: return@withContext FontManager.RESULT_ERROR_FONT_UPDATER_DISABLED
            val code = manager.removeCustomFontFamily(familyName)
            if (code == FontManager.RESULT_SUCCESS) {
                _installedFamilies.value = _installedFamilies.value.filterNot { it == familyName }
                if (_activeFamily.value == familyName) {
                    _activeFamily.value = null
                }
            }
            code
        }

    /**
     * Install a font file pointed at by [uri] and register it as a new single-font family.
     *
     * The framework parses the font's name table and uses the PostScript name as the family
     * identifier. Returns the resolved family name on success.
     */
    suspend fun installFromUri(uri: Uri): Result<String> =
        withContext(bgDispatcher) {
            val manager = fontManager
                ?: return@withContext Result.failure(IllegalStateException("FontManager unavailable"))
            Log.d(TAG, "installFromUri: opening PFD for $uri")
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r").use { pfd ->
                    requireNotNull(pfd) { "Could not open font uri: $uri" }
                    Log.d(TAG, "installFromUri: PFD opened, calling installCustomFontFamilyFromFile")
                    val familyName = manager.installCustomFontFamilyFromFile(pfd)
                    Log.d(TAG, "installFromUri: success, familyName=$familyName")
                    _installedFamilies.value =
                        (_installedFamilies.value + familyName).distinct()
                    familyName
                }
            }.also { result ->
                result.onFailure { e ->
                    Log.e(TAG, "installFromUri: failed", e)
                }
            }
        }

    companion object {
        private const val TAG = "FontRepository"
    }
}
