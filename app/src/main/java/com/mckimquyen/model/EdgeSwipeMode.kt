package com.mckimquyen.model

enum class EdgeSwipeMode {
    OFF,
    BOTH,
    RIGHT_ONLY,
    LEFT_ONLY;

    companion object {
        @JvmStatic
        fun fromStored(value: String?): EdgeSwipeMode =
            entries.firstOrNull { it.name == value } ?: BOTH
    }
}
