package dev.shugyo.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.shugyo.data.Prefs
import dev.shugyo.scan.Basket
import dev.shugyo.scan.BasketItem
import dev.shugyo.scan.WebScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/** The in-app browser. Kept alive while the app is open so your textbook stays where you left it. */
class BrowserState(val activity: Activity) {
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var canGoBack by mutableStateOf(false)
    var desktop by mutableStateOf(false)
    private var mobileAgent = ""

    private var view: WebView? = null
    var scanner: WebScanner? = null
        private set

    @SuppressLint("SetJavaScriptEnabled")
    fun webView(): WebView {
        view?.let { return it }
        val w = WebView(activity)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false) // links that open a new window open here instead
        }
        mobileAgent = w.settings.userAgentString
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        val sc = WebScanner(w)
        sc.install()
        scanner = sc
        w.webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView, u: String, favicon: Bitmap?) {
                sc.pageStarted()
                url = u
                canGoBack = v.canGoBack()
            }

            override fun onPageFinished(v: WebView, u: String) {
                url = u
                canGoBack = v.canGoBack()
                if (u.startsWith("http")) Prefs.update { it.copy(lastUrl = u) }
                CookieManager.getInstance().flush()
            }

            override fun doUpdateVisitedHistory(v: WebView, u: String, isReload: Boolean) {
                url = u
                canGoBack = v.canGoBack()
            }

            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme.orEmpty()
                return scheme != "http" && scheme != "https" && scheme != "about" && scheme != "blob" && scheme != "data"
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                progress = p
            }

            override fun onReceivedTitle(v: WebView, t: String?) {
                title = t.orEmpty()
            }
        }
        view = w
        w.loadUrl(Prefs.current.lastUrl.ifBlank { "https://connect.mheducation.com" })
        return w
    }

    val userAgent: String get() = view?.settings?.userAgentString.orEmpty()

    fun load(input: String) {
        val t = input.trim()
        if (t.isEmpty()) return
        val u = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            !t.contains(' ') && t.contains('.') -> "https://$t"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(t, "UTF-8")
        }
        webView().loadUrl(u)
    }

    fun goBack(): Boolean {
        val w = view ?: return false
        if (!w.canGoBack()) return false
        w.goBack()
        return true
    }

    fun reload() = view?.reload()

    fun toggleDesktop() {
        val w = webView()
        desktop = !desktop
        w.settings.userAgentString = if (desktop) {
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        } else mobileAgent
        w.reload()
    }

    fun destroy() {
        view?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        view = null
    }
}

