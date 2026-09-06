package com.mckimquyen.model

object AppOrganizationRules {
    const val MAX_FOLDER_LENGTH = 40

    @JvmStatic
    fun normalizeFolder(value: String?): String? {
        val cleaned = value
            ?.filterNot(Char::isISOControl)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return null
        val codePointCount = cleaned.codePointCount(0, cleaned.length)
        if (codePointCount <= MAX_FOLDER_LENGTH) return cleaned
        val endIndex = cleaned.offsetByCodePoints(0, MAX_FOLDER_LENGTH)
        return cleaned.substring(0, endIndex)
    }
}
