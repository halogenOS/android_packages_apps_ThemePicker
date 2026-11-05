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
import com.android.customization.picker.font.shared.model.FontOrigin
import com.android.customization.picker.font.shared.model.InstalledFontFamily
import com.android.themepicker.R
import com.android.wallpaper.picker.common.text.ui.viewmodel.Text
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn

class FontPickerViewModel
@AssistedInject
constructor(
    @ApplicationContext private val context: Context,
    private val interactor: FontInteractor,
    @Assisted private val viewModelScope: CoroutineScope,
) {

    private val activeFamily = interactor.activeFamily

    /** Non-null = user has picked something, awaiting Apply. Null = mirror current active. */
    private val overridingFamily = MutableStateFlow<Selection?>(null)

    /** Emits when a font change was successfully applied and the system should be restarted. */
    val restartRequired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

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

    // The default font first, then the installed families by name
    private val fonts: Flow<List<FontViewModel>> =
        combine(interactor.families, interactor.defaultFamily) { families, default ->
                // The framework lists the families in an unspecified order.
                val sortedFamilies =
                    families.sortedWith(
                        compareBy(String.CASE_INSENSITIVE_ORDER, InstalledFontFamily::displayName)
                            .thenBy(InstalledFontFamily::name)
                    )
                listOf(describeDefaultFont(default)) + sortedFamilies.map(::describeFont)
            }
            .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    /** The font being previewed. */
    val previewingFont: Flow<FontViewModel?> =
        combine(previewingFamily, fonts) { family, fonts ->
                fonts.firstOrNull { it.family == family }
            }
            .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    /** The fonts of the Font sheet in sections, leaving out empty ones. */
    val sections: Flow<List<FontSectionViewModel>> =
        fonts
            .map { allFonts ->
                allFonts
                    .groupBy { it.origin }
                    .toSortedMap(compareBy { it.sectionRank })
                    .map { (origin, sectionFonts) ->
                        FontSectionViewModel(origin = origin, fonts = sectionFonts)
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
                        restartRequired.tryEmit(Unit)
                    }
                }
            }
        }

    suspend fun refresh() = interactor.refresh()

    suspend fun remove(familyName: String): Int = interactor.remove(familyName)

    suspend fun installFromUri(uri: Uri): Result<String> = interactor.installFromUri(uri)

    /**
     * Installs the user's picked file(s). A single ZIP archive is expanded into its font entries;
     * otherwise each URI is treated as a font file and the set is installed as one batch, with
     * variants clustered into families by the framework.
     */
    suspend fun installFromUris(uris: List<Uri>): Result<List<String>> {
        if (uris.isEmpty()) return Result.success(emptyList())
        if (uris.size == 1 && isZip(uris.first())) {
            return interactor.installFromZip(uris.first())
        }
        return interactor.installFromUris(uris)
    }

    private fun isZip(uri: Uri): Boolean {
        val type = context.contentResolver.getType(uri)
        if (type == "application/zip" || type == "application/x-zip-compressed") {
            return true
        }
        val extension = uri.lastPathSegment?.substringAfterLast('.', "")
        return extension.equals("zip", ignoreCase = true)
    }

    fun resetPreview() {
        overridingFamily.value = null
        interactor.clearPending()
    }

    /** Previews [family] until Apply, or the default font when [family] is `null`. */
    fun previewFont(family: String?) {
        overridingFamily.value = family?.let { Selection.Family(it) } ?: Selection.Stock
        interactor.setPending(family)
    }

    private fun describeDefaultFont(defaultFamily: String?) =
        FontViewModel(
            family = null,
            typefaceFamily = defaultFamily ?: STOCK_TYPEFACE_FAMILY,
            name = Text.Resource(R.string.font_picker_stock_option),
            origin = FontOrigin.INCLUDED,
        )

    private fun describeFont(family: InstalledFontFamily) =
        FontViewModel(
            family = family.name,
            typefaceFamily = family.name,
            name = Text.Loaded(family.displayName),
            origin = family.origin,
        )

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
        private const val STOCK_TYPEFACE_FAMILY = "sans-serif"
    }
}
