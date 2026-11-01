package com.mckimquyen.util

import java.util.Locale

/** FISH-037: the optical-settings line printed under a lens name. Pure: no Context, no resources. */
object PolaroidCaptionFormatter {
    private const val DASH = "–"
    private const val SEPARATOR = " · "
    private const val PERCENT = 100f

    @JvmStatic
    fun opticalLine(profileName: String, distortion: Float, scale: Float, hood: Float, locale: Locale): String {
        val profile = profileName.trim().ifEmpty { DASH }
        val hoodText = if (hood.isFinite()) "${Math.round(hood * PERCENT)}%" else DASH
        return listOf(
            profile,
            "d=${oneDecimal(distortion, locale)}",
            "s=${oneDecimal(scale, locale)}",
            "hood=$hoodText"
        ).joinToString(SEPARATOR)
    }

    private fun oneDecimal(value: Float, locale: Locale): String =
        if (value.isFinite()) String.format(locale, "%.1f", value) else DASH
}
