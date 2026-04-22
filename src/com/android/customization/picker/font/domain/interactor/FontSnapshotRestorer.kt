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

package com.android.customization.picker.font.domain.interactor

import com.android.wallpaper.picker.undo.domain.interactor.SnapshotRestorer
import com.android.wallpaper.picker.undo.domain.interactor.SnapshotStore
import com.android.wallpaper.picker.undo.shared.model.RestorableSnapshot
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FontSnapshotRestorer @Inject constructor(private val interactor: FontInteractor) :
    SnapshotRestorer {

    private var store: SnapshotStore = SnapshotStore.NOOP
    private var originalFamily: String? = null
    private var hadOriginal = false

    override suspend fun setUpSnapshotRestorer(store: SnapshotStore): RestorableSnapshot {
        this.store = store
        interactor.refresh()
        originalFamily = interactor.activeFamily.value
        hadOriginal = true
        return snapshot(originalFamily)
    }

    override suspend fun restoreToSnapshot(snapshot: RestorableSnapshot) {
        if (!hadOriginal) return
        interactor.setPending(originalFamily)
        interactor.applyPending()
    }

    fun store(familyName: String?) {
        store.store(snapshot(familyName))
    }

    private fun snapshot(familyName: String?): RestorableSnapshot {
        return RestorableSnapshot(
            args =
                buildMap { familyName?.let { put(KEY_FONT_FAMILY_NAME, it) } }
        )
    }

    companion object {
        private const val KEY_FONT_FAMILY_NAME = "font_family"
    }
}
