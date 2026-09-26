package com.mckimquyen.model

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mckimquyen.app.ApplicationScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FISH-008: Represents an independent Lens workspace (e.g. "Lens 1", "Work", "Personal").
 * Each workspace owns its own app organization layout (pinned zones, custom order, visibility,
 * favorites, folders) in [AppPersistent].
 */
@Keep
@Entity(tableName = "LENS_WORKSPACE")
data class LensWorkspace(
    @PrimaryKey
    @ColumnInfo(name = "ID")
    val id: String,
    @ColumnInfo(name = "NAME")
    val name: String,
    @ColumnInfo(name = "ORDER_INDEX", defaultValue = "0")
    val orderIndex: Int = 0,
    @ColumnInfo(name = "CREATED_AT", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_LENS_ID = "default"
        const val DEFAULT_LENS_NAME = "Lens 1"

        fun createDefault(): LensWorkspace = LensWorkspace(
            id = DEFAULT_LENS_ID,
            name = DEFAULT_LENS_NAME,
            orderIndex = 0,
            createdAt = 0L
        )

        /**
         * FISH-008 Phase 2: a fresh install (never ran MIGRATION_10_11) starts with an empty
         * LENS_WORKSPACE table - seed the default lens once at startup so the home screen
         * always has at least one lens to page. Existing installs already have this row from
         * the migration; [insertIfAbsent]'s IGNORE conflict strategy makes this a no-op there.
         */
        @JvmStatic
        fun seedDefaultIfAbsent() {
            ApplicationScope.scope.launch(Dispatchers.IO) {
                AppDatabase.getInstance().lensWorkspaceDao().insertIfAbsent(createDefault())
            }
        }

        private fun dao() = AppDatabase.getInstance().lensWorkspaceDao()

        /** Java-callable (see [com.mckimquyen.ext.Biometric.toggleLockApp] for the same block-
         *  lambda-from-Java pattern already used elsewhere in this codebase). Delivers on main. */
        @JvmStatic
        fun loadAll(onLoaded: (List<LensWorkspace>) -> Unit) {
            ApplicationScope.scope.launch(Dispatchers.Main.immediate) {
                val lenses = withContext(Dispatchers.IO) { dao().getAll() }
                onLoaded(lenses)
            }
        }

        /** Owner decision (FISH-008 Phase 2): a new lens starts as a copy of [copyFromLensId]'s
         *  layout, not empty. */
        @JvmStatic
        fun createLens(name: String, copyFromLensId: String, onDone: (List<LensWorkspace>) -> Unit) {
            ApplicationScope.scope.launch(Dispatchers.Main.immediate) {
                withContext(Dispatchers.IO) {
                    val existing = dao().getAll()
                    val newLens = LensWorkspace(
                        id = java.util.UUID.randomUUID().toString(),
                        name = name,
                        orderIndex = existing.size
                    )
                    dao().insertOrUpdate(newLens)
                    AppDatabase.getInstance().appPersistentDao()
                        .duplicateLensLayout(copyFromLensId, newLens.id)
                }
                notifyLensesChanged(onDone)
            }
        }

        @JvmStatic
        fun renameLens(lens: LensWorkspace, newName: String, onDone: (List<LensWorkspace>) -> Unit) {
            ApplicationScope.scope.launch(Dispatchers.Main.immediate) {
                withContext(Dispatchers.IO) { dao().update(lens.copy(name = newName)) }
                notifyLensesChanged(onDone)
            }
        }

        /** No DB-level cascade exists between LENS_WORKSPACE and APP_PERSISTENT (see
         *  AppDatabase's MIGRATION_10_11) - both deletes are explicit. */
        @JvmStatic
        fun deleteLens(lensId: String, onDone: (List<LensWorkspace>) -> Unit) {
            ApplicationScope.scope.launch(Dispatchers.Main.immediate) {
                withContext(Dispatchers.IO) {
                    dao().deleteById(lensId)
                    AppDatabase.getInstance().appPersistentDao().deleteForLens(lensId)
                }
                notifyLensesChanged(onDone)
            }
        }

        private suspend fun notifyLensesChanged(onDone: (List<LensWorkspace>) -> Unit) {
            val lenses = withContext(Dispatchers.IO) { dao().getAll() }
            onDone(lenses)
        }
    }
}
