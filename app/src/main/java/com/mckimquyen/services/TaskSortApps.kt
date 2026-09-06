package com.mckimquyen.services

import android.app.Application
import android.content.Context
import android.content.Intent
import com.mckimquyen.app.ApplicationScope
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App
import com.mckimquyen.util.Logger
import com.mckimquyen.util.UtilAppSorter
import com.mckimquyen.util.UtilSettings
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Application-owned, cancel-latest sorter for app-state and organization edits. */
class TaskSortApps @JvmOverloads constructor(
    context: Context,
    private val application: Application,
    private val scope: CoroutineScope = ApplicationScope.scope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val snapshotLoader: (suspend () -> List<App>)? = null
) {
    private val appContext = context.applicationContext ?: context
    private val requestedGeneration = AtomicLong(0L)
    private val jobLock = Any()

    @Volatile
    private var sortJob: Job? = null

    fun execute(): Job {
        val generation = requestedGeneration.incrementAndGet()
        return synchronized(jobLock) {
            sortJob?.cancel()
            scope.launch {
                try {
                    val snapshot = withContext(ioDispatcher) {
                        snapshotLoader?.invoke() ?: loadSortedSnapshot()
                    }
                    ensureActive()
                    withContext(mainDispatcher) {
                        if (requestedGeneration.get() == generation) commit(snapshot)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Logger.e("TaskSortApps: sort failed for generation $generation", error)
                }
            }.also { sortJob = it }
        }
    }

    fun cancel() {
        synchronized(jobLock) {
            requestedGeneration.incrementAndGet()
            sortJob?.cancel()
            sortJob = null
        }
    }

    private fun loadSortedSnapshot(): List<App> {
        val apps = RAppsSingleton.instance.apps ?: arrayListOf()
        UtilAppSorter.sort(apps, UtilSettings(appContext).sortType)
        return apps.map { app ->
            app.icon?.let { RAppsSingleton.instance.setAppIcon(app.iconCacheKey, it) }
            app.copy(icon = null)
        }
    }

    private fun commit(snapshot: List<App>) {
        RAppsSingleton.instance.apps = ArrayList(snapshot)
        application.sendBroadcast(
            Intent(application, BroadcastReceivers.AppsLoadedReceiver::class.java)
        )
    }
}
