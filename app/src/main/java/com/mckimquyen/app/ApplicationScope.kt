package com.mckimquyen.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application-wide CoroutineScope for long-running operations.
 * <p>
 * This scope is tied to the Application lifecycle and should be used for:
 * - Background tasks that need to continue even when Activities are destroyed
 * - Operations that span multiple Activities
 * - Tasks initiated by Application class or Services
 * <p>
 * Lifecycle: Created once when first accessed, lives until process is killed.
 * <p>
 * Alternative to GlobalScope (which is a delicate API):
 * - Uses SupervisorJob for independent child coroutine failures
 * - Uses Main dispatcher as default (can override in individual launches)
 * - Explicitly scoped to application lifecycle rather than global
 * <p>
 * Usage in Java:
 * ```java
 * ApplicationScope.INSTANCE.getScope().launch(Dispatchers.getIO(), (scope, continuation) -> {
 *     // Background work
 *     return Unit.INSTANCE;
 * });
 * ```
 * <p>
 * Usage in Kotlin:
 * ```kotlin
 * ApplicationScope.scope.launch(Dispatchers.IO) {
 *     // Background work
 * }
 * ```
 */
object ApplicationScope {
    /**
     * Application-wide CoroutineScope.
     * Uses SupervisorJob to ensure child coroutine failures don't affect others.
     */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
}
