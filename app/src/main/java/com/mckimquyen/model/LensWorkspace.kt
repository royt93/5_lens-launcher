package com.mckimquyen.model

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * FISH-008: Represents an independent Lens workspace (e.g. "Lens 1", "Work", "Personal").
 * Each workspace owns its own app organization layout (pinned zones, custom order, visibility,
 * favorites, folders) in [AppPersistent].
 */
@Keep
@Entity(tableName = "LENS_WORKSPACE")
data class LensWorkspace(
    @PrimaryKey
    @ColumnInfo(name = "ID")
    val id: String,
    @ColumnInfo(name = "NAME")
    val name: String,
    @ColumnInfo(name = "ORDER_INDEX", defaultValue = "0")
    val orderIndex: Int = 0,
    @ColumnInfo(name = "CREATED_AT", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_LENS_ID = "default"
        const val DEFAULT_LENS_NAME = "Lens 1"

        fun createDefault(): LensWorkspace = LensWorkspace(
            id = DEFAULT_LENS_ID,
            name = DEFAULT_LENS_NAME,
            orderIndex = 0,
            createdAt = 0L
        )
    }
}