@Composable
fun BrowserScreen(state: BrowserState) {
    val scope = rememberCoroutineScope()
    val items by Basket.items.collectAsState()
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf("") }
    var hideOverlays by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val incoming by Incoming.url

    LaunchedEffect(incoming) {
        incoming?.let {
            state.load(it)
            Incoming.url.value = null
        }
    }
    LaunchedEffect(message) {
        if (message.isNotEmpty()) {
            delay(6000)
            message = ""
        }
    }

    BackHandler {
        if (!state.goBack()) Nav.back()
    }

    fun snapOnce(onDone: (Bitmap?) -> Unit) {
        scope.launch {
            hideOverlays = true
            delay(120)
            val v = state.webView()
            val bmp = WebScanner.snap(state.activity, v)
            hideOverlays = false
            onDone(bmp)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .imePadding(),
    ) {
        // Address bar.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { Nav.back() }) { Icon(Icons.Filled.Close, "Close browser", tint = Paper) }
            OutlinedTextField(
                value = if (editing) field else state.url.removePrefix("https://").removePrefix("www."),
                onValueChange = { field = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = Paper),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Seal, unfocusedBorderColor = Line,
                    focusedContainerColor = Card, unfocusedContainerColor = Card,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    state.load(field)
                    editing = false
                }),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { f ->
                        if (f.isFocused && !editing) {
                            field = state.url
                            editing = true
                        } else if (!f.isFocused) editing = false
                    },
            )
            IconButton(onClick = { state.reload() }) { Icon(Icons.Filled.Refresh, "Reload", tint = Paper) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = Paper) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("📸  Snap whole page (scrolls for you)") },
                        onClick = {
                            menu = false
                            scope.launch {
                                busy = "Snapping the page…"
                                var last: Bitmap? = null
                                var count = 0
                                for (i in 0 until 25) {
                                    hideOverlays = true
                                    delay(150)
                                    val bmp = WebScanner.snap(state.activity, state.webView())
                                    hideOverlays = false
                                    if (bmp == null) break
                                    if (last != null && WebScanner.same(last, bmp)) break
                                    withContext(Dispatchers.IO) { Basket.addSnap(bmp, state.title) }
                                    count++
                                    busy = "Snapped $count screens…"
                                    last = bmp
                                    val moved = state.scanner?.scrollDown() ?: false
                                    if (!moved) break
                                    delay(800)
                                }
                                busy = ""
                                message = if (count > 0) "Added $count screen snaps. Keep going, or tap Build." else "Couldn't snap this page."
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (state.desktop) "📱  Mobile site" else "🖥  Desktop site") },
                        onClick = {
                            menu = false
                            state.toggleDesktop()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("🏠  McGraw Hill Connect") },
                        onClick = {
                            menu = false
                            state.load("https://connect.mheducation.com")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("🔎  Google") },
                        onClick = {
                            menu = false
                            state.load("https://www.google.com")
                        },
                    )
                }
            }
        }
        if (state.progress < 100) Bar(state.progress / 100f, Seal, height = 3.dp, track = Ink)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = {
                    state.webView().also { (it.parent as? ViewGroup)?.removeView(it) }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (!hideOverlays && (message.isNotEmpty() || busy.isNotEmpty())) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardHigh)
                        .clickable { message = "" }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(busy.ifEmpty { message }, color = Paper, fontSize = 14.sp)
                }
            }
        }

        // Scan bar.
        val pages = items.count { it is BasketItem.Page }
        val snaps = items.count { it is BasketItem.Snap }
        Row(
            Modifier
                .fillMaxWidth()
                .background(Card)
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BigButton(
                "🔍 Scan page",
                enabled = busy.isEmpty(),
                onClick = {
                    scope.launch {
                        busy = "Reading the page…"
                        val sc = state.scanner
                        val frames = sc?.scan() ?: emptyList()
                        val page = withContext(Dispatchers.IO) {
                            Basket.pageFromScan(frames, state.url, state.userAgent)
                        }
                        busy = ""
                        message = when {
                            page == null || (page.words < 40 && page.images.isEmpty()) ->
                                "This reader hides its text. Use 📸 Snap (or ⋮ → Snap whole page) instead."
                            items.any { it is BasketItem.Page && it.url == page.url && it.words == page.words } ->
                                "You already scanned this page."
                            else -> {
                                Basket.addPage(page)
                                "Added ${"%,d".format(page.words)} words · ${page.images.size} pictures · ${page.tables} tables"
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1.3f),
            )
            GhostButton(
                "📸 Snap",
                enabled = busy.isEmpty(),
                onClick = {
                    snapOnce { bmp ->
                        if (bmp != null) {
                            scope.launch {
                                withContext(Dispatchers.IO) { Basket.addSnap(bmp, state.title) }
                                message = "Snapped this screen. Scroll and snap again, or tap Build."
                            }
                        } else message = "Couldn't take a picture of this screen."
                    }
                },
                modifier = Modifier.weight(1f),
            )
            BigButton(
                if (pages + snaps == 0) "Build" else "Build · ${pages + snaps}",
                enabled = pages + snaps > 0,
                color = Gold,
                textColor = Ink,
                onClick = { Nav.go(Route.Build) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
