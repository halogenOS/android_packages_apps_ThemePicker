// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0
package com.android.customization.picker.color.ui.viewmodel

import android.graphics.Color
import com.android.customization.model.color.ColorOption
import com.android.customization.model.color.ColorProviderUtil
import com.android.customization.picker.color.domain.interactor.ColorPickerInteractor
import com.android.customization.picker.color.shared.model.ColorStyle
import com.android.customization.picker.color.shared.model.SavedColor
import com.android.customization.picker.color.shared.model.SavedColorId
import com.android.customization.picker.color.shared.model.SeedColor
import com.android.systemui.monet.ColorScheme
import java.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A color as hue in degrees, and saturation and value from 0 to 1. */
data class Hsv(val hue: Float, val saturation: Float, val value: Float) {
    fun toSeed() = SeedColor(Color.HSVToColor(floatArrayOf(hue, saturation, value)))

    companion object {
        const val FULL_TURN = 360f

        fun of(seed: SeedColor): Hsv {
            val hsv = FloatArray(3).also { Color.colorToHSV(seed.argb, it) }
            return Hsv(hue = hsv[0], saturation = hsv[1], value = hsv[2])
        }
    }
}

/** Color being made or changed in the color editor. */
data class ColorDraft(
    /** The saved color being changed, or `null` for a new color. */
    val editing: SavedColor?,
    val name: String,
    /** The seed of the last complete hex digits or of the picker position. */
    val seed: SeedColor,
    /** The hex field as typed, which can hold fewer than six digits. */
    val hexText: String,
    /**
     * The position of the picker. While the brightness is at zero, the draft still has the hue and
     * saturation the person set.
     */
    val hsv: Hsv,
    val style: ColorStyle,
) {
    val isHexComplete: Boolean
        get() = SeedColor.parseHex(hexText) != null

    val canSave: Boolean
        get() = SavedColor.isValidName(name) && isHexComplete

    /** The color this draft saves, or `null` while it cannot be saved. */
    fun toSavedColor(now: Instant): SavedColor? =
        if (canSave) {
            SavedColor(
                id = editing?.id ?: SavedColorId.create(),
                name = name.trim(),
                seed = seed,
                style = style,
                addedAt = editing?.addedAt ?: now,
            )
        } else {
            null
        }

    companion object {
        fun of(editing: SavedColor?, name: String, seed: SeedColor, style: ColorStyle) =
            ColorDraft(
                editing = editing,
                name = name,
                seed = seed,
                hexText = seed.hexDigits,
                hsv = Hsv.of(seed),
                style = style,
            )
    }
}

enum class ColorEditorStatus(
    /** Whether a change is being written, which keeps every control of the editor disabled. */
    val isBusy: Boolean
) {
    EDITING(isBusy = false),
    SAVING(isBusy = true),
    DELETING(isBusy = true),
    SAVE_FAILED(isBusy = false),
    DELETE_FAILED(isBusy = false),
}

/** A style the editor offers, with the palette it builds from the seed of the draft. */
data class StyleOptionViewModel(val style: ColorStyle, val icon: ColorOptionIconViewModel)

