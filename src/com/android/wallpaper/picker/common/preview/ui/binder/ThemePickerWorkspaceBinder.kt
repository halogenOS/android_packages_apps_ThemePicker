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

package com.android.wallpaper.picker.common.preview.ui.binder

import android.view.SurfaceView
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.LifecycleOwner
import com.android.customization.picker.clock.ui.view.ClockViewFactory
import com.android.wallpaper.customization.ui.viewmodel.ThemePickerCustomizationOptionsViewModel
import com.android.wallpaper.model.PickerPage
import com.android.wallpaper.model.wallpaper.DeviceDisplayType
import com.android.wallpaper.picker.common.preview.ui.compose.ColorPreviewScreens
import com.android.wallpaper.picker.customization.ui.viewmodel.ColorUpdateViewModel
import com.android.wallpaper.picker.customization.ui.viewmodel.CustomizationPickerViewModel2
import com.android.wallpaper.util.DisplayUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemePickerWorkspaceBinder
@Inject
constructor(
    private val defaultWorkspaceBinder: DefaultWorkspaceBinder,
    private val displayUtils: DisplayUtils,
) : WorkspaceBinder {

    override fun bind(
        surfaceView: SurfaceView,
        alternativeWorkspaceView: ComposeView,
        viewModel: CustomizationPickerViewModel2,
        colorUpdateViewModel: ColorUpdateViewModel,
        page: PickerPage,
        deviceDisplayType: DeviceDisplayType,
        lifecycleOwner: LifecycleOwner,
        clockViewFactory: ClockViewFactory,
    ) {
        val optionsViewModel =
            viewModel.customizationOptionsViewModel as ThemePickerCustomizationOptionsViewModel
        val previewScreen = optionsViewModel.workspacePreviewScreen(page)
        if (previewScreen == null) {
            defaultWorkspaceBinder.bind(
                surfaceView = surfaceView,
                alternativeWorkspaceView = alternativeWorkspaceView,
                viewModel = viewModel,
                colorUpdateViewModel = colorUpdateViewModel,
                page = page,
                deviceDisplayType = deviceDisplayType,
                lifecycleOwner = lifecycleOwner,
                clockViewFactory = clockViewFactory,
            )
        }
        // Bind compose view
        previewScreen?.let { screen ->
            alternativeWorkspaceView.apply {
                // Make sure Composable lifecycle aligns with fragment lifecycle
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycleOwner)
                )
                setContent {
                    ColorPreviewScreens(
                        previewScreen = screen,
                        colorPickerViewModel = optionsViewModel.colorPickerViewModel2,
                        darkModeViewModel = optionsViewModel.darkModeViewModel,
                        displayUtils = displayUtils,
                    )
                }
                visibility = View.VISIBLE
            }
        }
    }
}
