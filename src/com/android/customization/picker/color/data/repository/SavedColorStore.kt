// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0
package com.android.customization.picker.color.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.android.customization.picker.color.shared.model.ColorStyle
import com.android.customization.picker.color.shared.model.SavedColor
import com.android.customization.picker.color.shared.model.SavedColorId
import com.android.customization.picker.color.shared.model.SeedColor
import com.android.wallpaper.picker.di.modules.BackgroundDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Stores the colors saved by the person, one preference per color, so writing one color leaves
 * every other entry as it was, including entries this version cannot read.
 */
@Singleton
class SavedColorStore
@Inject
constructor(
    @ApplicationContext context: Context,
    @BackgroundDispatcher private val backgroundDispatcher: CoroutineDispatcher,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** The readable saved colors in the order they were added, read again after every change. */
    val colors: Flow<List<SavedColor>> =
        callbackFlow {
                val listener =
                    SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
                preferences.registerOnSharedPreferenceChangeListener(listener)
                trySend(Unit)
                awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            .conflate()
            .map { readAll() }
            .flowOn(backgroundDispatcher)

    /** Stores [color] under its id. */
    suspend fun write(color: SavedColor): Result<Unit> =
        withContext(backgroundDispatcher) {
            preferences.edit().putString(color.id.value, encode(color)).commitOrFail {
                "Could not write saved color ${color.id.value} to disk"
            }
        }

    /** Removes the color with [id]. */
    suspend fun delete(id: SavedColorId): Result<Unit> =
        withContext(backgroundDispatcher) {
            preferences.edit().remove(id.value).commitOrFail {
                "Could not remove saved color ${id.value} from disk"
            }
        }

    private fun SharedPreferences.Editor.commitOrFail(describeFailure: () -> String): Result<Unit> =
        if (commit()) {
            Result.success(Unit)
        } else {
            val error = IOException(describeFailure())
            Log.e(TAG, error.message, error)
            Result.failure(error)
        }

    private fun readAll(): List<SavedColor> =
        preferences.all
            .mapNotNull { (key, value) -> (value as? String)?.let { decode(key, it) } }
            .sortedWith(compareBy<SavedColor> { it.addedAt }.thenBy { it.id.value })

    private fun encode(color: SavedColor): String =
        Json.encodeToString(
            StoredColor.serializer(),
            StoredColor(
                name = color.name,
                seed = color.seed.argb,
                style = color.style,
                addedAt = color.addedAt.toString(),
            ),
        )

    private fun decode(id: String, text: String): SavedColor? =
        try {
            val stored = Json.decodeFromString(StoredColor.serializer(), text)
            SavedColor(
                id = SavedColorId(id),
                name = stored.name,
                seed = SeedColor(stored.seed),
                style = stored.style,
                addedAt = Instant.parse(stored.addedAt),
            )
        } catch (e: IllegalArgumentException) {
            // Thrown for malformed JSON, an unknown style, a blank name or a translucent seed
            skipUnreadable(id, e)
        } catch (e: DateTimeParseException) {
            skipUnreadable(id, e)
        }

    private fun skipUnreadable(id: String, cause: Exception): SavedColor? {
        Log.w(TAG, "Skipping unreadable saved color $id", cause)
        return null
    }

    /** A saved color as it is written to disk. */
    @Serializable
    private data class StoredColor(
        val name: String,
        val seed: Int,
        val style: ColorStyle,
        /** ISO-8601 instant, which keeps every digit of the time */
        val addedAt: String,
    )

    private companion object {
        const val TAG = "SavedColorStore"
        const val PREFERENCES_NAME = "saved_colors"
    }
}
