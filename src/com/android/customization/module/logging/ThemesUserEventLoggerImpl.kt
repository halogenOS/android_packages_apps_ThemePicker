/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.customization.module.logging

import android.app.WallpaperManager
import android.content.Intent
import android.stats.style.StyleEnums.APP_LAUNCHED
import android.stats.style.StyleEnums.CLOCK_APPLIED
import android.stats.style.StyleEnums.CLOCK_COLOR_APPLIED
import android.stats.style.StyleEnums.CLOCK_SIZE_APPLIED
import android.stats.style.StyleEnums.CLOCK_SIZE_DYNAMIC
import android.stats.style.StyleEnums.CLOCK_SIZE_SMALL
import android.stats.style.StyleEnums.CLOCK_SIZE_UNSPECIFIED
import android.stats.style.StyleEnums.CURATED_PHOTOS_FETCH_END
import android.stats.style.StyleEnums.CURATED_PHOTOS_RENDER_COMPLETE
import android.stats.style.StyleEnums.DARK_THEME_APPLIED
import android.stats.style.StyleEnums.ENTER_SCREEN
import android.stats.style.StyleEnums.GRID_APPLIED
import android.stats.style.StyleEnums.LAUNCHED_CROP_AND_SET_ACTION
import android.stats.style.StyleEnums.LAUNCHED_DEEP_LINK
import android.stats.style.StyleEnums.LAUNCHED_KEYGUARD
import android.stats.style.StyleEnums.LAUNCHED_LAUNCHER
import android.stats.style.StyleEnums.LAUNCHED_LAUNCH_ICON
import android.stats.style.StyleEnums.LAUNCHED_PREFERENCE_UNSPECIFIED
import android.stats.style.StyleEnums.LAUNCHED_SETTINGS
import android.stats.style.StyleEnums.LAUNCHED_SETTINGS_SEARCH
import android.stats.style.StyleEnums.LAUNCHED_SUW
import android.stats.style.StyleEnums.LAUNCHED_TIPS
import android.stats.style.StyleEnums.LOCK_SCREEN_NOTIFICATION_APPLIED
import android.stats.style.StyleEnums.RESET_APPLIED
import android.stats.style.StyleEnums.SCREEN_CLOCK
import android.stats.style.StyleEnums.SCREEN_COLORS
import android.stats.style.StyleEnums.SCREEN_ICONS
import android.stats.style.StyleEnums.SCREEN_LAYOUT
import android.stats.style.StyleEnums.SCREEN_SHORTCUTS
import android.stats.style.StyleEnums.SCREEN_UNSPECIFIED
import android.stats.style.StyleEnums.SHAPE_APPLIED
import android.stats.style.StyleEnums.SHORTCUT_APPLIED
import android.stats.style.StyleEnums.SNAPSHOT
import android.stats.style.StyleEnums.THEMED_ICON_APPLIED
import android.stats.style.StyleEnums.THEME_COLOR_APPLIED
import android.stats.style.StyleEnums.WALLPAPER_APPLIED
import android.stats.style.StyleEnums.WALLPAPER_DESTINATION_HOME_AND_LOCK_SCREEN
import android.stats.style.StyleEnums.WALLPAPER_DESTINATION_HOME_SCREEN
import android.stats.style.StyleEnums.WALLPAPER_DESTINATION_LOCK_SCREEN
import android.stats.style.StyleEnums.WALLPAPER_EFFECT_APPLIED
import android.stats.style.StyleEnums.WALLPAPER_EFFECT_FG_DOWNLOAD
import android.stats.style.StyleEnums.WALLPAPER_EFFECT_PROBE
import android.stats.style.StyleEnums.WALLPAPER_EXPLORE
import android.text.TextUtils
import android.util.Log
import com.android.customization.model.color.ColorCustomizationManager
import com.android.customization.model.grid.GridOptionModel
import com.android.customization.model.grid.ShapeGridManager
import com.android.customization.model.grid.ShapeOptionModel
import com.android.customization.module.logging.ThemesUserEventLogger.AppIconStyle
import com.android.customization.module.logging.ThemesUserEventLogger.ClockSize
import com.android.customization.module.logging.ThemesUserEventLogger.ColorSource
import com.android.customization.picker.clock.data.repository.ClockPickerRepository
import com.android.customization.picker.clock.shared.ClockSize.DYNAMIC
import com.android.customization.picker.clock.shared.ClockSize.SMALL
import com.android.customization.picker.icon.data.repository.IconStyleRepository
import com.android.systemui.shared.customization.data.content.CustomizationProviderClient
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerHomeCustomizationOption.APP_ICONS
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerHomeCustomizationOption.GRID
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerLockCustomizationOption.CLOCK
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerLockCustomizationOption.SHORTCUTS
import com.android.wallpaper.customization.ui.util.ThemePickerCustomizationOptionUtil.ThemePickerStyleCustomizationOption.COLORS
import com.android.wallpaper.module.WallpaperPreferences
import com.android.wallpaper.module.logging.UserEventLogger.CustomizationPickerScreen
import com.android.wallpaper.module.logging.UserEventLogger.EffectStatus
import com.android.wallpaper.module.logging.UserEventLogger.LaunchedPreference
import com.android.wallpaper.module.logging.UserEventLogger.SetWallpaperEntryPoint
import com.android.wallpaper.module.logging.UserEventLogger.WallpaperDestination
import com.android.wallpaper.picker.customization.ui.util.CustomizationOptionUtil.CustomizationOption
import com.android.wallpaper.util.LaunchSourceUtils
import io.grpc.Status
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** StatsLog-backed implementation of [ThemesUserEventLogger]. */
@Singleton
class ThemesUserEventLoggerImpl
@Inject
constructor(
    private val preferences: WallpaperPreferences,
    private val colorManager: ColorCustomizationManager,
    private val shapeGridManager: ShapeGridManager,
    private val iconStyleRepository: IconStyleRepository,
    private val clockPickerRepository: ClockPickerRepository,
    private val customizationProviderClient: CustomizationProviderClient,
    private val appSessionId: AppSessionId,
    private val sysUiStatsLoggerFactory: SysUiStatsLoggerFactory,
) : ThemesUserEventLogger {

    override suspend fun logSnapshot() {
        val selectedClockLoggingData = clockPickerRepository.getSelectedClockLoggingData()

        sysUiStatsLoggerFactory
            .get(SNAPSHOT)
            .setWallpaperCategoryHash(preferences.getHomeCategoryHash())
            .setWallpaperIdHash(preferences.getHomeWallpaperIdHash())
            .setEffectIdHash(preferences.getHomeWallpaperEffectsIdHash())
            .setLockWallpaperCategoryHash(preferences.getLockCategoryHash())
            .setLockWallpaperIdHash(preferences.getLockWallpaperIdHash())
            .setLockEffectIdHash(preferences.getLockWallpaperEffectsIdHash())
            .setColorSource(colorManager.currentColorSourceForLogging)
            .setColorVariant(colorManager.currentStyleForLogging)
            .setSeedColor(colorManager.currentSeedColorForLogging)
            .setShapePackageHash(shapeGridManager.getSelectedShapeIdHash())
            .setAppIconStyle(iconStyleRepository.getIconStyleForLogging())
            .setLauncherGrid(shapeGridManager.getSelectedGridInt())
            .setClockPackageHash(selectedClockLoggingData.clockIdHash)
            .setClockSeedColor(selectedClockLoggingData.clockSeedColor)
            .setUseClockCustomization(selectedClockLoggingData.useClockCustomization)
            .setClockSize(selectedClockLoggingData.clockSize)
            .setShortcut(customizationProviderClient.getSelectedShortcutsString())
            .log()
    }

    override fun logAppLaunched(launchSource: Intent) {
        sysUiStatsLoggerFactory
            .get(APP_LAUNCHED)
            .setAppSessionId(appSessionId.createNewId().getId())
            .setLaunchedPreference(launchSource.getAppLaunchSource())
            .log()
    }

    override fun logWallpaperApplied(
        collectionId: String?,
        wallpaperId: String?,
        effects: String?,
        @SetWallpaperEntryPoint setWallpaperEntryPoint: Int,
        @WallpaperDestination destination: Int,
    ) {
        val categoryHash = getIdHashCode(collectionId)
        val wallpaperIdHash = getIdHashCode(wallpaperId)
        val isHomeWallpaperSet =
            destination == WALLPAPER_DESTINATION_HOME_SCREEN ||
                destination == WALLPAPER_DESTINATION_HOME_AND_LOCK_SCREEN
        val isLockWallpaperSet =
            destination == WALLPAPER_DESTINATION_LOCK_SCREEN ||
                destination == WALLPAPER_DESTINATION_HOME_AND_LOCK_SCREEN
        sysUiStatsLoggerFactory
            .get(WALLPAPER_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setWallpaperCategoryHash(if (isHomeWallpaperSet) categoryHash else 0)
            .setWallpaperIdHash(if (isHomeWallpaperSet) wallpaperIdHash else 0)
            .setEffectIdHash(if (isHomeWallpaperSet) getIdHashCode(effects) else 0)
            .setLockWallpaperCategoryHash(if (isLockWallpaperSet) categoryHash else 0)
            .setLockWallpaperIdHash(if (isLockWallpaperSet) wallpaperIdHash else 0)
            .setLockEffectIdHash(if (isLockWallpaperSet) getIdHashCode(effects) else 0)
            .setSetWallpaperEntryPoint(setWallpaperEntryPoint)
            .setWallpaperDestination(destination)
            .log()
    }

    override fun logEffectApply(
        effect: String,
        @EffectStatus status: Int,
        timeElapsedMillis: Long,
        resultCode: Int,
    ) {
        sysUiStatsLoggerFactory
            .get(WALLPAPER_EFFECT_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setEffectPreference(status)
            .setEffectIdHash(getIdHashCode(effect))
            .setTimeElapsed(timeElapsedMillis)
            .setEffectResultCode(resultCode)
            .log()
    }

    override fun logEffectProbe(effect: String, @EffectStatus status: Int) {
        sysUiStatsLoggerFactory
            .get(WALLPAPER_EFFECT_PROBE)
            .setAppSessionId(appSessionId.getId())
            .setEffectPreference(status)
            .setEffectIdHash(getIdHashCode(effect))
            .log()
    }

    override fun logEffectForegroundDownload(
        effect: String,
        @EffectStatus status: Int,
        timeElapsedMillis: Long,
    ) {
        sysUiStatsLoggerFactory
            .get(WALLPAPER_EFFECT_FG_DOWNLOAD)
            .setAppSessionId(appSessionId.getId())
            .setEffectPreference(status)
            .setEffectIdHash(getIdHashCode(effect))
            .setTimeElapsed(timeElapsedMillis)
            .log()
    }

    override fun logResetApplied() {
        sysUiStatsLoggerFactory.get(RESET_APPLIED).setAppSessionId(appSessionId.getId()).log()
    }

    override fun logWallpaperExploreButtonClicked() {
        sysUiStatsLoggerFactory.get(WALLPAPER_EXPLORE).setAppSessionId(appSessionId.getId()).log()
    }

    override fun logThemeColorApplied(@ColorSource source: Int, style: Int, seedColor: Int) {
        sysUiStatsLoggerFactory
            .get(THEME_COLOR_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setColorSource(source)
            .setColorVariant(style)
            .setSeedColor(seedColor)
            .log()
    }

    override fun logGridApplied(grid: GridOptionModel) {
        sysUiStatsLoggerFactory
            .get(GRID_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setLauncherGrid(grid.getLauncherGridInt())
            .log()
    }

    override fun logClockApplied(clockId: String, useClockCustomization: Boolean) {
        sysUiStatsLoggerFactory
            .get(CLOCK_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setClockPackageHash(getIdHashCode(clockId))
            .setUseClockCustomization(useClockCustomization)
            .log()
    }

    override fun logClockColorApplied(seedColor: Int) {
        sysUiStatsLoggerFactory
            .get(CLOCK_COLOR_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setClockSeedColor(seedColor)
            .log()
    }

    override fun logClockSizeApplied(@ClockSize clockSize: Int) {
        sysUiStatsLoggerFactory
            .get(CLOCK_SIZE_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setClockSize(clockSize)
            .log()
    }

    override fun logThemedIconApplied(useThemeIcon: Boolean) {
        sysUiStatsLoggerFactory
            .get(THEMED_ICON_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setToggleOn(useThemeIcon)
            .log()
    }

    override fun logIconStyleApplied(@AppIconStyle iconStyle: Int) {
        sysUiStatsLoggerFactory
            .get(THEMED_ICON_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setAppIconStyle(iconStyle)
            .log()
    }

    override fun logLockScreenNotificationApplied(showLockScreenNotifications: Boolean) {
        sysUiStatsLoggerFactory
            .get(LOCK_SCREEN_NOTIFICATION_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setToggleOn(showLockScreenNotifications)
            .log()
    }

    override fun logShortcutApplied(shortcut: String, shortcutSlotId: String) {
        sysUiStatsLoggerFactory
            .get(SHORTCUT_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setShortcut(shortcut)
            .setShortcutSlotId(shortcutSlotId)
            .log()
    }

    override fun logDarkThemeApplied(useDarkTheme: Boolean) {
        sysUiStatsLoggerFactory
            .get(DARK_THEME_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setToggleOn(useDarkTheme)
            .log()
    }

    override fun logShapeApplied(shapeId: String) {
        sysUiStatsLoggerFactory
            .get(SHAPE_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setShapePackageHash(getIdHashCode(shapeId))
            .log()
    }

    // We use the field toggle on to denote whether the event corresponds to a user selected photo
    // or a default wallpaper
    override fun logCuratedPhotosRendered(timeElapsedMillis: Long, userPhoto: Boolean) {
        sysUiStatsLoggerFactory
            .get(CURATED_PHOTOS_RENDER_COMPLETE)
            .setAppSessionId(appSessionId.getId())
            .setTimeElapsed(timeElapsedMillis)
            .setToggleOn(userPhoto)
            .log()
    }

    override fun logCuratedPhotosFetched(timeElapsedMillis: Long, status: Status) {
        sysUiStatsLoggerFactory
            .get(CURATED_PHOTOS_FETCH_END)
            .setAppSessionId(appSessionId.getId())
            .setEffectResultCode(status.code.value())
            .setTimeElapsed(timeElapsedMillis)
            .log()
    }

    override fun logEnterScreen(@CustomizationPickerScreen screen: Int) {
        sysUiStatsLoggerFactory
            .get(ENTER_SCREEN)
            .setAppSessionId(appSessionId.getId())
            .setCustomizationPickerScreen(screen)
            .log()
    }

    @CustomizationPickerScreen
    override fun transformCustomizationOptionToScreenForLogging(
        customizationOption: CustomizationOption
    ): Int {
        return when (customizationOption) {
            COLORS -> SCREEN_COLORS
            APP_ICONS -> SCREEN_ICONS
            GRID -> SCREEN_LAYOUT
            CLOCK -> SCREEN_CLOCK
            SHORTCUTS -> SCREEN_SHORTCUTS
            else -> SCREEN_UNSPECIFIED
        }
    }

    /**
     * The grid integer depends on the column and row numbers. For example: 4x5 is 405 13x37 is 1337
     * The upper limit for the column / row count is 99.
     */
    private fun GridOptionModel.getLauncherGridInt(): Int {
        return getLauncherGridInt(cols, rows)
    }

    private fun getLauncherGridInt(cols: Int, rows: Int): Int {
        return cols * 100 + rows
    }

    @LaunchedPreference
    private fun Intent.getAppLaunchSource(): Int {
        return if (hasExtra(LaunchSourceUtils.WALLPAPER_LAUNCH_SOURCE)) {
            when (getStringExtra(LaunchSourceUtils.WALLPAPER_LAUNCH_SOURCE)) {
                LaunchSourceUtils.LAUNCH_SOURCE_LAUNCHER -> LAUNCHED_LAUNCHER
                LaunchSourceUtils.LAUNCH_SOURCE_SETTINGS -> LAUNCHED_SETTINGS
                LaunchSourceUtils.LAUNCH_SOURCE_SUW -> LAUNCHED_SUW
                LaunchSourceUtils.LAUNCH_SOURCE_TIPS -> LAUNCHED_TIPS
                LaunchSourceUtils.LAUNCH_SOURCE_DEEP_LINK -> LAUNCHED_DEEP_LINK
                LaunchSourceUtils.LAUNCH_SOURCE_KEYGUARD -> LAUNCHED_KEYGUARD
                LaunchSourceUtils.LAUNCH_SOURCE_SETTINGS_SEARCH -> LAUNCHED_SETTINGS_SEARCH
                else -> LAUNCHED_PREFERENCE_UNSPECIFIED
            }
        } else if (action != null && action == WallpaperManager.ACTION_CROP_AND_SET_WALLPAPER) {
            LAUNCHED_CROP_AND_SET_ACTION
        } else if (categories != null && categories.contains(Intent.CATEGORY_LAUNCHER)) {
            LAUNCHED_LAUNCH_ICON
        } else {
            LAUNCHED_PREFERENCE_UNSPECIFIED
        }
    }

    /** If not set, the output hash is 0. */
    private fun WallpaperPreferences.getHomeCategoryHash(): Int {
        return getIdHashCode(getHomeWallpaperCollectionId())
    }

    /** If not set, the output hash is 0. */
    private fun WallpaperPreferences.getHomeWallpaperIdHash(): Int {
        val remoteId = getHomeWallpaperRemoteId()
        val wallpaperId =
            if (!TextUtils.isEmpty(remoteId)) remoteId else getHomeWallpaperServiceName()
        return getIdHashCode(wallpaperId)
    }

    /** If not set, the output hash is 0. */
    private fun WallpaperPreferences.getLockCategoryHash(): Int {
        return getIdHashCode(getLockWallpaperCollectionId())
    }

    /** If not set, the output hash is 0. */
    private fun WallpaperPreferences.getLockWallpaperIdHash(): Int {
        val remoteId = getLockWallpaperRemoteId()
        val wallpaperId =
            if (!TextUtils.isEmpty(remoteId)) remoteId else getLockWallpaperServiceName()
        return getIdHashCode(wallpaperId)
    }

    /** If not set, the output hash is 0. */
    private fun WallpaperPreferences.getHomeWallpaperEffectsIdHash(): Int {
        return getIdHashCode(getHomeWallpaperEffects())
    }

    /** If not set, the output hash is 0. */
    private fun WallpaperPreferences.getLockWallpaperEffectsIdHash(): Int {
        return getIdHashCode(getLockWallpaperEffects())
    }

    private suspend fun ShapeGridManager.getSelectedShapeIdHash(): Int {
        val selectedShape: ShapeOptionModel? =
            try {
                getShapeOptions().firstOrNull { it.isCurrent }
            } catch (e: Exception) {
                Log.e(TAG, "Fail to get shape options. Skip logging selected shape.", e)
                null
            }
        return getIdHashCode(selectedShape?.key)
    }

    private suspend fun ShapeGridManager.getSelectedGridInt(): Int {
        val selectedGrid: GridOptionModel? =
            try {
                getGridOptions().firstOrNull { it.isCurrent }
            } catch (e: Exception) {
                Log.e(TAG, "Fail to get grid options. Skip logging selected grid.", e)
                null
            }
        return selectedGrid?.getLauncherGridInt() ?: 0
    }

    private suspend fun ClockPickerRepository.getSelectedClockLoggingData():
        SelectedClockLoggingData {
        val selectedClock =
            try {
                withTimeoutOrNull(TIMEOUT) { selectedClock.first() }
            } catch (e: Exception) {
                Log.e(TAG, "Fail to get selected clock. Skip logging selected clock.", e)
                null
            }
        val selectedClockSize =
            try {
                withTimeoutOrNull(TIMEOUT) { selectedClockSize.first() }
            } catch (e: Exception) {
                Log.e(TAG, "Fail to get selected clock size. Skip logging selected clock size.", e)
                null
            }
        return SelectedClockLoggingData(
            clockIdHash = getIdHashCode(selectedClock?.clockId),
            clockSeedColor = selectedClock?.seedColor ?: 0,
            useClockCustomization = selectedClock?.axisPresetConfig?.current != null,
            clockSize =
                when (selectedClockSize) {
                    SMALL -> CLOCK_SIZE_SMALL
                    DYNAMIC -> CLOCK_SIZE_DYNAMIC
                    else -> CLOCK_SIZE_UNSPECIFIED
                },
        )
    }

    private suspend fun CustomizationProviderClient.getSelectedShortcutsString(): String {
        val shortcutSelections =
            try {
                querySelections()
            } catch (e: Exception) {
                Log.e(TAG, "Fail to get selected shortcuts. Skip logging selected shortcuts.", e)
                emptyList()
            }
        return shortcutSelections.joinToString(separator = ",") {
            "${it.slotId}:${it.affordanceId}"
        }
    }

    private fun getIdHashCode(id: String?): Int {
        return id?.hashCode() ?: 0
    }

    companion object {
        private const val TAG = "ThemesUserEventLoggerImpl"
        val TIMEOUT = 5000.milliseconds
    }
}
