package com.mckimquyen.adt

import androidx.appcompat.app.AppCompatActivity
import com.mckimquyen.ext.Biometric
import com.mckimquyen.model.App
import com.mckimquyen.model.AppOrganizationRules
import com.mckimquyen.model.AppPersistent
import com.mckimquyen.model.PinnedZone
import com.mckimquyen.services.AppEventManager

/** Persistence và sự kiện của hàng app, dùng chung cho Home và Settings. */
class AppRowActions @JvmOverloads constructor(
    private val authenticate: (AppCompatActivity, App, () -> Unit) -> Unit = { activity, app, success ->
        Biometric.toggleLockApp(activity, app.label.toString(), app.packageName.toString(), app.isOpened) { _, _ -> success() }
    }
) {
    fun toggleVisibility(app: App): App {
        requireIdentity(app)
        val updated = app.copy(isVisible = !app.isVisible)
        AppPersistent.setAppVisibility(app.packageName.toString(), app.name.toString(), updated.isVisible, app.lensId, app.userId)
        AppEventManager.notifyVisibilityChanged()
        return updated
    }

    fun toggleLock(activity: AppCompatActivity, app: App, onUpdated: (App) -> Unit) {
        requireIdentity(app)
        authenticate(activity, app) {
            val updated = app.copy(isOpened = !app.isOpened)
            AppPersistent.setAppOpened(app.packageName.toString(), app.name.toString(), updated.isOpened, app.lensId, app.userId)
            onUpdated(updated)
            AppEventManager.notifyLockChanged()
        }
    }

    fun setOrganization(app: App, favorite: Boolean, folder: String?, zone: PinnedZone): App {
        requireIdentity(app)
        val normalized = AppOrganizationRules.normalizeFolder(folder)
        AppPersistent.setOrganization(app.packageName.toString(), app.name.toString(), favorite, normalized, zone, app.lensId, app.userId)
        AppEventManager.notifyAppsEdited()
        return app.copyWithOrganization(favorite, normalized, zone)
    }

    private fun requireIdentity(app: App) {
        require(app.packageName.isNotBlank() && app.name.isNotBlank() && app.lensId.isNotBlank()) {
            "App row requires package, component and lens IDs"
        }
    }
}
