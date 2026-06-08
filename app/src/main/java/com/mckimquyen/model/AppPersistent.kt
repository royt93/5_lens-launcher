package com.mckimquyen.model

import androidx.room.*

@Entity(tableName = "APP_PERSISTENT")
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
    var paletteColor: Int = 0
) {
    companion object {
        private const val DEFAULT_APP_VISIBILITY = true
        private const val DEFAULT_APP_OPENED = true
        private const val DEFAULT_ORDER_NUMBER = -1
        private const val DEFAULT_OPEN_COUNT = 0L
        private const val FIRST_OPEN_COUNT = 1L

        @JvmStatic
        fun generateIdentifier(packageName: String?, name: String?): String {
            if (packageName == null || name == null) {
                return ""
            }
            return "$packageName-$name"
        }

        private fun getDao(): AppPersistentDao {
            return AppDatabase.getInstance().appPersistentDao()
        }

        @JvmStatic
        fun incrementAppCount(packageName: String?, name: String?) {
            if (packageName == null || name == null) return
            val identifier = generateIdentifier(packageName, name)
            val dao = getDao()
            val app = dao.findByIdentifier(identifier)
            val currentCount = if (app != null) {
                app.openCount += 1
                dao.update(app)
                app.openCount
            } else {
                val newApp = AppPersistent(
                    packageName = packageName,
                    name = name,
                    identifier = identifier,
                    openCount = FIRST_OPEN_COUNT,
                    orderNumber = DEFAULT_ORDER_NUMBER,
                    appVisible = DEFAULT_APP_VISIBILITY,
                    appOpened = DEFAULT_APP_OPENED
                )
                dao.insert(newApp)
                FIRST_OPEN_COUNT
            }
            com.mckimquyen.app.RAppsSingleton.instance.updateAppState(packageName, name, openCount = currentCount)
        }

        @JvmStatic
        fun setAppOrderNumber(packageName: String?, name: String?, orderNumber: Int) {
            if (packageName == null || name == null) return
            val identifier = generateIdentifier(packageName, name)
            val dao = getDao()
            val app = dao.findByIdentifier(identifier)
            if (app != null) {
                app.orderNumber = orderNumber
                dao.update(app)
            } else {
                val newApp = AppPersistent(
                    packageName = packageName,
                    name = name,
                    identifier = identifier,
                    openCount = DEFAULT_OPEN_COUNT,
                    orderNumber = orderNumber,
                    appVisible = DEFAULT_APP_VISIBILITY,
                    appOpened = DEFAULT_APP_OPENED
                )
                dao.insert(newApp)
            }
        }

        @JvmStatic
        fun getAppOpened(packageName: String?, name: String?): Boolean {
            if (packageName == null || name == null) return true
            val identifier = generateIdentifier(packageName, name)
            val app = getDao().findByIdentifier(identifier)
            return app?.appOpened ?: true
        }

        @JvmStatic
        fun setAppOpened(packageName: String?, name: String?, appOpened: Boolean) {
            if (packageName == null || name == null) return
            val identifier = generateIdentifier(packageName, name)
            val dao = getDao()
            val app = dao.findByIdentifier(identifier)
            if (app != null) {
                app.appOpened = appOpened
                dao.update(app)
            } else {
                val newApp = AppPersistent(
                    packageName = packageName,
                    name = name,
                    identifier = identifier,
                    openCount = DEFAULT_OPEN_COUNT,
                    orderNumber = DEFAULT_ORDER_NUMBER,
                    appVisible = DEFAULT_APP_VISIBILITY,
                    appOpened = appOpened
                )
                dao.insert(newApp)
            }
            com.mckimquyen.app.RAppsSingleton.instance.updateAppState(packageName, name, isOpened = appOpened)
        }

        @JvmStatic
        fun getAppVisibility(packageName: String?, name: String?): Boolean {
            if (packageName == null || name == null) return true
            val identifier = generateIdentifier(packageName, name)
            val app = getDao().findByIdentifier(identifier)
            return app?.appVisible ?: true
        }

        @JvmStatic
        fun setAppVisibility(packageName: String?, name: String?, visible: Boolean) {
            if (packageName == null || name == null) return
            val identifier = generateIdentifier(packageName, name)
            val dao = getDao()
            val app = dao.findByIdentifier(identifier)
            if (app != null) {
                app.appVisible = visible
                dao.update(app)
            } else {
                val newApp = AppPersistent(
                    packageName = packageName,
                    name = name,
                    identifier = identifier,
                    openCount = DEFAULT_OPEN_COUNT,
                    orderNumber = DEFAULT_ORDER_NUMBER,
                    appVisible = visible,
                    appOpened = DEFAULT_APP_OPENED
                )
                dao.insert(newApp)
            }
            com.mckimquyen.app.RAppsSingleton.instance.updateAppState(packageName, name, isVisible = visible)
        }

        @JvmStatic
        fun getAppOpenCount(packageName: String?, name: String?): Long {
            if (packageName == null || name == null) return 0
            val identifier = generateIdentifier(packageName, name)
            val app = getDao().findByIdentifier(identifier)
            return app?.openCount ?: 0L
        }

        @JvmStatic
        fun getAppPaletteColor(packageName: String?, name: String?): Int {
            if (packageName == null || name == null) return 0
            val identifier = generateIdentifier(packageName, name)
            val app = getDao().findByIdentifier(identifier)
            return app?.paletteColor ?: 0
        }

        @JvmStatic
        fun setAppPaletteColor(packageName: String?, name: String?, color: Int) {
            if (packageName == null || name == null) return
            val identifier = generateIdentifier(packageName, name)
            val dao = getDao()
            val app = dao.findByIdentifier(identifier)
            if (app != null) {
                app.paletteColor = color
                dao.update(app)
            } else {
                val newApp = AppPersistent(
                    packageName = packageName,
                    name = name,
                    identifier = identifier,
                    openCount = DEFAULT_OPEN_COUNT,
                    orderNumber = DEFAULT_ORDER_NUMBER,
                    appVisible = DEFAULT_APP_VISIBILITY,
                    appOpened = DEFAULT_APP_OPENED,
                    paletteColor = color
                )
                dao.insert(newApp)
            }
        }
    }
}
