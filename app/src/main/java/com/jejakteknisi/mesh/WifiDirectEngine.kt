package com.jejakteknisi.mesh

import android.content.Context

/**
 * Compatibility stub kept so older repository copies that still reference this
 * class continue to compile. Internet Intercom V2.0 does not use Wi-Fi Direct.
 */
class WifiDirectEngine(private val context: Context) {
    var status: String = "Wi-Fi Direct tidak digunakan"
        private set

    var onStatus: ((String) -> Unit)? = null

    fun start() {
        status = "Internet Intercom aktif — gunakan Wi-Fi atau data seluler"
        onStatus?.invoke(status)
    }

    fun stop() {
        status = "Wi-Fi Direct tidak digunakan"
        onStatus?.invoke(status)
    }
}
