/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.customization.picker.color.ui.viewmodel

import android.content.Context
import android.content.theming.ThemeStyle
import android.platform.test.annotations.EnableFlags
import android.platform.test.flag.junit.SetFlagsRule
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.customization.model.color.ColorOption
import com.android.customization.model.color.ColorProviderUtil
import com.android.customization.module.logging.TestThemesUserEventLogger
import com.android.customization.picker.color.data.repository.FakeColorPickerRepository
import com.android.customization.picker.color.domain.interactor.ColorPickerInteractor
import com.android.customization.picker.color.shared.model.ColorType
import com.android.customization.picker.mode.data.repository.DarkModeStateRepository
import com.android.wallpaper.Flags.FLAG_COLOR_PICKER_UPDATE_FLAG
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.picker.customization.ui.viewmodel.ColorUpdateViewModel
import com.android.wallpaper.testing.collectLastValue
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.internal.lifecycle.RetainedLifecycleImpl
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@HiltAndroidTest
@OptIn(ExperimentalCoroutinesApi::class)
@SmallTest
@RunWith(RobolectricTestRunner::class)
class ColorPickerViewModelTest {
    @get:Rule var hiltRule = HiltAndroidRule(this)
    @get:Rule val setFlagsRule = SetFlagsRule()

    private val logger = TestThemesUserEventLogger()
    private lateinit var underTest: ColorPickerViewModel
    private lateinit var colorUpdateViewModel: ColorUpdateViewModel

    private lateinit var context: Context
    private lateinit var testScope: TestScope

    @Inject lateinit var repository: FakeColorPickerRepository
    @Inject lateinit var interactor: ColorPickerInteractor
    @Inject lateinit var darkModeStateRepository: DarkModeStateRepository

    @Before
    fun setUp() {
        hiltRule.inject()

        context = InstrumentationRegistry.getInstrumentation().targetContext
        val testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher)

        colorUpdateViewModel =
            ColorUpdateViewModel(context, RetainedLifecycleImpl(), darkModeStateRepository)

        underTest =
            ColorPickerViewModel(
                interactor = interactor,
                logger = logger,
                colorUpdateViewModel = colorUpdateViewModel,
                viewModelScope = testScope.backgroundScope,
                baseFlags = BaseFlags.get(context),
                backgroundDispatcher = testDispatcher,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    @EnableFlags(FLAG_COLOR_PICKER_UPDATE_FLAG)
    fun confirmAndCancelStyleOptionSelection_colorPickerUpdate() =
        testScope.runTest {
            setupColorPickerUpdateTest()
            val previewingStyle = collectLastValue(underTest.previewingStyle)
            assertThat(previewingStyle()).isEqualTo(ThemeStyle.TONAL_SPOT)

            // Select a new style
            underTest.onStyleOptionClick(ThemeStyle.VIBRANT)
            assertThat(previewingStyle()).isEqualTo(ThemeStyle.VIBRANT)

            // Confirm style selection
            underTest.confirmStyleOptionSelection()
            assertThat(previewingStyle()).isEqualTo(ThemeStyle.VIBRANT)

            // Select a new style
            underTest.onStyleOptionClick(ThemeStyle.SPRITZ)
            assertThat(previewingStyle()).isEqualTo(ThemeStyle.SPRITZ)

            // Cancel style selection
            underTest.cancelStyleOptionSelection()
            assertThat(previewingStyle()).isEqualTo(ThemeStyle.VIBRANT)

            // Reset style selection
            underTest.resetStyleOptionSelection()
            assertThat(previewingStyle()).isEqualTo(ThemeStyle.TONAL_SPOT)
        }

    @Test
    @EnableFlags(FLAG_COLOR_PICKER_UPDATE_FLAG)
    fun doubleConfirmStyleOptionSelection_colorPickerUpdate() =
        testScope.runTest {
            setupColorPickerUpdateTest()
            val previewingStyle = collectLastValue(underTest.previewingStyle)
            assertThat(previewingStyle()).isEqualTo(ThemeStyle.TONAL_SPOT)

            // Select a new style
            underTest.onStyleOptionClick(ThemeStyle.VIBRANT)
            assertThat(previewingStyle()).isEqualTo(ThemeStyle.VIBRANT)

            // Confirm style selection
            underTest.confirmStyleOptionSelection()
            assertThat(previewingStyle()).isEqualTo(ThemeStyle.VIBRANT)

            // Confirm style selection again without selecting new style
            underTest.confirmStyleOptionSelection()
            assertThat(previewingStyle()).isEqualTo(ThemeStyle.VIBRANT)
        }

    @Test
    @EnableFlags(FLAG_COLOR_PICKER_UPDATE_FLAG)
    fun applyAndResetStyleOptionSelection_colorPickerUpdate() =
        testScope.runTest {
            setupColorPickerUpdateTest()
            val previewingStyle = collectLastValue(underTest.previewingStyle)
            assertThat(previewingStyle()).isEqualTo(ThemeStyle.TONAL_SPOT)

            // Select a new style
            underTest.onStyleOptionClick(ThemeStyle.VIBRANT)
            assertThat(previewingStyle()).isEqualTo(ThemeStyle.VIBRANT)

            // Confirm style selection
            underTest.confirmStyleOptionSelection()
            assertThat(previewingStyle()).isEqualTo(ThemeStyle.VIBRANT)

            // Apply style selection
            applySelection()
            assertThat(previewingStyle()).isEqualTo(ThemeStyle.VIBRANT)

            // Select a new style
            underTest.onStyleOptionClick(ThemeStyle.SPRITZ)
            assertThat(previewingStyle()).isEqualTo(ThemeStyle.SPRITZ)

            // Reset style selection
            underTest.resetStyleOptionSelection()
            assertThat(previewingStyle()).isEqualTo(ThemeStyle.VIBRANT)
        }

    private fun setupColorPickerUpdateTest(): Map<ColorType, List<ColorOption>> {
        // Arrange: set up distinct options for clarity.
        val wallpaperOptions =
            listOf(
                repository.buildWallpaperOption(
                    ColorProviderUtil.COLOR_SOURCE_HOME,
                    ThemeStyle.TONAL_SPOT,
                    1,
                ),
                repository.buildWallpaperOption(
                    ColorProviderUtil.COLOR_SOURCE_HOME,
                    ThemeStyle.VIBRANT,
                    2,
                ),
            )
        val presetOptions = listOf(repository.buildPresetOption(ThemeStyle.EXPRESSIVE, 3))
        // Initially select the first wallpaper option (index 0).
        repository.setOptions(wallpaperOptions, presetOptions, ColorType.WALLPAPER_COLOR, 0)
        return mapOf(
            ColorType.WALLPAPER_COLOR to wallpaperOptions,
            ColorType.PRESET_COLOR to presetOptions,
        )
    }

    /** Simulates a user tapping the apply button, and the apply completes. */
    private suspend fun TestScope.applySelection() {
        val onApply = collectLastValue(underTest.onApply)()
        testScope.launch { onApply?.invoke() }
        colorUpdateViewModel.updateColors()
    }
}
