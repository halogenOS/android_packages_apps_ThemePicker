// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0
package com.android.customization.picker.color.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.customization.picker.color.shared.model.ColorStyle
import com.android.customization.picker.color.ui.viewmodel.ColorDraft
import com.android.customization.picker.color.ui.viewmodel.ColorEditorStatus
import com.android.customization.picker.color.ui.viewmodel.ColorEditorViewModel
import com.android.customization.picker.color.ui.viewmodel.Hsv
import com.android.customization.picker.color.ui.viewmodel.StyleOptionViewModel
import com.android.customization.picker.color.ui.viewmodel.SwatchFill
import com.android.customization.picker.color.ui.viewmodel.colors
import com.android.customization.picker.color.ui.viewmodel.label
import com.android.customization.picker.common.ui.compose.LabeledOption
import com.android.themepicker.R

private val StyleSwatchSize = 40.dp

/** Editor for a new color of the user or one they saved: name, seed and style. */
@Composable
internal fun ColorEditor(
    viewModel: ColorEditorViewModel,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val draft: ColorDraft = viewModel.draft.collectAsStateWithLifecycle().value ?: return
    val status: ColorEditorStatus by viewModel.status.collectAsStateWithLifecycle()
    val styleOptions: List<StyleOptionViewModel> by
        viewModel.styleOptions.collectAsStateWithLifecycle(initialValue = emptyList())
    val isEnabled = !status.isBusy
    val colorScheme = MaterialTheme.colorScheme
    val hueLabel = stringResource(R.string.color_hue_label)

    DrillDownFloatingSheet(
        onCancel = viewModel::close,
        onConfirm = viewModel::save,
        isConfirmEnabled = draft.canSave && isEnabled,
        isConfirmInProgress = status == ColorEditorStatus.SAVING,
        isCancelEnabled = isEnabled,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text =
                    stringResource(
                        if (draft.editing == null) R.string.new_color_title
                        else R.string.edit_color_title
                    ),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            OutlinedTextField(
                value = draft.name,
                onValueChange = viewModel::changeName,
                modifier = Modifier.fillMaxWidth(),
                enabled = isEnabled,
                label = { Text(stringResource(R.string.color_name_label)) },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Swatch(
                    fill = SwatchFill.Seed(draft.seed.argb),
                    isDarkMode = isDarkMode,
                    modifier = Modifier.size(56.dp),
                )
                HexField(
                    text = draft.hexText,
                    isComplete = draft.isHexComplete,
                    enabled = isEnabled,
                    onTextChange = viewModel::changeHex,
                    modifier = Modifier.weight(1f),
                )
            }
            SaturationValuePicker(
                hsv = draft.hsv,
                enabled = isEnabled,
                onChange = viewModel::changeHsv,
                modifier = Modifier.fillMaxWidth().height(112.dp),
            )
            BrushSlider(
                brush = Brush.horizontalGradient(colors = HueColors),
                sliderPosition = draft.hsv.hue,
                valueRange = 0f..Hsv.FULL_TURN,
                onValueChange = { hue -> viewModel.changeHsv(draft.hsv.copy(hue = hue)) },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = hueLabel },
            )
            StyleOptions(
                options = styleOptions,
                selected = draft.style,
                isDarkMode = isDarkMode,
                enabled = isEnabled,
                onSelect = viewModel::changeStyle,
            )
            EditorMessage(status)
            if (draft.editing != null) {
                TextButton(
                    onClick = viewModel::delete,
                    enabled = isEnabled,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(
                        painter = painterResource(com.android.wallpaper.R.drawable.ic_delete),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp).size(18.dp),
                    )
                    Text(stringResource(R.string.delete_color_label))
                }
            }
        }
    }
}

/** Field for the seed as six hex digits, which shows an error while fewer are typed. */
@Composable
private fun HexField(
    text: String,
    isComplete: Boolean,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier,
        enabled = enabled,
        label = { Text(stringResource(R.string.color_hex_label)) },
        prefix = { Text(stringResource(R.string.color_hex_prefix)) },
        isError = !isComplete,
        supportingText =
            if (isComplete) null
            else {
                { Text(stringResource(R.string.color_hex_incomplete)) }
            },
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
    )
}

/**
 * Square that picks saturation from left to right and brightness from top to bottom, for the hue
 * set by the slider below it.
 */
@Composable
private fun SaturationValuePicker(
    hsv: Hsv,
    enabled: Boolean,
    onChange: (Hsv) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.color_saturation_value_label)
    val currentHsv by rememberUpdatedState(hsv)
    val currentOnChange by rememberUpdatedState(onChange)

    fun pick(position: Offset, width: Int, height: Int) {
        currentOnChange(
            currentHsv.copy(
                saturation = (position.x / width).coerceIn(0f, 1f),
                value = 1f - (position.y / height).coerceIn(0f, 1f),
            )
        )
    }

    Canvas(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .semantics { contentDescription = label }
                .pointerInput(enabled) {
                    if (enabled) {
                        detectTapGestures { pick(it, size.width, size.height) }
                    }
                }
                .pointerInput(enabled) {
                    if (enabled) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            pick(change.position, size.width, size.height)
                        }
                    }
                }
    ) {
        drawRect(
            brush =
                Brush.horizontalGradient(
                    listOf(Color.White, Color.hsv(hsv.hue, saturation = 1f, value = 1f))
                )
        )
        drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val thumbCenter =
            Offset(x = hsv.saturation * size.width, y = (1f - hsv.value) * size.height)
        val thumbRadius = 11.dp.toPx()
        drawCircle(color = Color(hsv.toSeed().argb), radius = thumbRadius, center = thumbCenter)
        drawCircle(
            color = Color.White,
            radius = thumbRadius,
            center = thumbCenter,
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

@Composable
private fun StyleOptions(
    options: List<StyleOptionViewModel>,
    selected: ColorStyle,
    isDarkMode: Boolean,
    enabled: Boolean,
    onSelect: (ColorStyle) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        options.forEach { option ->
            LabeledOption(
                label = stringResource(option.style.label()),
                isSelected = option.style == selected,
                onClick = { onSelect(option.style) },
                size = StyleSwatchSize,
                cornerRadius = StyleSwatchSize / 2,
                enabled = enabled,
            ) {
                Canvas(modifier = it) { drawQuadrants(option.icon.colors(isDarkMode)) }
            }
        }
    }
}

@Composable
private fun EditorMessage(status: ColorEditorStatus) {
    val message =
        when (status) {
            ColorEditorStatus.EDITING,
            ColorEditorStatus.SAVING,
            ColorEditorStatus.DELETING -> return
            ColorEditorStatus.SAVE_FAILED -> R.string.color_save_failed
            ColorEditorStatus.DELETE_FAILED -> R.string.color_delete_failed
        }
    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

private val HueColors =
    (0..Hsv.FULL_TURN.toInt() step 30).map { hue ->
        Color.hsv(hue.toFloat(), saturation = 1f, value = 1f)
    }
