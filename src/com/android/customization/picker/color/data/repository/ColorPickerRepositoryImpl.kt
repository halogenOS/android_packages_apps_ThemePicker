/*
 * Copyright (C) 2024 The Android Open Source Project
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
 *
 */
package com.android.customization.picker.color.data.repository

import android.Manifest.permission
import android.app.ThemeManager
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.theming.IThemeSettingsCallback
import android.content.theming.ThemeSettings
import android.content.theming.ThemeStyle
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.graphics.toColorInt
import com.android.customization.model.CustomizationManager
import com.android.customization.model.ResourceConstants
import com.android.customization.model.color.ColorCustomizationManager
import com.android.customization.model.color.ColorOption
import com.android.customization.model.color.ColorOptionImpl
import com.android.customization.model.color.ColorProvider
import com.android.customization.model.color.ColorProviderUtil
import com.android.customization.picker.color.shared.model.ColorStyle
import com.android.customization.picker.color.shared.model.ColorType
import com.android.customization.picker.color.shared.model.SavedColor
import com.android.customization.picker.color.shared.model.SavedColorId
import com.android.customization.picker.color.shared.model.SavedColorOption
import com.android.customization.picker.color.shared.model.SeedColor
import com.android.systemui.monet.ColorScheme
import com.android.themepicker.R
import com.android.wallpaper.config.BaseFlags
import com.android.wallpaper.model.Screen
import com.android.wallpaper.picker.customization.data.content.WallpaperClient
import com.android.wallpaper.picker.di.modules.BackgroundDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.suspendCancellableCoroutine

