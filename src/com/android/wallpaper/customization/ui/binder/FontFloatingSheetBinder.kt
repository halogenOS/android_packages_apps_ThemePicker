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
import android.app.StatusBarManager
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.ServiceSpecificException
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.customization.picker.common.ui.view.SingleRowListItemSpacing
import com.android.themepicker.R
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerHomeCustomizationOption.FONT
import com.android.wallpaper.customization.ui.viewmodel.ThemePickerCustomizationOptionsViewModel
import com.android.wallpaper.picker.customization.ui.binder.ColorUpdateBinder
import com.android.wallpaper.picker.customization.ui.viewmodel.ColorUpdateViewModel
import com.android.wallpaper.picker.option.ui.adapter.OptionItemAdapter2
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch

object FontFloatingSheetBinder {

    fun bind(
        view: View,
        optionsViewModel: ThemePickerCustomizationOptionsViewModel,
        colorUpdateViewModel: ColorUpdateViewModel,
        lifecycleOwner: LifecycleOwner,
        backgroundDispatcher: CoroutineDispatcher,
        launchFontFilePicker: ((onResult: (Uri?) -> Unit) -> Unit)?,
    ) {
        val viewModel = optionsViewModel.fontPickerViewModel
        val isFloatingSheetActive = { optionsViewModel.selectedOption.value == FONT }

        val container = view.requireViewById<ViewGroup>(R.id.floating_sheet_content_container)
        ColorUpdateBinder.bind(
            setColor = { color ->
                DrawableCompat.setTint(DrawableCompat.wrap(container.background), color)
            },
            color = colorUpdateViewModel.colorSurfaceBright,
            shouldAnimate = isFloatingSheetActive,
            lifecycleOwner = lifecycleOwner,
        )

        val adapter =
            createFontOptionAdapter(
                colorUpdateViewModel = colorUpdateViewModel,
                shouldAnimateColor = isFloatingSheetActive,
                lifecycleOwner = lifecycleOwner,
                backgroundDispatcher = backgroundDispatcher,
            )
        val recycler =
            view.requireViewById<RecyclerView>(R.id.font_options).also {
                it.initFontOptionList(view.context, adapter)
            }

        val installButton = view.requireViewById<View>(R.id.install_font_button)
        installButton.isEnabled = launchFontFilePicker != null
        installButton.setOnClickListener {
            val launcher = launchFontFilePicker ?: return@setOnClickListener
            launcher { uri ->
                uri ?: return@launcher
                lifecycleOwner.lifecycleScope.launch {
                    viewModel.installFromUri(uri).onFailure { error ->
                        val msg =
                            if (error is ServiceSpecificException
                                && error.message?.contains("wght") == true
                            ) {
                                view.context.getString(
                                    R.string.font_picker_install_error_not_variable
                                )
                            } else {
                                view.context.getString(R.string.font_picker_install_error_generic)
                            }
                        Toast.makeText(view.context, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.refresh()
                launch {
                    viewModel.options.collect { options ->
                        adapter.setItems(options) {
                            val indexToFocus =
                                options.indexOfFirst { it.isSelected.value }.coerceAtLeast(0)
                            (recycler.layoutManager as LinearLayoutManager).scrollToPosition(
                                indexToFocus
                            )
                        }
                    }
                }
                launch {
                    viewModel.restartSystemUiRequired.collect {
                        AlertDialog.Builder(view.context)
                            .setTitle(R.string.font_picker_restart_dialog_title)
                            .setMessage(R.string.font_picker_restart_dialog_message)
                            .setPositiveButton(R.string.font_picker_restart_dialog_positive) { _, _ ->
                                view.context
                                    .getSystemService(StatusBarManager::class.java)
                                    ?.restartSystemUI()
                            }
                            .setNegativeButton(R.string.font_picker_restart_dialog_negative, null)
                            .setCancelable(false)
                            .show()
                    }
                }
            }
        }
    }

    private fun createFontOptionAdapter(
        colorUpdateViewModel: ColorUpdateViewModel,
        shouldAnimateColor: () -> Boolean,
        lifecycleOwner: LifecycleOwner,
        backgroundDispatcher: CoroutineDispatcher,
    ): OptionItemAdapter2<String?> =
        OptionItemAdapter2(
            layoutResourceId = R.layout.font_option,
            lifecycleOwner = lifecycleOwner,
            backgroundDispatcher = backgroundDispatcher,
            bindPayload = { view: View, familyName: String? ->
                val preview = view.findViewById<TextView>(R.id.font_preview)
                preview?.typeface =
                    if (familyName == null) Typeface.DEFAULT
                    else runCatching { Typeface.create(familyName, Typeface.NORMAL) }
                        .getOrDefault(Typeface.DEFAULT)
                return@OptionItemAdapter2 null
            },
            colorUpdateViewModel = WeakReference(colorUpdateViewModel),
            shouldAnimateColor = shouldAnimateColor,
        )

    private fun RecyclerView.initFontOptionList(
        context: Context,
        adapter: OptionItemAdapter2<String?>,
    ) {
        this.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        addItemDecoration(
            SingleRowListItemSpacing(
                edgeItemSpacePx =
                    context.resources.getDimensionPixelSize(
                        R.dimen.floating_sheet_content_horizontal_padding
                    ),
                itemHorizontalSpacePx =
                    context.resources.getDimensionPixelSize(
                        R.dimen.floating_sheet_grid_list_item_horizontal_space
                    ),
            )
        )
        this.adapter = adapter
    }
}
