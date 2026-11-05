/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.customization.picker.color.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.themepicker.R

/**
 * UI for the drill down to fine tune a selected option. This includes a floating bottom sheet and a
 * cancel and confirm button. While [isConfirmInProgress], the confirm button shows progress. Cancel
 * and back do nothing while [isCancelEnabled] is `false`.
 */
@Composable
fun DrillDownFloatingSheet(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    isConfirmEnabled: Boolean = true,
    isConfirmInProgress: Boolean = false,
    isCancelEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme: CustomColorScheme = LocalAnimatedColorScheme.current

    // Handle back navigation when color variant picker is active.
    BackHandler {
        if (isCancelEnabled) {
            onCancel()
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier.wrapContentSize()
                    .padding(horizontal = 16.dp)
                    .clip(shape = RoundedCornerShape(28.dp))
                    .drawBehind { drawRect(colorScheme.surfaceBright) }
        ) {
            content()
        }

        Spacer(
            modifier =
                Modifier.height(
                    dimensionResource(R.dimen.floating_sheet_tab_toolbar_vertical_margin)
                )
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.spacedBy(dimensionResource(R.dimen.clock_font_apply_padding_start)),
        ) {
            Button(
                modifier = Modifier.width(72.dp).height(56.dp),
                onClick = onCancel,
                enabled = isCancelEnabled,
                colors =
                    ButtonColors(
                        containerColor = colorScheme.secondaryContainer,
                        contentColor = colorScheme.onSecondaryContainer,
                        disabledContainerColor = colorScheme.onSurface,
                        disabledContentColor = colorScheme.onSurface,
                    ),
            ) {
                Icon(
                    painter = painterResource(com.android.wallpaper.R.drawable.ic_close),
                    contentDescription = stringResource(R.string.color_variant_editor_revert),
                    modifier = Modifier.size(24.dp),
                )
            }
            Button(
                modifier = Modifier.width(72.dp).height(56.dp),
                onClick = onConfirm,
                enabled = isConfirmEnabled && !isConfirmInProgress,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary,
                    ),
            ) {
                val confirmLabel = stringResource(R.string.color_variant_editor_apply)
                if (isConfirmInProgress) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(24.dp).semantics { contentDescription = confirmLabel },
                        color = colorScheme.onSurface,
                        strokeWidth = 2.5.dp,
                    )
                } else {
                    Icon(
                        painter =
                            painterResource(com.android.wallpaper.R.drawable.ic_check_wallpaper),
                        contentDescription = confirmLabel,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
