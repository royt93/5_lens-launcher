package com.mckimquyen.testing

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.preference.PreferenceManager
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.model.AppDatabase
import org.junit.rules.ExternalResource

/**
 * TEST-005: khôi phục prefs, snapshot app và cài đặt hệ thống sau từng test, kể cả khi test ném lỗi.
 * Rule bao ngoài @Before/@After. Không bảo vệ được khi tiến trình bị kill; bộ chạy bên ngoài phải
 * giữ baseline để phát hiện trường hợp đó. Room vẫn do fixture từng lớp quản lý.
 */
class DeviceStateGuard : ExternalResource() {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private var savedPrefs: List<Pair<SharedPreferences, Map<String, Any?>>> = emptyList()
    private var savedApps: ArrayList<App>? = null
    private var savedSettings: Map<Pair<String, String>, String?> = emptyMap()
    private var savedAppLocales: LocaleList? = null

    override fun before() {
        val stores = listOf(
            PreferenceManager.getDefaultSharedPreferences(context),
            context.getSharedPreferences(SEARCH_PREFERENCES, Context.MODE_PRIVATE),
            context.getSharedPreferences(VIP_PREFERENCES, Context.MODE_PRIVATE),
            context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE)
        )
        savedPrefs = stores.map { prefs ->
            prefs to prefs.all.mapValues { (_, value) -> if (value is Set<*>) value.toSet() else value }
        }
        savedApps = RAppsSingleton.instance.apps
        savedSettings = SETTING_KEYS.associateWith(::readSetting)
        savedAppLocales = readAppLocales()
    }

    override fun after() {
        // Không để lỗi khôi phục prefs ngăn khôi phục font/animation hay snapshot.
        val failures = mutableListOf<Throwable>()
        savedPrefs.forEach { (prefs, values) ->
            runCatching { restorePreferences(prefs, values) }.exceptionOrNull()?.let(failures::add)
        }
        runCatching { RAppsSingleton.instance.apps = savedApps }.exceptionOrNull()?.let(failures::add)
        runCatching { removeOrphanAppRows() }.exceptionOrNull()?.let(failures::add)
        savedSettings.forEach { (setting, value) ->
            runCatching {
                if (readSetting(setting) != value) {
                    val (namespace, key) = setting
                    shell(if (value == null) "settings delete $namespace $key" else "settings put $namespace $key $value")
                    check(readSetting(setting) == value) { "Không khôi phục được $namespace.$key" }
                }
            }.exceptionOrNull()?.let(failures::add)
        }
        runCatching { restoreAppLocales() }.exceptionOrNull()?.let(failures::add)
        failures.firstOrNull()?.let { first ->
            failures.drop(1).forEach(first::addSuppressed)
            throw first
        }
    }

    /**
     * LENS_WORKSPACE and APP_PERSISTENT have no ON DELETE CASCADE, so a test that deletes a lens with
     * `dao.delete(lens)` leaves that lens's app rows behind (21 device classes do). The next class's
     * `replaceWorkspace` then fails its `require(rows.all { it.lensId in lensIds })`. Remove only rows
     * whose lens no longer exists; rows of lenses that still exist are never touched.
     */
    private fun removeOrphanAppRows() = kotlinx.coroutines.runBlocking {
        // Classes that never touch Room may run before anything initialised it.
        AppDatabase.init(context)
        val db = AppDatabase.getInstance()
        val lensIds = db.lensWorkspaceDao().getAll().map { it.id }.toSet()
        db.appPersistentDao().getAllRows().map { it.lensId }.toSet()
            .filter { it !in lensIds }
            .forEach { db.appPersistentDao().deleteForLens(it) }
    }

    /** I18N-001: API 33+ per-app locales live in the framework, not in prefs, so prefs restore can't undo them. */
    private fun readAppLocales(): LocaleList? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales
        else null

    private fun restoreAppLocales() {
        val saved = savedAppLocales ?: return
        val manager = context.getSystemService(android.app.LocaleManager::class.java) ?: return
        if (manager.applicationLocales != saved) manager.applicationLocales = saved
        val deadline = android.os.SystemClock.uptimeMillis() + LOCALE_RESTORE_TIMEOUT_MS
        while (manager.applicationLocales != saved && android.os.SystemClock.uptimeMillis() < deadline) {
            Thread.sleep(LOCALE_RESTORE_POLL_MS)
        }
        check(manager.applicationLocales == saved) { "Không khôi phục được app locales" }
    }

    private fun restorePreferences(prefs: SharedPreferences, values: Map<String, Any?>) {
        if (prefs.all == values) return
        val editor = prefs.edit().clear()
        values.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                null -> Unit
                else -> error("Kiểu prefs không hỗ trợ cho '$key': ${value::class.java}")
            }
        }
        check(editor.commit()) { "Không khôi phục được SharedPreferences" }
    }

    private fun readSetting(setting: Pair<String, String>): String? {
        val (namespace, key) = setting
        return if (namespace == GLOBAL) Settings.Global.getString(context.contentResolver, key)
        else Settings.System.getString(context.contentResolver, key)
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes() }
    }

    private companion object {
        const val LOCALE_RESTORE_TIMEOUT_MS = 5_000L
        const val LOCALE_RESTORE_POLL_MS = 100L
        const val GLOBAL = "global"
        const val SYSTEM = "system"
        const val VIP_PREFERENCES = "vip_screen_prefs"
        const val APP_PREFERENCES = "app_preferences"
        const val SEARCH_PREFERENCES = "app_search_history"
        val SETTING_KEYS = listOf(
            SYSTEM to Settings.System.FONT_SCALE,
            GLOBAL to Settings.Global.ANIMATOR_DURATION_SCALE,
            GLOBAL to Settings.Global.TRANSITION_ANIMATION_SCALE,
            GLOBAL to Settings.Global.WINDOW_ANIMATION_SCALE
        )
    }
}
