package com.mckimquyen.model

enum class SearchFilterPreset {
    ALL,
    THIS_LENS,
    FAVORITES,
    HIDDEN;

    companion object {
        @JvmStatic
        fun fromStored(value: String?): SearchFilterPreset =
            entries.firstOrNull { it.name == value } ?: ALL
    }
}
