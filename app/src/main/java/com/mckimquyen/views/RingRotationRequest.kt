package com.mckimquyen.views

import com.mckimquyen.model.App

/**
 * FISH-035: one finished rotation waiting for the host to confirm the database write. [apps] is the full new order of
 * lens [lensId]; [token] ties the host's later answer to this exact request (see [LensView.resolveRingCommit]).
 */
class RingRotationRequest(val token: Long, val lensId: String, val apps: List<App>)

/** Called on the main thread when a rotation finished; the host must answer through [LensView.resolveRingCommit]. */
fun interface OnRingRotatedListener {
    fun onRingRotated(request: RingRotationRequest)
}
