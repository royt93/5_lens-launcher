package com.mckimquyen.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mckimquyen.app.ApplicationScope
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.services.AppEventManager
import com.mckimquyen.util.Logger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Entity(
    tableName = "APP_PERSISTENT",
    indices = [Index(value = ["LENS_ID", "IDENTIFIER"], unique = true)]
)
data class AppPersistent(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "ID")
    var id: Long? = null,
    @ColumnInfo(name = "PACKAGE_NAME")
    var packageName: String? = null,
    @ColumnInfo(name = "NAME")
    var name: String? = null,
    @ColumnInfo(name = "IDENTIFIER")
    var identifier: String = "",
    @ColumnInfo(name = "OPEN_COUNT")
    var openCount: Long = 1,
    @ColumnInfo(name = "ORDER_NUMBER")
    var orderNumber: Int = -1,
    @ColumnInfo(name = "APP_VISIBLE")
    var appVisible: Boolean = true,
    @ColumnInfo(name = "APP_OPENED")
    var appOpened: Boolean = true,
    @ColumnInfo(name = "PALETTE_COLOR", defaultValue = "0")
    var paletteColor: Int = 0,
    @ColumnInfo(name = "IS_FAVORITE", defaultValue = "0")
    var isFavorite: Boolean = false,
    @ColumnInfo(name = "FOLDER_NAME")
    var folderName: String? = null,
    @ColumnInfo(name = "PINNED_ZONE", defaultValue = "'NONE'")
    var pinnedZone: String = PinnedZone.NONE.name,
    @ColumnInfo(name = "LENS_ID", defaultValue = "'default'")
    var lensId: String = LensWorkspace.DEFAULT_LENS_ID,
    @ColumnInfo(name = "NOTIFICATION_COUNT", defaultValue = "0")
    var notificationCount: Int = 0
) {
    companion object {
        const val DEFAULT_LENS_ID = LensWorkspace.DEFAULT_LENS_ID
        private const val DEFAULT_ORDER_NUMBER = -1
        private const val DEFAULT_OPEN_COUNT = 0L
        // UI-024: not private - BadgeCountReceiver shares this ceiling instead of redefining it.
        const val MAX_STORED_NOTIFICATION_COUNT = 9999
        private val orderWriteMutexes = ConcurrentHashMap<String, Mutex>()
        private val orderWriteRevisions = ConcurrentHashMap<String, AtomicLong>()
        private val latestWriteMutexes = ConcurrentHashMap<String, Mutex>()
        private val latestWriteRevisions = ConcurrentHashMap<String, AtomicLong>()

        @JvmStatic
        fun generateIdentifier(packageName: String?, name: String?): String =
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) "" else "$packageName-$name"

        private fun dao(): AppPersistentDao = AppDatabase.getInstance().appPersistentDao()

        @JvmOverloads
        @JvmStatic
        fun defaults(
            packageName: String,
            name: String,
            lensId: String = DEFAULT_LENS_ID,
            userId: Int = AppIdentity.PRIMARY_USER_ID
        ) = AppPersistent(
            packageName = packageName,
            name = name,
            identifier = AppIdentity.identifier(packageName, name, userId),
            openCount = DEFAULT_OPEN_COUNT,
            orderNumber = DEFAULT_ORDER_NUMBER,
            appVisible = true,
            appOpened = true,
            lensId = lensId
        )

        /**
         * Runs a database [operation]. A cancelled coroutine must stay cancelled, so
         * [CancellationException] is always rethrown (swallowing it would break structured
         * cancellation of [ApplicationScope]); any other failure is logged and handed to
         * [onFailure] so the caller can undo its optimistic in-memory change.
         */
        internal suspend fun runGuarded(
            onFailure: (Throwable) -> Unit = {},
            operation: suspend () -> Unit
        ) {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Logger.e("AppPersistent: asynchronous database operation failed", error)
                onFailure(error)
            }
        }

        /** @param onFailure runs after a real (non-cancellation) failure, before the UI is told. */
        private fun persist(
            onFailure: (Throwable) -> Unit = {},
            operation: suspend AppPersistentDao.() -> Unit
        ): Job = ApplicationScope.scope.launch(Dispatchers.IO) {
            runGuarded(onFailure = { error ->
                onFailure(error)
                AppEventManager.notifyPersistFailed()
            }) { dao().operation() }
        }

        private fun persistLatest(
            channel: String,
            identifier: String,
            lensId: String = DEFAULT_LENS_ID,
            onFailure: (Throwable) -> Unit = {},
            operation: suspend AppPersistentDao.() -> Unit
        ): Job {
            val key = "$channel:$lensId:$identifier"
            val revisionCounter = latestWriteRevisions.computeIfAbsent(key) { AtomicLong(0L) }
            val revision = revisionCounter.incrementAndGet()
            val mutex = latestWriteMutexes.computeIfAbsent(key) { Mutex() }
            return persist(
                // Only the newest write for this key may undo itself: an older failed write must
                // not clobber the value a newer, successful one has already applied.
                onFailure = { error -> if (revision == revisionCounter.get()) onFailure(error) }
            ) {
                mutex.withLock {
                    if (revision == revisionCounter.get()) operation()
                }
            }
        }

        @JvmOverloads
        @JvmStatic
        fun incrementAppCount(packageName: String?, name: String?, userId: Int = AppIdentity.PRIMARY_USER_ID) {
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) return
            val cached = RAppsSingleton.instance.findApp(packageName, name, userId)
            val optimisticCount = (cached?.openCount ?: DEFAULT_OPEN_COUNT) + 1L
            RAppsSingleton.instance.updateAppState(packageName, name, openCount = optimisticCount, userId = userId)
            persist {
                val committedCount = incrementAtomic(defaults(packageName, name, userId = userId))
                RAppsSingleton.instance.updateAppState(packageName, name, openCount = committedCount, userId = userId)
            }
        }

        @JvmOverloads
        @JvmStatic
        fun setNotificationCount(
            packageName: String?,
            name: String?,
            count: Int,
            userId: Int = AppIdentity.PRIMARY_USER_ID
        ) {
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) return
            val clamped = count.coerceIn(0, MAX_STORED_NOTIFICATION_COUNT)
            RAppsSingleton.instance.updateAppState(packageName, name, notificationCount = clamped, userId = userId)
            persist {
                setNotificationCount(defaults(packageName, name, userId = userId), clamped)
            }
        }

        @JvmStatic
        fun clearOrderForLens(lensId: String) {
            persist { clearOrderForLens(lensId) }
        }

        @JvmOverloads
        @JvmStatic
        fun setAppOrderBatch(apps: List<App>, lensId: String = DEFAULT_LENS_ID) {
            if (apps.isEmpty()) return
            val revisionCounter = orderWriteRevisions.computeIfAbsent(lensId) { AtomicLong(0L) }
            val revision = revisionCounter.incrementAndGet()
            val mutex = orderWriteMutexes.computeIfAbsent(lensId) { Mutex() }
            // DB-006: remember each app's previous in-memory order so a failed write can undo it.
            val previousOrders = mutableListOf<Triple<Pair<String, Int>, String, Int>>()
            val rows = apps.mapIndexedNotNull { index, app ->
                val packageName = app.packageName?.toString()
                val name = app.name?.toString()
                if (packageName.isNullOrBlank() || name.isNullOrBlank()) {
                    null
                } else {
                    RAppsSingleton.instance.findApp(packageName, name, app.userId)
                        ?.let { previousOrders += Triple(packageName to app.userId, name, it.orderNumber) }
                    RAppsSingleton.instance.updateAppOrder(packageName, name, index, app.userId)
                    defaults(packageName, name, lensId, app.userId).copy(orderNumber = index)
                }
            }
            persist(
                // Only the newest order write of this lens may undo itself (see persistLatest).
                onFailure = {
                    if (revision == revisionCounter.get()) {
                        previousOrders.forEach { (pkgUser, n, order) ->
                            RAppsSingleton.instance.updateAppOrder(pkgUser.first, n, order, pkgUser.second)
                        }
                    }
                }
            ) {
                mutex.withLock {
                    if (revision == revisionCounter.get()) setOrders(rows)
                }
            }
        }

        @JvmOverloads
        @JvmStatic
        fun getAppOpened(packageName: String?, name: String?, userId: Int = AppIdentity.PRIMARY_USER_ID): Boolean {
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) return true
            return RAppsSingleton.instance.findApp(packageName, name, userId)?.isOpened ?: true
        }

        @JvmOverloads
        @JvmStatic
        fun setAppOpened(
            packageName: String?,
            name: String?,
            appOpened: Boolean,
            lensId: String = DEFAULT_LENS_ID,
            userId: Int = AppIdentity.PRIMARY_USER_ID
        ) {
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) return
            val previous = RAppsSingleton.instance.findApp(packageName, name, userId)?.isOpened
            RAppsSingleton.instance.updateAppState(packageName, name, isOpened = appOpened, userId = userId)
            val defaults = defaults(packageName, name, lensId, userId)
            persistLatest("opened", defaults.identifier, lensId,
                onFailure = { previous?.let { RAppsSingleton.instance.updateAppState(packageName, name, isOpened = it, userId = userId) } }
            ) { setOpened(defaults, appOpened) }
        }

        @JvmOverloads
        @JvmStatic
        fun getAppVisibility(packageName: String?, name: String?, userId: Int = AppIdentity.PRIMARY_USER_ID): Boolean {
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) return true
            return RAppsSingleton.instance.findApp(packageName, name, userId)?.isVisible ?: true
        }

        @JvmOverloads
        @JvmStatic
        fun setAppVisibility(
            packageName: String?,
            name: String?,
            visible: Boolean,
            lensId: String = DEFAULT_LENS_ID,
            userId: Int = AppIdentity.PRIMARY_USER_ID
        ) {
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) return
            val previous = RAppsSingleton.instance.findApp(packageName, name, userId)?.isVisible
            RAppsSingleton.instance.updateAppState(packageName, name, isVisible = visible, userId = userId)
            val defaults = defaults(packageName, name, lensId, userId)
            persistLatest("visibility", defaults.identifier, lensId,
                onFailure = { previous?.let { RAppsSingleton.instance.updateAppState(packageName, name, isVisible = it, userId = userId) } }
            ) { setVisibility(defaults, visible) }
        }

        @JvmOverloads
        @JvmStatic
        fun getAppOpenCount(packageName: String?, name: String?, userId: Int = AppIdentity.PRIMARY_USER_ID): Long {
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) return 0L
            return RAppsSingleton.instance.findApp(packageName, name, userId)?.openCount ?: 0L
        }

        @JvmOverloads
        @JvmStatic
        fun getAppPaletteColor(packageName: String?, name: String?, userId: Int = AppIdentity.PRIMARY_USER_ID): Int {
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) return 0
            return RAppsSingleton.instance.findApp(packageName, name, userId)?.paletteColor ?: 0
        }

        @JvmOverloads
        @JvmStatic
        fun setAppPaletteColor(
            packageName: String?,
            name: String?,
            color: Int,
            userId: Int = AppIdentity.PRIMARY_USER_ID
        ) {
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) return
            val previous = RAppsSingleton.instance.findApp(packageName, name, userId)?.paletteColor
            RAppsSingleton.instance.updateAppState(packageName, name, paletteColor = color, userId = userId)
            val defaults = defaults(packageName, name, userId = userId)
            persistLatest("palette", defaults.identifier,
                onFailure = {
                    previous?.let { RAppsSingleton.instance.updateAppState(packageName, name, paletteColor = it, userId = userId) }
                }
            ) { setPaletteColor(defaults, color) }
        }

        suspend fun setAppPaletteColorAndAwait(
            packageName: String,
            name: String,
            color: Int,
            userId: Int = AppIdentity.PRIMARY_USER_ID
        ) {
            dao().setPaletteColor(defaults(packageName, name, userId = userId), color)
        }

        @JvmOverloads
        @JvmStatic
        fun setOrganization(
            packageName: String?,
            name: String?,
            favorite: Boolean,
            folderName: String?,
            pinnedZone: PinnedZone,
            lensId: String = DEFAULT_LENS_ID,
            userId: Int = AppIdentity.PRIMARY_USER_ID
        ) {
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) return
            val normalizedFolder = AppOrganizationRules.normalizeFolder(folderName)
            val previous = RAppsSingleton.instance.findApp(packageName, name, userId)
            RAppsSingleton.instance.updateOrganization(
                packageName,
                name,
                favorite,
                normalizedFolder,
                pinnedZone,
                userId
            )
            val defaults = defaults(packageName, name, lensId, userId)
            persistLatest("organization", defaults.identifier, lensId, onFailure = {
                previous?.let {
                    RAppsSingleton.instance.updateOrganization(packageName, name, it.isFavorite, it.folderName, it.pinnedZone, userId)
                }
            }) {
                setOrganization(
                    defaults,
                    favorite,
                    normalizedFolder,
                    pinnedZone.name
                )
            }
        }
    }
}
