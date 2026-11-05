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

package com.android.wallpaper.picker.common.preview.ui.compose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.visible
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.theme.PlatformTheme
import com.android.customization.model.color.ColorOption
import com.android.customization.picker.color.ui.compose.ColorPreviewTheme
import com.android.customization.picker.color.ui.viewmodel.ColorPickerViewModel
import com.android.customization.picker.mode.ui.viewmodel.DarkModeViewModel
import com.android.systemui.monet.ColorScheme
import com.android.wallpaper.picker.common.preview.ui.viewmodel.WorkspacePreviewScreen
import com.android.wallpaper.util.DisplayUtils

/** Displays [previewScreen] in the colors, style and dark theme being previewed. */
@Composable
fun ColorPreviewScreens(
    previewScreen: WorkspacePreviewScreen,
    colorPickerViewModel: ColorPickerViewModel,
    darkModeViewModel: DarkModeViewModel,
    displayUtils: DisplayUtils,
) {
    val previewingColorOption: ColorOption? by
        colorPickerViewModel.tempPreviewingColorOption.collectAsStateWithLifecycle(
            initialValue = null
        )
    val previewingIsDarkMode: Boolean by
        darkModeViewModel.previewingIsDarkMode.collectAsStateWithLifecycle(initialValue = false)
    val previewingStyle: Int? by
        colorPickerViewModel.previewingStyle.collectAsStateWithLifecycle(initialValue = null)

    PlatformTheme {
        val scheme =
            remember(previewingColorOption, previewingIsDarkMode, previewingStyle) {
                previewingColorOption?.let {
                    ColorScheme(it.seedColor, previewingIsDarkMode, previewingStyle ?: it.style)
                        .materialScheme
                }
            }

        ColorPreviewTheme(scheme) {
            var size by remember { mutableStateOf(IntSize.Zero) }
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .onSizeChanged { newSize -> size = newSize }
                        .wrapContentSize(unbounded = true)
            ) {
                val displaySize = displayUtils.getActiveDisplaySize(context = LocalContext.current)
                val displayWidth = displaySize.x
                val displayHeight = displaySize.y
                val displayWidthDp = with(LocalDensity.current) { displayWidth.toDp() }
                val displayHeightDp = with(LocalDensity.current) { displayHeight.toDp() }
                val contentScale = size.width / displayWidth.toFloat()

                AnimatedContent(
                    targetState = previewScreen,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                ) { value ->
                    // Render content in full screen, then scale down to fit preview
                    Box(
                        modifier =
                            Modifier.size(width = displayWidthDp, height = displayHeightDp)
                                .align(Alignment.Center)
                                .scale(contentScale)
                    ) {
                        SkeletonShade(
                            isDualShade = displayWidthDp > 600.dp,
                            modifier = Modifier.visible(value == WorkspacePreviewScreen.SHADE),
                        )
                        SkeletonWidgetPicker(
                            modifier =
                                Modifier.visible(value == WorkspacePreviewScreen.WIDGET_PICKER)
                        )
                    }
                }
            }
        }
    }
}
