package com.mckimquyen.search

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.AlarmClock
import android.provider.Settings
import java.util.Locale

/**
 * SEARCH-002: pure, deterministic quick actions surfaced above normal app search results -
 * calculator, offline unit conversion, timer quick-set, battery %, and system Settings
 * deep-links. Tried in that fixed priority order; the first match wins. Malformed/ambiguous
 * input returns null, which callers must treat as "fall through to normal app search" - this
 * engine never throws.
 */
sealed class QuickAction {
    /** A value to display (and offer to copy) - calculator result, converted unit, battery %. */
    data class Info(val label: String, val value: String) : QuickAction()

    /** A value that launches an Intent when tapped - timer quick-set, Settings deep-link. */
    data class Action(val label: String, val intent: Intent) : QuickAction()
}

object QuickActionEngine {

    @JvmStatic
    fun resolve(context: Context, query: CharSequence?): QuickAction? {
        val raw = query?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return resolveCalculator(raw)
            ?: resolveUnitConversion(raw)
            ?: resolveTimer(raw)
            ?: resolveBattery(context, raw)
            ?: resolveSettingsShortcut(raw)
    }

    // ==================================================================== Calculator

    private val CALCULATOR_ALLOWED_CHARS = Regex("^[0-9+\\-*/().\\s]+$")

    // A bare number (optionally negative) is not an "expression" - e.g. searching for an app
    // literally named "5" shouldn't be hijacked into showing a calculator result for "5".
    private val CALCULATOR_BARE_NUMBER = Regex("^\\s*-?[0-9]+(?:\\.[0-9]+)?\\s*$")

