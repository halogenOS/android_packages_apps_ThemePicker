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
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
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
     * Install a single font file pointed at by [uri], clustering it into a family by its embedded
     * typographic family name (augmenting an existing family of that name if present).
     *
     * Returns the resolved family name on success.
     */
    suspend fun installFromUri(uri: Uri): Result<String> =
        installFromUris(listOf(uri)).map { it.first() }

    /**
     * Install one or more font files at once. Each file is clustered into a family by its embedded
     * family name; variants sharing a family (e.g. Regular + Bold) are merged into one. Returns the
     * names of the families that were created or augmented.
     */
    suspend fun installFromUris(uris: List<Uri>): Result<List<String>> =
        withContext(bgDispatcher) {
            val manager = fontManager
                ?: return@withContext Result.failure(IllegalStateException("FontManager unavailable"))
            Log.d(TAG, "installFromUris: streaming ${uris.size} file(s) through pipes")
            runCatching {
                installStreams(manager, uris.map { uri -> { openFontStream(uri) } })
            }.onFailure { e -> Log.e(TAG, "installFromUris: failed", e) }
        }

    /**
     * Install every font file contained in the ZIP archive at [uri]. Entries that are not font
     * files are ignored. The clustered families are returned.
     */
    suspend fun installFromZip(uri: Uri): Result<List<String>> =
        withContext(bgDispatcher) {
            val manager = fontManager
                ?: return@withContext Result.failure(IllegalStateException("FontManager unavailable"))
            Log.d(TAG, "installFromZip: expanding $uri")
            runCatching {
                val fonts = readFontEntriesFromZip(uri)
                if (fonts.isEmpty()) {
                    throw IOException("No font files found in archive")
                }
                installStreams(manager, fonts.map { bytes -> { ByteArrayInputStream(bytes) } })
            }.onFailure { e -> Log.e(TAG, "installFromZip: failed", e) }
        }

    private fun openFontStream(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw IOException("openInputStream returned null for $uri")

    /** Reads the font-file entries out of a ZIP archive into memory. Font files are small. */
    private fun readFontEntriesFromZip(uri: Uri): List<ByteArray> {
        val fonts = mutableListOf<ByteArray>()
        ZipInputStream(openFontStream(uri)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && hasFontExtension(entry.name)) {
                    fonts.add(zip.readBytes())
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return fonts
    }

    private fun hasFontExtension(name: String): Boolean =
        FONT_EXTENSIONS.any { name.substringAfterLast('.', "").equals(it, ignoreCase = true) }

    /**
     * Streams each font source through its own anonymous pipe and hands the read ends to the
     * framework in a single batch install.
     *
     * The font bytes are streamed through pipes rather than handing the original URIs' file
     * descriptors to system_server. An original FD is anchored on the source inode (e.g. a file on
     * `/sdcard`, labelled `fuse:s0`), and SELinux denies system_server `read` on that label
     * regardless of who opened the FD. By copying through a pipe whose inode carries this app's
     * domain (`appdomain:fifo_file`), system_server reads against an already-allowlisted label.
     *
     * Writers run concurrently because the framework drains the read ends sequentially within one
     * binder call and pipe buffers are bounded — a writer whose reader has not been reached yet
     * blocks until then, so they must all be in flight at once.
     */
    private suspend fun installStreams(
        manager: FontManager,
        streams: List<() -> InputStream>,
    ): List<String> = coroutineScope {
        val pipes = streams.map { ParcelFileDescriptor.createReliablePipe() }
        val readSides = pipes.map { it[0] }

        val writers = pipes.mapIndexed { index, pipe ->
            val writeSide = pipe[1]
            async(bgDispatcher) {
                try {
                    FileOutputStream(writeSide.fileDescriptor).use { out ->
                        streams[index]().use { it.copyTo(out) }
                    }
                    writeSide.close()
                } catch (t: Throwable) {
                    runCatching { writeSide.closeWithError(t.message ?: "font copy failed") }
                    throw t
                }
            }
        }

        try {
            val families = try {
                manager.installCustomFontFamilyFromFiles(readSides)
            } finally {
                readSides.forEach { runCatching { it.close() } }
            }
            writers.forEach { it.await() }
            _installedFamilies.value = (_installedFamilies.value + families).distinct()
            Log.d(TAG, "installStreams: success, families=$families")
            families
        } catch (t: Throwable) {
            writers.forEach { it.cancel() }
            throw t
        }
    }

    companion object {
        private const val TAG = "FontRepository"
        private val FONT_EXTENSIONS = listOf("ttf", "otf", "ttc", "otc")
    }
}
