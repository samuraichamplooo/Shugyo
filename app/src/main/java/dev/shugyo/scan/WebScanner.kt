package dev.shugyo.scan

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume

/**
 * Talks to the scanning script in every frame of the in-app browser, and takes pictures of the
 * screen for readers that don't share their text.
 */
class WebScanner(private val webView: WebView) {
    private val tag = "WebScanner"
    private val proxies = CopyOnWriteArrayList<JavaScriptReplyProxy>()
    private val scans = CopyOnWriteArrayList<JSONObject>()
    private val scrolls = CopyOnWriteArrayList<Boolean>()

    val allFrames = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    @SuppressLint("RequiresFeature", "RestrictedApi")
    fun install() {
        if (!allFrames) return
        try {
            WebViewCompat.addWebMessageListener(webView, "ShugyoBridge", setOf("*")) { _, message, _, _, reply ->
                onMessage(message.data ?: return@addWebMessageListener, reply)
            }
            WebViewCompat.addDocumentStartJavaScript(webView, ScanScript.JS, setOf("*"))
        } catch (e: Exception) {
            Log.w(tag, "couldn't set up frame scanning", e)
        }
    }

    /** A new page is loading: the old frames are gone. */
    fun pageStarted() {
        proxies.clear()
    }

    private fun onMessage(data: String, reply: JavaScriptReplyProxy) {
        val o = try {
            JSONObject(data)
        } catch (e: Exception) {
            return
        }
        when (o.optString("kind")) {
            "hello" -> if (proxies.none { it === reply }) proxies.add(reply)
            "scan" -> {
                if (proxies.none { it === reply }) proxies.add(reply)
                o.optJSONObject("data")?.let { scans.add(it) }
            }
            "scroll" -> scrolls.add(o.optBoolean("moved"))
        }
    }

    /** Reads the page in every frame. Returns one result per frame that answered. */
    suspend fun scan(): List<JSONObject> {
        if (allFrames && proxies.isNotEmpty()) {
            scans.clear()
            val asked = withContext(Dispatchers.Main) {
                val list = proxies.toList()
                for (p in list) {
                    try {
                        p.postMessage("scan")
                    } catch (e: Exception) {
                        proxies.remove(p)
                    }
                }
                list.size
            }
            // Frames that went away never answer, so don't wait for every one of them for long.
            var waited = 0
            while (waited < 5000 && scans.size < asked) {
                delay(100)
                waited += 100
                if (scans.isNotEmpty() && waited >= 2500) break
            }
            if (scans.isNotEmpty()) return scans.toList()
        }
        // Older WebView: the main frame only.
        val raw = evaluate(ScanScript.JS + "\n;JSON.stringify(window.__shugyoScan())")
        return listOfNotNull(decodeJsResult(raw)?.let { s -> try { JSONObject(s) } catch (e: Exception) { null } })
    }

    /** Scrolls the page (or the reader inside it) down by most of a screen. False at the bottom. */
    suspend fun scrollDown(): Boolean {
        if (allFrames && proxies.isNotEmpty()) {
            scrolls.clear()
            val asked = withContext(Dispatchers.Main) {
                proxies.forEach { p -> try { p.postMessage("scroll") } catch (e: Exception) { } }
                proxies.size
            }
            var waited = 0
            while (waited < 1500 && scrolls.size < asked) {
                delay(50)
                waited += 50
            }
            return scrolls.any { it }
        }
        val raw = evaluate(ScanScript.JS + "\n;window.__shugyoScroll()")
        return raw == "true"
    }

    private suspend fun evaluate(js: String): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<String?> { cont ->
            webView.evaluateJavascript(js) { cont.resume(it) }
        }
    }

    /** evaluateJavascript hands back a JSON-encoded string. */
    private fun decodeJsResult(raw: String?): String? {
        if (raw == null || raw == "null") return null
        return try {
            JSONArray("[$raw]").getString(0)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /** A picture of exactly what [view] shows on screen (works for readers that draw text as images). */
        suspend fun snap(activity: Activity, view: View): Bitmap? = withContext(Dispatchers.Main) {
            if (view.width <= 0 || view.height <= 0) return@withContext null
            val loc = IntArray(2)
            view.getLocationInWindow(loc)
            val rect = Rect(loc[0], loc[1], loc[0] + view.width, loc[1] + view.height)
            val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            suspendCancellableCoroutine<Bitmap?> { cont ->
                try {
                    PixelCopy.request(activity.window, rect, bmp, { result ->
                        cont.resume(if (result == PixelCopy.SUCCESS) bmp else null)
                    }, Handler(Looper.getMainLooper()))
                } catch (e: Exception) {
                    cont.resume(null)
                }
            }
        }

        /** Roughly whether two snaps show the same thing (the end of the page was reached). */
        fun same(a: Bitmap, b: Bitmap): Boolean {
            if (a.width != b.width || a.height != b.height) return false
            val sa = Bitmap.createScaledBitmap(a, 32, 48, true)
            val sb = Bitmap.createScaledBitmap(b, 32, 48, true)
            var diff = 0L
            for (y in 0 until 48) for (x in 0 until 32) {
                val p = sa.getPixel(x, y)
                val q = sb.getPixel(x, y)
                diff += Math.abs((p shr 16 and 0xff) - (q shr 16 and 0xff)) +
                    Math.abs((p shr 8 and 0xff) - (q shr 8 and 0xff)) + Math.abs((p and 0xff) - (q and 0xff))
            }
            return diff / (32 * 48 * 3) < 3
        }
    }
}
