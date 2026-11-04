package com.mckimquyen.util

/** FISH-033: quyết định thuần có đăng ký sensor nghiêng hay không; mọi điều kiện phải đúng cùng lúc. */
object TiltGate {
    fun shouldRun(
        enabled: Boolean,
        reduceMotion: Boolean,
        resumed: Boolean,
        searchShowing: Boolean,
        listMode: Boolean,
        sensorAvailable: Boolean
    ): Boolean = enabled && !reduceMotion && resumed && !searchShowing && !listMode && sensorAvailable
}
