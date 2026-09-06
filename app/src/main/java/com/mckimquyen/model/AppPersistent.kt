package com.mckimquyen.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mckimquyen.app.ApplicationScope
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Entity(
    tableName = "APP_PERSISTENT",
    indices = [Index(value = ["IDENTIFIER"], unique = true)]
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
    var pinnedZone: String = PinnedZone.NONE.name
) {
    companion object {
        private const val DEFAULT_ORDER_NUMBER = -1
        private const val DEFAULT_OPEN_COUNT = 0L
        private val orderWriteMutex = Mutex()
        private val orderWriteRevision = AtomicLong(0L)
        private val latestWriteMutexes = ConcurrentHashMap<String, Mutex>()
        private val latestWriteRevisions = ConcurrentHashMap<String, AtomicLong>()

        @JvmStatic
        fun generateIdentifier(packageName: String?, name: String?): String =
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) "" else "$packageName-$name"

        private fun dao(): AppPersistentDao = AppDatabase.getInstance().appPersistentDao()

        private fun defaults(packageName: String, name: String) = AppPersistent(
            packageName = packageName,
            name = name,
            identifier = generateIdentifier(packageName, name),
            openCount = DEFAULT_OPEN_COUNT,
            orderNumber = DEFAULT_ORDER_NUMBER,
            appVisible = true,
            appOpened = true
        )

        private fun persist(operation: suspend AppPersistentDao.() -> Unit): Job =
            ApplicationScope.scope.launch(Dispatchers.IO) {
                try {
                    dao().operation()
                } catch (error: Throwable) {
                    Logger.e("AppPersistent: asynchronous database operation failed", error)
                }
            }

        private fun persistLatest(
            channel: String,
            identifier: String,
            operation: suspend AppPersistentDao.() -> Unit
        ): Job {
            val key = "$channel:$identifier"
            val revisionCounter = latestWriteRevisions.computeIfAbsent(key) { AtomicLong(0L) }
            val revision = revisionCounter.incrementAndGet()
            val mutex = latestWriteMutexes.computeIfAbsent(key) { Mutex() }
            return persist {
                mutex.withLock {
                    if (revision == revisionCounter.get()) operation()
                }
            }
        }

        @JvmStatic
        fun incrementAppCount(packageName: String?, name: String?) {
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) return
            val cached = RAppsSingleton.instance.findApp(packageName, name)
            val optimisticCount = (cached?.openCount ?: DEFAULT_OPEN_COUNT) + 1L
            RAppsSingleton.instance.updateAppState(packageName, name, openCount = optimisticCount)
            persist {
                val committedCount = incrementAtomic(defaults(packageName, name))
                RAppsSingleton.instance.updateAppState(packageName, name, openCount = committedCount)
            }
        }

        @JvmStatic
        fun setAppOrderBatch(apps: List<App>) {
            if (apps.isEmpty()) return
            val revision = orderWriteRevision.incrementAndGet()
            val rows = apps.mapIndexedNotNull { index, app ->
                val packageName = app.packageName?.toString()
                val name = app.name?.toString()
                if (packageName.isNullOrBlank() || name.isNullOrBlank()) {
                    null
                } else {
                    RAppsSingleton.instance.updateAppOrder(packageName, name, index)
                    defaults(packageName, name).copy(orderNumber = index)
                }
            }
            persist {
                orderWriteMutex.withLock {
                    if (revision == orderWriteRevision.get()) setOrders(rows)
                }
            }
        }

        @JvmStatic
        fun getAppOpened(packageName: String?, name: String?): Boolean {
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) return true
            return RAppsSingleton.instance.findApp(packageName, name)?.isOpened ?: true
        }

        @JvmStatic
        fun setAppOpened(packageName: String?, name: String?, appOpened: Boolean) {
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) return
            RAppsSingleton.instance.updateAppState(packageName, name, isOpened = appOpened)
            val defaults = defaults(packageName, name)
            persistLatest("opened", defaults.identifier) { setOpened(defaults, appOpened) }
        }

        @JvmStatic
        fun getAppVisibility(packageName: String?, name: String?): Boolean {
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) return true
            return RAppsSingleton.instance.findApp(packageName, name)?.isVisible ?: true
        }

        @JvmStatic
        fun setAppVisibility(packageName: String?, name: String?, visible: Boolean) {
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) return
            RAppsSingleton.instance.updateAppState(packageName, name, isVisible = visible)
            val defaults = defaults(packageName, name)
            persistLatest("visibility", defaults.identifier) { setVisibility(defaults, visible) }
        }

        @JvmStatic
        fun getAppOpenCount(packageName: String?, name: String?): Long {
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) return 0L
            return RAppsSingleton.instance.findApp(packageName, name)?.openCount ?: 0L
        }

        @JvmStatic
        fun getAppPaletteColor(packageName: String?, name: String?): Int {
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) return 0
            return RAppsSingleton.instance.findApp(packageName, name)?.paletteColor ?: 0
        }

        @JvmStatic
        fun setAppPaletteColor(packageName: String?, name: String?, color: Int) {
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) return
            RAppsSingleton.instance.updateAppState(packageName, name, paletteColor = color)
            val defaults = defaults(packageName, name)
            persistLatest("palette", defaults.identifier) { setPaletteColor(defaults, color) }
        }

        suspend fun setAppPaletteColorAndAwait(packageName: String, name: String, color: Int) {
            dao().setPaletteColor(defaults(packageName, name), color)
        }

        @JvmStatic
        fun setOrganization(
            packageName: String?,
            name: String?,
            favorite: Boolean,
            folderName: String?,
            pinnedZone: PinnedZone
        ) {
            if (packageName.isNullOrBlank() || name.isNullOrBlank()) return
            val normalizedFolder = AppOrganizationRules.normalizeFolder(folderName)
            RAppsSingleton.instance.updateOrganization(
                packageName,
                name,
                favorite,
                normalizedFolder,
                pinnedZone
            )
            val defaults = defaults(packageName, name)
            persistLatest("organization", defaults.identifier) {
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
