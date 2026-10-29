package com.mckimquyen.util

import androidx.room.withTransaction
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import com.mckimquyen.model.AppPersistent

/** Ghi thứ tự lens đích mà không thay snapshot của lens đang hiển thị. */
class LensFreezeEditor(private val db: AppDatabase, private val settings: UtilSettings) {
    suspend fun setFrozen(lensId: String, frozen: Boolean, apps: List<App> = emptyList()) {
        require(lensId.isNotBlank())
        db.withTransaction {
            requireNotNull(db.lensWorkspaceDao().findById(lensId)) { "Unknown lens: $lensId" }
            val dao = db.appPersistentDao()
            if (frozen) {
                require(apps.isNotEmpty())
                require(apps.map { it.identifier }.distinct().size == apps.size)
                val rows = apps.mapIndexed { index, app ->
                    require(app.packageName.isNotBlank() && app.name.isNotBlank())
                    AppPersistent.defaults(app.packageName.toString(), app.name.toString(), lensId, app.userId)
                        .copy(orderNumber = index)
                }
                dao.setOrders(rows)
            } else {
                dao.clearOrderForLens(lensId)
            }
        }
        settings.saveLensFrozen(lensId, frozen)
    }
}
