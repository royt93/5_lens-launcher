package com.mckimquyen.search

import android.content.Context
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the contact row in the search panel should show. */
sealed interface ContactSearchUiState {
    data object Hidden : ContactSearchUiState
    data object PermissionRequest : ContactSearchUiState
    data class Result(val contact: ContactSearchResult) : ContactSearchUiState
}

/**
 * SEARCH-013: runs the ContentProvider contact lookup off the main thread, debounced, and drops
 * results that a newer keystroke (or [close]) has already superseded. Cheap gating (explicit
 * contact prefix, permission) stays synchronous so the permission row appears immediately.
 */
class ContactSearchController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS,
    private val searchFunction: (Context, CharSequence?) -> ContactSearchResult? = { ctx, query ->
        ContactSearchEngine.firstContactForQuery(ctx, query)
    },
    private val onStateChanged: (ContactSearchUiState) -> Unit
) {
    private var currentJob: Job? = null
    private var ownedScopeJob: Job? = null
    private val currentGeneration = AtomicLong(0L)

    @Volatile
    private var closed = false

    fun onQueryChanged(query: CharSequence?) {
        if (closed) return
        val generation = currentGeneration.incrementAndGet()
        currentJob?.cancel()
        currentJob = null

        if (ContactSearchEngine.shouldShowPermissionRequest(context, query)) {
            onStateChanged(ContactSearchUiState.PermissionRequest)
            return
        }
        if (!ContactSearchEngine.isExplicitContactQuery(query) || !ContactSearchEngine.hasContactsPermission(context)) {
            onStateChanged(ContactSearchUiState.Hidden)
            return
        }

        currentJob = scope.launch(mainDispatcher) {
            try {
                if (debounceMs > 0) delay(debounceMs)
                ensureActive()
                val result = withContext(ioDispatcher) { searchFunction(context, query) }
                ensureActive()
                if (currentGeneration.get() == generation && !closed) {
                    onStateChanged(result?.let { ContactSearchUiState.Result(it) } ?: ContactSearchUiState.Hidden)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (currentGeneration.get() == generation && !closed) {
                    onStateChanged(ContactSearchUiState.Hidden)
                }
            }
        }
    }

    fun cancel() {
        currentGeneration.incrementAndGet()
        currentJob?.cancel()
        currentJob = null
    }

    /** Cancels pending work and the scope owned by [create]; later queries are ignored. */
    fun close() {
        closed = true
        cancel()
        ownedScopeJob?.cancel()
    }

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 150L

        /** Activity-facing factory: own scope on Main, released by [close]. */
        @JvmStatic
        fun create(context: Context, onStateChanged: (ContactSearchUiState) -> Unit): ContactSearchController {
            val job = SupervisorJob()
            val scope = CoroutineScope(Dispatchers.Main.immediate + job)
            return ContactSearchController(context.applicationContext, scope, onStateChanged = onStateChanged)
                .also { it.ownedScopeJob = job }
        }
    }
}
