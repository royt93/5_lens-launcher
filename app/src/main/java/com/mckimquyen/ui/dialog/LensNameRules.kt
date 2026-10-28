package com.mckimquyen.ui.dialog

/** The one rule every lens name field shares (group names and lens names). */
object LensNameRules {
    const val MAX_LENGTH = 64

    /** Trimmed name, or null when blank or longer than [MAX_LENGTH] after trimming. */
    fun normalize(raw: CharSequence?): String? =
        raw?.toString()?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_LENGTH }
}
