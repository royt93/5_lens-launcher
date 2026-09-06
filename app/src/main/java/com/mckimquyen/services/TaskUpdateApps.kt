package com.mckimquyen.services

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import com.mckimquyen.app.ApplicationScope
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.util.Logger
import com.mckimquyen.util.UtilApp
import com.mckimquyen.util.UtilSettings
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Immutable result produced by one PackageManager scan. */
data class AppRefreshSnapshot(
    val apps: List<App>,
    val icons: Map<String, Bitmap> = emptyMap()
)

/** Application-owned refresh state; Activities can re-observe it after recreation. */
sealed interface AppRefreshState {
    data object Idle : AppRefreshState
    data class Loading(val generation: Long) : AppRefreshState
    data class Ready(val generation: Long, val apps: List<App>) : AppRefreshState
    data class Error(val generation: Long, val cause: Throwable) : AppRefreshState
}

/**
 * Serializes installed-app refreshes. A new request cancels the previous scan and a
 * generation guard prevents a stale loader from committing even if cancellation is late.
 * This object must be created once by [Application], not once per package event.
 */
class TaskUpdateApps @JvmOverloads constructor(
    private val packageManager: PackageManager,
    context: Context,
    private val application: Application,
    private val scope: CoroutineScope = ApplicationScope.scope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val debounceMillis: Long = PACKAGE_EVENT_DEBOUNCE_MS,
    private val loader: (suspend () -> AppRefreshSnapshot)? = null
) {
    private val appContext = context.applicationContext ?: application.applicationContext ?: context
    private val requestedGeneration = AtomicLong(0L)
    private val jobLock = Any()

    @Volatile
    private var refreshJob: Job? = null

    private val _state = MutableStateFlow<AppRefreshState>(AppRefreshState.Idle)
    val state: StateFlow<AppRefreshState> = _state.asStateFlow()

    /** Non-blocking Java-compatible entry point. */
    fun execute(): Job {
        val generation = requestedGeneration.incrementAndGet()
        return synchronized(jobLock) {
            refreshJob?.cancel()
            scope.launch {
                _state.value = AppRefreshState.Loading(generation)
                try {
                    if (debounceMillis > 0) delay(debounceMillis)
                    val snapshot = withContext(ioDispatcher) {
                        loader?.invoke() ?: loadSnapshot()
                    }
                    ensureActive()
                    withContext(mainDispatcher) {
                        if (requestedGeneration.get() == generation) {
                            commit(generation, snapshot)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Logger.e("TaskUpdateApps: refresh failed for generation $generation", error)
                    withContext(mainDispatcher) {
                        if (requestedGeneration.get() == generation) {
                            _state.value = AppRefreshState.Error(generation, error)
                        }
                    }
                }
            }.also { refreshJob = it }
        }
    }

    /** Awaitable compatibility entry point used by tests and coroutine callers. */
    suspend fun executeAsync() {
        execute().join()
    }

    fun cancel() {
        synchronized(jobLock) {
            requestedGeneration.incrementAndGet()
            refreshJob?.cancel()
            refreshJob = null
        }
    }

    private suspend fun loadSnapshot(): AppRefreshSnapshot {
        Logger.d("TaskUpdateApps: loading installed apps")
        val utilSettings = UtilSettings(appContext)
        val loadedApps = UtilApp.getApps(
            packageManager,
            appContext,
            application,
            utilSettings.getString(UtilSettings.KEY_ICON_PACK_LABEL_NAME) ?: "",
            utilSettings.sortType
        )

        val apps = ArrayList<App>(loadedApps.size)
        val icons = LinkedHashMap<String, Bitmap>(loadedApps.size)
        loadedApps.forEach { app ->
            app.icon?.let { icon ->
                apps.add(app.copy(icon = null))
                icons[app.packageName.toString()] = icon
            }
        }
        return AppRefreshSnapshot(apps.toList(), icons.toMap())
    }

    private fun commit(generation: Long, snapshot: AppRefreshSnapshot) {
        RAppsSingleton.instance.replaceSnapshot(snapshot.apps, snapshot.icons)
        _state.value = AppRefreshState.Ready(generation, snapshot.apps.toList())
        Logger.d("TaskUpdateApps: committed generation $generation (${snapshot.apps.size} apps)")

        val intent = Intent(application, BroadcastReceivers.AppsLoadedReceiver::class.java)
        application.sendBroadcast(intent)
    }

    companion object {
        const val PACKAGE_EVENT_DEBOUNCE_MS = 150L
    }
}
