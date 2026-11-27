package dev.shugyo

import android.app.Application
import dev.shugyo.data.Prefs
import dev.shugyo.data.Store
import dev.shugyo.scan.Basket

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Store.init(this)
        Basket.init(this)
    }
}
