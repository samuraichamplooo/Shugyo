package dev.shugyo.ai

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import dev.shugyo.App

/** Whether the phone has internet, and waiting for it to come back. */
object Net {
    fun online(): Boolean {
        val cm = App.instance.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** Blocks for up to [maxMs] until there's internet. True if there is. */
    fun waitOnline(maxMs: Long, cancelled: () -> Boolean): Boolean {
        val end = SystemClock.elapsedRealtime() + maxMs
        while (SystemClock.elapsedRealtime() < end) {
            if (cancelled()) return false
            if (online()) return true
            Thread.sleep(1000)
        }
        return online()
    }
}
