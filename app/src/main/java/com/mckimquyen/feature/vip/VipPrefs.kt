package com.mckimquyen.feature.vip

import android.content.Context
import androidx.core.content.edit

class VipPrefs(context: Context) {
    private val sp = context.getSharedPreferences("vip_screen_prefs", Context.MODE_PRIVATE)

    fun saveGrantedAtMs(ms: Long) = sp.edit { putLong("granted_at_ms", ms) }
    fun getGrantedAtMs(): Long = sp.getLong("granted_at_ms", 0L)
    fun clearGrantedAtMs() = sp.edit { remove("granted_at_ms") }

    fun markUserRedeemed() = sp.edit { putBoolean("pref_user_redeemed_key_at_least_once", true) }
    fun userRedeemedAtLeastOnce(): Boolean = sp.getBoolean("pref_user_redeemed_key_at_least_once", false)

    fun saveVipDays(days: Int) = sp.edit { putInt("vip_days", days) }
    fun getVipDays(): Int = sp.getInt("vip_days", 0)
    fun clearVipDays() = sp.edit { remove("vip_days") }
}

