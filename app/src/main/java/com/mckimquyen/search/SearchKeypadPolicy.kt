package com.mckimquyen.search

/** Keeps the list usable on short windows where the soft keyboard leaves no result area. */
object SearchKeypadPolicy {
    const val COMPACT_HEIGHT_DP = 480

    @JvmStatic
    fun useT9Keypad(userEnabled: Boolean, screenHeightDp: Int): Boolean =
        userEnabled || (screenHeightDp > 0 && screenHeightDp < COMPACT_HEIGHT_DP)
}
