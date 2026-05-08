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
import android.os.ParcelFileDescriptor
import android.util.Log
import com.android.wallpaper.picker.di.modules.BackgroundDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    private val _defaultFamily = MutableStateFlow<String?>(null)
    /** The build-time default font family (e.g. "adwaita-sans"), or `null` if unknown. */
    val defaultFamily: StateFlow<String?> = _defaultFamily.asStateFlow()

    /** Maps font family ID to human-readable display name (e.g. "lato" → "Lato"). */
    val displayNames: StateFlow<Map<String, String>> = MutableStateFlow(emptyMap())

    suspend fun refresh() =
        withContext(bgDispatcher) {
            val manager = fontManager ?: return@withContext
            val families = runCatching { manager.customFontFamilyNames }.getOrDefault(emptyList())
            _installedFamilies.value = families

            val displayNames = runCatching { manager.customFontFamilyDisplayNames }
                .getOrDefault(emptyMap())
            (this@FontRepository.displayNames as MutableStateFlow).value = displayNames

            val active = runCatching { manager.activeCustomFontFamily }.getOrNull()
            _activeFamily.value = active?.takeIf { families.contains(it) }

            val default = runCatching { manager.defaultFontFamily }.getOrNull()
            _defaultFamily.value = default?.takeIf { it.isNotEmpty() }
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
     *
     * The font bytes are streamed through an anonymous pipe rather than handing the original
     * URI's file descriptor to system_server. The original FD is anchored on the source inode
     * (e.g. a file on `/sdcard`, labelled `fuse:s0`), and SELinux denies system_server `read`
     * on that label regardless of who opened the FD. By copying through a pipe whose inode
     * carries this app's domain (`appdomain:fifo_file`), system_server reads against a label
     * that is already in its allowlist.
     */
    suspend fun installFromUri(uri: Uri): Result<String> =
        withContext(bgDispatcher) {
            val manager = fontManager
                ?: return@withContext Result.failure(IllegalStateException("FontManager unavailable"))
            Log.d(TAG, "installFromUri: streaming $uri through pipe")
            runCatching { installViaPipe(manager, uri) }
                .also { result ->
                    result.onFailure { e -> Log.e(TAG, "installFromUri: failed", e) }
                }
        }

    private suspend fun installViaPipe(manager: FontManager, uri: Uri): String =
        coroutineScope {
            val pipe = ParcelFileDescriptor.createReliablePipe()
            val readSide = pipe[0]
            val writeSide = pipe[1]

            val writerDeferred = async(bgDispatcher) {
                try {
                    FileOutputStream(writeSide.fileDescriptor).use { out ->
                        val input = context.contentResolver.openInputStream(uri)
                            ?: throw IOException("openInputStream returned null for $uri")
                        input.use { it.copyTo(out) }
                    }
                    writeSide.close()
                } catch (t: Throwable) {
                    runCatching { writeSide.closeWithError(t.message ?: "font copy failed") }
                    throw t
                }
            }

            try {
                val familyName = readSide.use { manager.installCustomFontFamilyFromFile(it) }
                writerDeferred.await()
                _installedFamilies.value = (_installedFamilies.value + familyName).distinct()
                Log.d(TAG, "installFromUri: success, familyName=$familyName")
                familyName
            } catch (t: Throwable) {
                writerDeferred.cancel()
                throw t
            }
        }

    companion object {
        private const val TAG = "FontRepository"
    }
}
