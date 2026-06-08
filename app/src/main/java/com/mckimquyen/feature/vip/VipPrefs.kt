package com.mckimquyen.feature.vip

import android.content.Context

class VipPrefs(context: Context) {
    private val sp = context.getSharedPreferences("vip_screen_prefs", Context.MODE_PRIVATE)

    fun saveGrantedAtMs(ms: Long) = sp.edit().putLong("granted_at_ms", ms).apply()
    fun getGrantedAtMs(): Long = sp.getLong("granted_at_ms", 0L)
    fun clearGrantedAtMs() = sp.edit().remove("granted_at_ms").apply()

    fun markUserRedeemed() = sp.edit().putBoolean("pref_user_redeemed_key_at_least_once", true).apply()
    fun userRedeemedAtLeastOnce(): Boolean = sp.getBoolean("pref_user_redeemed_key_at_least_once", false)

    fun saveVipDays(days: Int) = sp.edit().putInt("vip_days", days).apply()
    fun getVipDays(): Int = sp.getInt("vip_days", 0)
    fun clearVipDays() = sp.edit().remove("vip_days").apply()
}

