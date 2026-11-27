package dev.shugyo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import dev.shugyo.scan.Basket
import dev.shugyo.scan.Images
import dev.shugyo.ui.Incoming
import dev.shugyo.ui.Nav
import dev.shugyo.ui.Root
import dev.shugyo.ui.Route
import dev.shugyo.ui.ShugyoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            ShugyoTheme {
                Root(this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        App.visible = true
    }

    override fun onPause() {
        App.visible = false
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Something shared to Shugyō from another app: a link, some text, pictures or a PDF. */
    private fun handle(intent: Intent?) {
        intent ?: return
        val type = intent.type.orEmpty()
        when (intent.action) {
            Intent.ACTION_SEND -> when {
                type.startsWith("text/") -> {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().trim()
                    val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
                    val url = Regex("https?://\\S+").find(text)?.value
                    if (url != null && text.length < url.length + 120) {
                        Incoming.url.value = url
                        Nav.go(Route.Browser)
                    } else if (text.isNotBlank()) {
                        Basket.addText(text, subject)
                        Nav.go(Route.Build)
                    }
                }
                type == "application/pdf" -> {
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let {
                        Incoming.pdf.value = it
                        Nav.go(Route.Build)
                    }
                }
                type.startsWith("image/") -> {
                    IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { addPictures(listOf(it)) }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { addPictures(it) }
            }
        }
    }

    private fun addPictures(uris: List<Uri>) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                uris.forEach { u -> Images.fromUri(this@MainActivity, u)?.let { Basket.addSnap(it, "Shared picture") } }
            }
            Nav.go(Route.Build)
        }
    }
}
