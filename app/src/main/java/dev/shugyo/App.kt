package dev.shugyo

import android.app.Application
import dev.shugyo.data.Prefs
import dev.shugyo.data.Store
import dev.shugyo.scan.Basket

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        // If the app ever closes on its own, keep what went wrong so the next launch can show it.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val trace = android.util.Log.getStackTraceString(e)
                java.io.File(filesDir, CRASH_FILE).writeText(
                    "Shugyō ${BuildConfigInfo.version} · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL}\n" +
                        "Thread: ${thread.name}\n\n$trace"
                )
            } catch (t: Throwable) {
                // nothing more we can do
            }
            previous?.uncaughtException(thread, e)
        }
        BuildConfigInfo.version = try {
            packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        } catch (e: Exception) {
            ""
        }
        Prefs.init(this)
        Store.init(this)
        Basket.init(this)
    }

    companion object {
        const val CRASH_FILE = "last_crash.txt"
        lateinit var instance: App
            private set

        /** True while Shugyō is on screen. */
        @Volatile
        var visible = false
    }
}

/** The version, for crash reports. */
object BuildConfigInfo {
    var version = ""
}
