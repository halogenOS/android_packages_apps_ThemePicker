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

package com.android.wallpaper.customization.ui.binder

import android.app.AlertDialog
import android.net.Uri
import android.os.PowerManager
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.customization.picker.font.ui.compose.FontSheet
import com.android.themepicker.R
import com.android.wallpaper.customization.ui.viewmodel.FontPickerViewModel
import com.android.wallpaper.customization.ui.viewmodel.ThemePickerCustomizationOptionsViewModel
import kotlinx.coroutines.launch

object FontFloatingSheetBinder {

    fun bind(
        view: ComposeView,
        optionsViewModel: ThemePickerCustomizationOptionsViewModel,
        lifecycleOwner: LifecycleOwner,
        launchFontFilePicker: ((onResult: (List<Uri>) -> Unit) -> Unit)?,
    ) {
        val viewModel = optionsViewModel.fontPickerViewModel
        val onInstallFont =
            launchFontFilePicker?.let { launcher ->
                { installFonts(launcher, view, viewModel, lifecycleOwner) }
            }
        // Make sure Composable lifecycle aligns with fragment lifecycle
        view.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycleOwner)
        )
        view.setContent { FontSheet(viewModel = viewModel, onInstallFont = onInstallFont) }

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.refresh()
                launch {
                    viewModel.restartRequired.collect {
                        AlertDialog.Builder(view.context)
                            .setTitle(R.string.font_picker_restart_dialog_title)
                            .setMessage(R.string.font_picker_restart_dialog_message)
                            .setPositiveButton(R.string.font_picker_restart_dialog_positive) { _, _ ->
                                view.context
                                    .getSystemService(PowerManager::class.java)
                                    ?.reboot(null)
                            }
                            .setNegativeButton(R.string.font_picker_restart_dialog_negative, null)
                            .setCancelable(false)
                            .show()
                    }
                }
            }
        }
    }

    private fun installFonts(
        launcher: (onResult: (List<Uri>) -> Unit) -> Unit,
        view: ComposeView,
        viewModel: FontPickerViewModel,
        lifecycleOwner: LifecycleOwner,
    ) {
        launcher { uris ->
            if (uris.isEmpty()) return@launcher
            lifecycleOwner.lifecycleScope.launch {
                viewModel.installFromUris(uris).onFailure {
                    val msg = view.context.getString(R.string.font_picker_install_error_generic)
                    Toast.makeText(view.context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