@Singleton
class ColorPickerRepositoryImpl
@Inject
constructor(
    @ApplicationContext private val appContext: Context,
    @BackgroundDispatcher scope: CoroutineScope,
    @BackgroundDispatcher backgroundDispatcher: CoroutineDispatcher,
    private val colorManager: ColorCustomizationManager,
    private val themeManager: ThemeManager?,
    client: WallpaperClient,
    private val baseFlags: BaseFlags,
    private val savedColorStore: SavedColorStore,
) : ColorPickerRepository {

    // ThemeManager is only non-null when theme service flag is enabled
    private val shouldUseThemeService =
        baseFlags.isColorPickerUpdateEnabled() &&
            themeManager != null &&
            appContext.checkSelfPermission(permission.UPDATE_THEME_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
    private val colorProvider =
        ColorProvider(appContext, appContext.getString(R.string.themes_stub_package))

    init {
        if (shouldUseThemeService) {
            Log.d(TAG, "Using theme service")
        }
    }

    private val wallpaperColorsCallback: Flow<Pair<Screen, WallpaperColors?>> =
        callbackFlow {
                trySend(Pair(Screen.HOME_SCREEN, client.getWallpaperColors(Screen.HOME_SCREEN)))
                trySend(Pair(Screen.LOCK_SCREEN, client.getWallpaperColors(Screen.LOCK_SCREEN)))
                val listener = { colors: WallpaperColors?, which: Int ->
                    if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                        trySend(Pair(Screen.HOME_SCREEN, colors))
                    }
                    if (which and WallpaperManager.FLAG_LOCK != 0) {
                        trySend(Pair(Screen.LOCK_SCREEN, colors))
                    }
                }
                client.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
                awaitClose { client.removeOnColorsChangedListener(listener) }
            }
            .flowOn(backgroundDispatcher)
            // Make this a shared flow to make sure only one listener is added.
            .shareIn(scope = scope, started = SharingStarted.WhileSubscribed(), replay = 1)
    private val homeWallpaperColors: Flow<WallpaperColors?> =
        wallpaperColorsCallback
            .filter { (screen, _) -> screen == Screen.HOME_SCREEN }
            .map { (_, colors) -> colors }
    private val lockWallpaperColors: Flow<WallpaperColors?> =
        wallpaperColorsCallback
            .filter { (screen, _) -> screen == Screen.LOCK_SCREEN }
            .map { (_, colors) -> colors }

    override val colorOptions: Flow<List<Pair<ColorType, List<ColorOption>>>> =
        if (shouldUseThemeService) {
            homeWallpaperColors
                .map { homeColors ->
                    val optionMap =
                        colorProvider.fetchThemeServiceCompatibleOptions(homeColors).groupBy {
                            option ->
                            when (option.source) {
                                ColorProviderUtil.COLOR_SOURCE_HOME -> ColorType.WALLPAPER_COLOR
                                else -> option.type
                            }
                        }
                    listOf(
                        ColorType.WALLPAPER_COLOR to
                            (optionMap[ColorType.WALLPAPER_COLOR] ?: emptyList()),
                        ColorType.BRAND_COLOR to (optionMap[ColorType.BRAND_COLOR] ?: emptyList()),
                        ColorType.PRESET_COLOR to (optionMap[ColorType.PRESET_COLOR] ?: emptyList()),
                    )
                }
                // Fetching from color provider is time consuming. Start collecting Lazily to make
                // sure color options are pre-populated and not re-fetched each time when entering
                // the color floating sheet.
                .shareIn(scope = scope, started = SharingStarted.Lazily, replay = 1)
        } else {
            combine(homeWallpaperColors, lockWallpaperColors) { homeColors, lockColors ->
                    suspendCancellableCoroutine { continuation ->
                        colorManager.setWallpaperColors(homeColors, lockColors)
                        colorManager.fetchOptions(
                            object : CustomizationManager.OptionsFetchedListener<ColorOption> {
                                override fun onOptionsLoaded(options: MutableList<ColorOption>?) {
                                    val wallpaperColorOptions: MutableList<ColorOption> =
                                        mutableListOf()
                                    val brandColorOptions: MutableList<ColorOption> =
                                        mutableListOf()
                                    val presetColorOptions: MutableList<ColorOption> =
                                        mutableListOf()
                                    options?.forEach { option ->
                                        when (val colorType = option.type) {
                                            ColorType.WALLPAPER_COLOR ->
                                                wallpaperColorOptions.add(option)
                                            ColorType.BRAND_COLOR -> brandColorOptions.add(option)
                                            ColorType.PRESET_COLOR -> presetColorOptions.add(option)
                                            else ->
                                                Log.e(
                                                    TAG,
                                                    "This color type should not be in the CustomizationManager list: $colorType",
                                                )
                                        }
                                    }
                                    continuation.resumeWith(
                                        Result.success(
                                            listOf(
                                                ColorType.WALLPAPER_COLOR to wallpaperColorOptions,
                                                ColorType.BRAND_COLOR to brandColorOptions,
                                                ColorType.PRESET_COLOR to presetColorOptions,
                                            )
                                        )
                                    )
                                }

                                override fun onError(throwable: Throwable?) {
                                    Log.e(TAG, "Error loading theme bundles", throwable)
                                    continuation.resumeWith(
                                        Result.failure(
                                            throwable ?: Throwable("Error loading theme bundles")
                                        )
                                    )
                                }
                            },
                            /* reload= */ false,
                        )
                    }
                }
                .shareIn(scope = scope, started = SharingStarted.WhileSubscribed(), replay = 1)
        }

    override val savedColors: Flow<List<SavedColorOption>> =
        savedColorStore.colors
            .map { colors ->
                colors.mapIndexed { index, color ->
                    SavedColorOption(
                        color = color,
                        option =
                            buildSavedColorOption(
                                name = color.name,
                                seed = color.seed,
                                style = color.style,
                                index = index + 1,
                            ),
                    )
                }
            }
            .flowOn(backgroundDispatcher)
            .shareIn(scope = scope, started = SharingStarted.WhileSubscribed(), replay = 1)

    override suspend fun saveColor(color: SavedColor): Result<Unit> = savedColorStore.write(color)

    override suspend fun deleteColor(id: SavedColorId): Result<Unit> = savedColorStore.delete(id)

    override fun buildPreviewOption(name: String, seed: SeedColor, style: ColorStyle): ColorOption =
        buildSavedColorOption(name = name, seed = seed, style = style, index = PREVIEW_OPTION_INDEX)

    private fun buildSavedColorOption(
        name: String,
        seed: SeedColor,
        style: ColorStyle,
        index: Int,
    ): ColorOption =
        ColorProviderUtil.buildSeedOption(
            title = name,
            seedColor = seed.argb,
            style = style.themeStyle,
            type = ColorType.SAVED_COLOR,
            index = index,
            isUsingThemeService = shouldUseThemeService,
            isColorPickerUpdateEnabled = baseFlags.isColorPickerUpdateEnabled(),
        )

    private val settingsChanged =
        callbackFlow {
                trySend(Unit)
                colorManager.setListener { trySend(Unit) }
                awaitClose { colorManager.setListener(null) }
            }
            // Make this a shared flow to prevent colorManager.setListener from being called
            // every time this flow is collected, since colorManager is a singleton.
            .shareIn(scope = scope, started = SharingStarted.WhileSubscribed(), replay = 1)

    private val selectedThemeSettings =
        callbackFlow {
                trySend(themeManager?.themeSettingsOrDefault)
                val callback =
                    object : IThemeSettingsCallback.Stub() {
                        override fun onSettingsChanged(
                            oldSettings: ThemeSettings?,
                            newSettings: ThemeSettings?,
                        ) {
                            newSettings?.let { trySend(it) }
                        }
                    }
                themeManager?.registerThemeSettingsCallback(callback)
                awaitClose { themeManager?.unregisterThemeSettingsCallback(callback) }
            }
            .shareIn(scope = scope, started = SharingStarted.WhileSubscribed(), replay = 1)

    override val selectedColorOption =
        if (shouldUseThemeService) {
                selectedThemeSettings.map { themeSettings ->
                    themeSettings?.let {
                        ColorOptionImpl.buildSimplifiedSeedOption(
                            title = null,
                            source = it.colorSource(),
                            seedColor = it.seedColors().first().toArgb(),
                            defaultStyle = it.themeStyle(),
                        )
                    }
                }
            } else {
                combine(colorOptions, settingsChanged) { options, _ ->
                    options.forEach { (_, optionsByType) ->
                        optionsByType.forEach {
                            if (it.isActive(colorManager)) {
                                return@combine it
                            }
                        }
                    }
                    return@combine getSettingsColorOption()
                }
            }
            .shareIn(scope = scope, started = SharingStarted.WhileSubscribed(), replay = 1)

    override val styleList: List<Int> = colorProvider.styleList

    private fun getSettingsColorOption(): ColorOption {
        val overlays: Map<String, String> = colorManager.currentOverlays
        val style: Int =
            colorManager.currentStyle?.let {
                try {
                    ThemeStyle.valueOf(it)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Failed to parse settings style", e)
                    ThemeStyle.TONAL_SPOT
                }
            } ?: ThemeStyle.TONAL_SPOT
        val source: String? = colorManager.currentColorSource
        val builder = ColorOptionImpl.Builder()
        builder.source = source
        builder.style = style
        for (overlay in overlays) {
            builder.addOverlayPackage(overlay.key, overlay.value)
        }
        val seedColorStr = overlays[ResourceConstants.OVERLAY_CATEGORY_SYSTEM_PALETTE]
        if (!seedColorStr.isNullOrEmpty()) {
            val seedColorInt =
                if (!seedColorStr.startsWith("#")) {
                    "#$seedColorStr".toColorInt()
                } else {
                    seedColorStr.toColorInt()
                }
            builder.seedColor = seedColorInt
            builder.lightColors =
                ColorProviderUtil.getColorPreview(
                    ColorScheme(seedColorInt, /* darkTheme= */ false, style),
                    source,
                    /* darkTheme= */ false,
                )
            builder.darkColors =
                ColorProviderUtil.getColorPreview(
                    ColorScheme(seedColorInt, /* darkTheme= */ true, style),
                    source,
                    /* darkTheme= */ true,
                )
        } else {
            builder.lightColors =
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                )
            builder.darkColors =
                intArrayOf(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                )
        }
        return builder.build()
    }

    override val selectedStyle: Flow<Int?> =
        if (shouldUseThemeService) {
            selectedThemeSettings.map { it?.themeStyle() }
        } else {
            settingsChanged.map {
                runCatching { colorManager.currentStyle?.let { ThemeStyle.valueOf(it) } }.getOrNull()
                    ?: ThemeStyle.TONAL_SPOT
            }
        }

    override suspend fun apply(colorOption: ColorOption): Boolean {
        if (shouldUseThemeService) {
            val settings =
                ThemeSettings.Builder()
                    .setSeedColors(Color.valueOf(colorOption.seedColor))
                    .setThemeStyle(colorOption.style)
                    .setColorSource(colorOption.source)
                    .setAppliedTimestamp(Instant.now())
                    .build()
            Log.d(TAG, "Applying theme using theme service: $settings")
            val success = themeManager!!.updateThemeSettings(settings)
            if (!success) {
                Log.w(TAG, "Failed to apply theme using theme service")
            }
            return success
        } else {
            return suspendCancellableCoroutine { continuation ->
                colorManager.apply(
                    colorOption,
                    object : CustomizationManager.Callback {
                        override fun onSuccess() {
                            continuation.resumeWith(Result.success(true))
                        }

                        override fun onError(throwable: Throwable?) {
                            Log.w(TAG, "Apply theme with error", throwable)
                            continuation.resumeWith(Result.success(false))
                        }
                    },
                )
            }
        }
    }

    /** Selects a color option and style and returns whether the operation was successful */
    override suspend fun apply(colorOption: ColorOption, @ThemeStyle.Type style: Int): Boolean {
        val colorOptionForApply =
            if (style == colorOption.style) {
                colorOption
            } else if (shouldUseThemeService) {
                ColorOptionImpl.buildSimplifiedSeedOption(
                    title = colorOption.title,
                    source = colorOption.source,
                    seedColor = colorOption.seedColor,
                    defaultStyle = style,
                )
            } else {
                if (colorOption.source == ColorProviderUtil.COLOR_SOURCE_PRESET) {
                    ColorProviderUtil.buildPreset(
                        title = colorOption.title,
                        color = colorOption.seedColor,
                        index = colorOption.index,
                        style = style,
                        isColorPickerUpdateEnabled = baseFlags.isColorPickerUpdateEnabled(),
                    )
                } else {
                    ColorProviderUtil.buildBundle(
                        context = appContext,
                        colorInt = colorOption.seedColor,
                        index = colorOption.index,
                        style = style,
                        isDefault = colorOption.isDefault,
                        isColorPickerUpdateEnabled = baseFlags.isColorPickerUpdateEnabled(),
                        isThemeServiceEnabled = baseFlags.isThemeServiceEnabled(),
                    )
                }
            }
        return apply(colorOptionForApply)
    }

    companion object {
        private const val TAG = "ColorPickerRepositoryImpl"
        // A draft is applied only once saved, so its option never reaches the index in settings.
        private const val PREVIEW_OPTION_INDEX = -1
    }
}