/** The editor of colors saved by the user: the draft, writing it and deleting a saved color. */
@OptIn(ExperimentalCoroutinesApi::class)
class ColorEditorViewModel(
    private val interactor: ColorPickerInteractor,
    private val scope: CoroutineScope,
    backgroundDispatcher: CoroutineDispatcher,
    /** Called with the id of a color once it is stored. */
    private val onSaved: (SavedColorId) -> Unit,
) {
    private val _draft = MutableStateFlow<ColorDraft?>(null)
    /** The color open in the editor, or `null` while the editor is closed */
    val draft = _draft.asStateFlow()
    private val _status = MutableStateFlow(ColorEditorStatus.EDITING)
    val status = _status.asStateFlow()
    private var pendingWrite: Job? = null

    /** The option previewing the draft, built once for each change of name, seed or style */
    val previewOption: StateFlow<ColorOption?> =
        draft
            .map { draft -> draft?.let { Triple(it.name, it.seed, it.style) } }
            .distinctUntilChanged()
            .mapLatest { inputs ->
                inputs?.let { (name, seed, style) ->
                    interactor.buildPreviewOption(name, seed, style)
                }
            }
            .flowOn(backgroundDispatcher)
            .stateIn(scope, SharingStarted.WhileSubscribed(), null)

    /** Every [ColorStyle], each with the palette it builds from the seed of the draft */
    val styleOptions: Flow<List<StyleOptionViewModel>> =
        draft
            .map { it?.seed }
            .distinctUntilChanged()
            .mapLatest { seed ->
                seed
                    ?.let { ColorStyle.entries.map { style -> buildStyleOption(it, style) } }
                    .orEmpty()
            }
            .flowOn(backgroundDispatcher)

    fun startNew(from: ColorDetails) = open(ColorDraft.of(null, "", from.seed, from.style))

    fun startEditing(color: SavedColor) =
        open(ColorDraft.of(color, color.name, color.seed, color.style))

    private fun open(draft: ColorDraft) {
        _status.value = ColorEditorStatus.EDITING
        _draft.value = draft
    }

    fun changeName(name: String) = update { it.copy(name = name) }

    fun changeStyle(style: ColorStyle) = update { it.copy(style = style) }

    /** Takes the hex digits of [text]. Only six of them change the seed. */
    fun changeHex(text: String) = update { draft ->
        val digits = SeedColor.filterHexDigits(text)
        val seed = SeedColor.parseHex(digits)
        draft.copy(
            hexText = digits,
            seed = seed ?: draft.seed,
            hsv = seed?.let(Hsv::of) ?: draft.hsv,
        )
    }

    fun changeHsv(hsv: Hsv) = update { draft ->
        val seed = hsv.toSeed()
        draft.copy(hsv = hsv, seed = seed, hexText = seed.hexDigits)
    }

    private fun update(change: (ColorDraft) -> ColorDraft) {
        if (_status.value.isBusy) return
        _draft.value = _draft.value?.let(change)
        _status.value = ColorEditorStatus.EDITING
    }

    /** Saves the draft, then closes the editor. The editor remains open if saving fails */
    fun save() {
        if (_status.value.isBusy) return
        val color = _draft.value?.toSavedColor(now = Instant.now()) ?: return
        _status.value = ColorEditorStatus.SAVING
        pendingWrite =
            scope.launch {
                interactor
                    .saveColor(color)
                    .onSuccess {
                        onSaved(color.id)
                        close()
                    }
                    .onFailure { _status.value = ColorEditorStatus.SAVE_FAILED }
            }
    }

    /** Deletes the color open in the editor. The editor remains open if deleting fails */
    fun delete() {
        if (_status.value.isBusy) return
        val color = _draft.value?.editing ?: return
        _status.value = ColorEditorStatus.DELETING
        pendingWrite =
            scope.launch {
                interactor
                    .deleteColor(color.id)
                    .onSuccess { close() }
                    .onFailure { _status.value = ColorEditorStatus.DELETE_FAILED }
            }
    }

    /** Closes the editor. A write still running finishes on disk, but nothing follows it here. */
    fun close() {
        pendingWrite?.cancel()
        pendingWrite = null
        _draft.value = null
        _status.value = ColorEditorStatus.EDITING
    }

    private fun buildStyleOption(seed: SeedColor, style: ColorStyle): StyleOptionViewModel {
        val light =
            ColorProviderUtil.getLightThreeColorPreview(
                ColorScheme(seed.argb, /* darkTheme= */ false, style.themeStyle)
            )
        val dark =
            ColorProviderUtil.getDarkThreeColorPreview(
                ColorScheme(seed.argb, /* darkTheme= */ true, style.themeStyle)
            )
        return StyleOptionViewModel(
            style = style,
            icon =
                ColorOptionIconViewModel(
                    lightThemeColor0 = light[0],
                    lightThemeColor1 = light[1],
                    lightThemeColor2 = light[2],
                    lightThemeColor3 = light[3],
                    darkThemeColor0 = dark[0],
                    darkThemeColor1 = dark[1],
                    darkThemeColor2 = dark[2],
                    darkThemeColor3 = dark[3],
                ),
        )
    }
}
