package com.mckimquyen.model

enum class PinnedZone(val rank: Int) {
    START(0),
    NONE(1),
    END(2);

    companion object {
        @JvmStatic
        fun fromStored(value: String?): PinnedZone =
            entries.firstOrNull { it.name == value } ?: NONE
    }
}
