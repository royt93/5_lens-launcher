package com.mckimquyen.util

import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests cho [UtilNightModeUtil]. Không có test nào tồn tại cho class này trước đây.
 * Thêm khi dọn lint [SwitchIntDef] (đổi `MODE_NIGHT_AUTO` — alias deprecated, cùng giá trị
 * int với `MODE_NIGHT_AUTO_TIME` — sang chính `MODE_NIGHT_AUTO_TIME`) để khóa lại hành vi
 * của mọi nhánh trong cả hai chiều convert.
 */
class UtilNightModeUtilTest {

    @Test
    fun `getNightModeDisplayName maps every known mode correctly`() {
        assertEquals("Light", UtilNightModeUtil.getNightModeDisplayName(AppCompatDelegate.MODE_NIGHT_NO))
        assertEquals("Dark", UtilNightModeUtil.getNightModeDisplayName(AppCompatDelegate.MODE_NIGHT_YES))
        assertEquals("Auto", UtilNightModeUtil.getNightModeDisplayName(AppCompatDelegate.MODE_NIGHT_AUTO_TIME))
        assertEquals(
            "Follow System",
            UtilNightModeUtil.getNightModeDisplayName(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        )
    }

    @Test
    fun `getNightModeDisplayName falls back to Follow System for unmapped modes`() {
        assertEquals(
            "Follow System",
            UtilNightModeUtil.getNightModeDisplayName(AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY)
        )
        assertEquals(
            "Follow System",
            UtilNightModeUtil.getNightModeDisplayName(AppCompatDelegate.MODE_NIGHT_UNSPECIFIED)
        )
    }

    @Test
    fun `getNightModeFromDisplayName maps every known display name correctly`() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, UtilNightModeUtil.getNightModeFromDisplayName("Light"))
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, UtilNightModeUtil.getNightModeFromDisplayName("Dark"))
        assertEquals(AppCompatDelegate.MODE_NIGHT_AUTO_TIME, UtilNightModeUtil.getNightModeFromDisplayName("Auto"))
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            UtilNightModeUtil.getNightModeFromDisplayName("Follow System")
        )
    }

    @Test
    fun `getNightModeFromDisplayName falls back to Follow System for unknown or null names`() {
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            UtilNightModeUtil.getNightModeFromDisplayName("garbage")
        )
        assertEquals(
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            UtilNightModeUtil.getNightModeFromDisplayName(null)
        )
    }

    @Test
    fun `display name round-trips back to the same mode for every known mode`() {
        val modes = listOf(
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_AUTO_TIME,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )
        modes.forEach { mode ->
            val displayName = UtilNightModeUtil.getNightModeDisplayName(mode)
            assertEquals(mode, UtilNightModeUtil.getNightModeFromDisplayName(displayName))
        }
    }
}