    internal fun resolveCalculator(raw: String): QuickAction.Info? {
        val trimmed = raw.trim()
        if (!CALCULATOR_ALLOWED_CHARS.matches(trimmed)) return null
        if (CALCULATOR_BARE_NUMBER.matches(trimmed)) return null
        val result = try {
            ExpressionEvaluator(trimmed).evaluate()
        } catch (e: ArithmeticException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        return QuickAction.Info(trimmed, formatNumber(result))
    }

    private fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "?"
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
        }
    }

    /** Tiny recursive-descent evaluator for +,-,*,/ and parentheses. No dependency needed. */
    private class ExpressionEvaluator(private val text: String) {
        private var pos = 0

        fun evaluate(): Double {
            val value = parseExpression()
            skipWhitespace()
            if (pos != text.length) throw IllegalArgumentException("trailing input at $pos")
            return value
        }

        private fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipWhitespace()
                when (peek()) {
                    '+' -> { pos++; value += parseTerm() }
                    '-' -> { pos++; value -= parseTerm() }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                skipWhitespace()
                when (peek()) {
                    '*' -> { pos++; value *= parseFactor() }
                    '/' -> {
                        pos++
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("division by zero")
                        value /= divisor
                    }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double {
            skipWhitespace()
            if (peek() == '-') {
                pos++
                return -parseFactor()
            }
            if (peek() == '(') {
                pos++
                val value = parseExpression()
                skipWhitespace()
                if (peek() != ')') throw IllegalArgumentException("expected ')' at $pos")
                pos++
                return value
            }
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) pos++
            if (start == pos) throw IllegalArgumentException("expected number at $pos")
            return text.substring(start, pos).toDouble()
        }

        private fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        private fun peek(): Char? = if (pos < text.length) text[pos] else null
    }

    // ==================================================================== Unit conversion

    private val UNIT_CONVERSION_PATTERN = Regex(
        "^(-?[0-9]+(?:\\.[0-9]+)?)\\s*([a-zA-ZÀ-ỹ°]+)\\s+(?:to|sang|->|=>)\\s+([a-zA-ZÀ-ỹ°]+)$",
        RegexOption.IGNORE_CASE
    )

    private val LENGTH_TO_METERS = mapOf(
        "mm" to 0.001, "cm" to 0.01, "m" to 1.0, "km" to 1000.0,
        "in" to 0.0254, "ft" to 0.3048, "yd" to 0.9144, "mi" to 1609.344
    )
    private val WEIGHT_TO_GRAMS = mapOf(
        "mg" to 0.001, "g" to 1.0, "kg" to 1000.0, "lb" to 453.592, "oz" to 28.3495
    )
    private val TEMPERATURE_UNITS = setOf("c", "celsius", "f", "fahrenheit", "k", "kelvin")

    internal fun resolveUnitConversion(raw: String): QuickAction.Info? {
        val match = UNIT_CONVERSION_PATTERN.matchEntire(raw.trim()) ?: return null
        val amount = match.groupValues[1].toDoubleOrNull() ?: return null
        val fromUnit = AppSearchEngine.normalize(match.groupValues[2]).replace(" ", "")
        val toUnit = AppSearchEngine.normalize(match.groupValues[3]).replace(" ", "")

        val result = when {
            fromUnit in TEMPERATURE_UNITS || toUnit in TEMPERATURE_UNITS ->
                convertTemperature(amount, fromUnit, toUnit)
            LENGTH_TO_METERS.containsKey(fromUnit) || LENGTH_TO_METERS.containsKey(toUnit) ->
                convertUsing(LENGTH_TO_METERS, amount, fromUnit, toUnit)
            WEIGHT_TO_GRAMS.containsKey(fromUnit) || WEIGHT_TO_GRAMS.containsKey(toUnit) ->
                convertUsing(WEIGHT_TO_GRAMS, amount, fromUnit, toUnit)
            else -> null
        } ?: return null

        return QuickAction.Info(raw.trim(), "${formatNumber(result)} ${match.groupValues[3].trim()}")
    }

    private fun convertUsing(table: Map<String, Double>, amount: Double, from: String, to: String): Double? {
        val fromFactor = table[from] ?: return null
        val toFactor = table[to] ?: return null
        return amount * fromFactor / toFactor
    }

    private fun convertTemperature(amount: Double, from: String, to: String): Double? {
        val celsius = when (from) {
            "c", "celsius" -> amount
            "f", "fahrenheit" -> (amount - 32.0) * 5.0 / 9.0
            "k", "kelvin" -> amount - 273.15
            else -> return null
        }
        return when (to) {
            "c", "celsius" -> celsius
            "f", "fahrenheit" -> celsius * 9.0 / 5.0 + 32.0
            "k", "kelvin" -> celsius + 273.15
            else -> null
        }
    }

    // ==================================================================== Timer quick-set

    private val TIMER_PATTERN = Regex(
        "(?:hẹn giờ|hen gio|đặt hẹn giờ|dat hen gio|timer|set timer(?:\\s+for)?)\\s+([0-9]+)\\s*(phút|phut|minutes?|min|giờ|gio|hours?|giây|giay|seconds?|sec)?",
        RegexOption.IGNORE_CASE
    )
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3600

    internal fun resolveTimer(raw: String): QuickAction.Action? {
        val match = TIMER_PATTERN.find(raw) ?: return null
        val amount = match.groupValues[1].toIntOrNull() ?: return null
        if (amount <= 0) return null
        val unit = AppSearchEngine.normalize(match.groupValues[2])
        val seconds = when {
            unit.startsWith("gia") || unit.startsWith("giâ") || unit.startsWith("second") || unit.startsWith("sec") -> amount
            unit.startsWith("gi") || unit.startsWith("hour") -> amount * SECONDS_PER_HOUR
            else -> amount * SECONDS_PER_MINUTE
        }
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
        return QuickAction.Action(raw.trim(), intent)
    }

    // ==================================================================== Battery %

    private val BATTERY_KEYWORDS = setOf(
        "pin", "battery", "pin dien thoai", "battery level", "phan tram pin",
        "battery percentage", "muc pin", "pin may"
    )

    internal fun resolveBattery(context: Context, raw: String): QuickAction.Info? {
        val normalized = AppSearchEngine.normalize(raw)
        if (normalized !in BATTERY_KEYWORDS) return null
        val batteryStatus = context.applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return null
        val level = batteryStatus.getIntExtra("level", -1)
        val scale = batteryStatus.getIntExtra("scale", -1)
        if (level < 0 || scale <= 0) return null
        val percent = (level * 100 / scale.toFloat()).toInt()
        return QuickAction.Info(raw.trim(), "$percent%")
    }

    // ==================================================================== Settings shortcuts

    // Owner-reviewable table; some Settings.ACTION_* constants get deprecated across API levels,
    // so this deserves an occasional look when bumping compileSdk/targetSdk.
    private val SETTINGS_KEYWORDS: Map<String, String> = mapOf(
        "wifi" to Settings.ACTION_WIFI_SETTINGS,
        "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "am luong" to Settings.ACTION_SOUND_SETTINGS,
        "volume" to Settings.ACTION_SOUND_SETTINGS,
        "sound" to Settings.ACTION_SOUND_SETTINGS,
        "hien thi" to Settings.ACTION_DISPLAY_SETTINGS,
        "display" to Settings.ACTION_DISPLAY_SETTINGS,
        "ngon ngu" to Settings.ACTION_LOCALE_SETTINGS,
        "language" to Settings.ACTION_LOCALE_SETTINGS,
        "vi tri" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "ngay gio" to Settings.ACTION_DATE_SETTINGS,
        "date time" to Settings.ACTION_DATE_SETTINGS,
        "mang" to Settings.ACTION_WIRELESS_SETTINGS,
        "network" to Settings.ACTION_WIRELESS_SETTINGS,
        "bao mat" to Settings.ACTION_SECURITY_SETTINGS,
        "security" to Settings.ACTION_SECURITY_SETTINGS
    )

    internal fun resolveSettingsShortcut(raw: String): QuickAction.Action? {
        val normalized = AppSearchEngine.normalize(raw)
        val action = SETTINGS_KEYWORDS[normalized] ?: return null
        return QuickAction.Action(raw.trim(), Intent(action))
    }
}
