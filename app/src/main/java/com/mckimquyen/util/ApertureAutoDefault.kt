package com.mckimquyen.util

/** Initial distortion for a duplicated lens, based on the number of app rows copied into it. */
object ApertureAutoDefault {
    const val SMALL_LENS_MAX_APP_COUNT = 24
    const val LARGE_LENS_MIN_APP_COUNT = 100
    const val SMALL_LENS_DISTORTION = 3.5f
    const val MEDIUM_LENS_DISTORTION = 2.5f
    const val LARGE_LENS_DISTORTION = 1.8f

    fun forAppCount(count: Int): Float = when {
        count.coerceAtLeast(0) <= SMALL_LENS_MAX_APP_COUNT -> SMALL_LENS_DISTORTION
        count < LARGE_LENS_MIN_APP_COUNT -> MEDIUM_LENS_DISTORTION
        else -> LARGE_LENS_DISTORTION
    }
}
