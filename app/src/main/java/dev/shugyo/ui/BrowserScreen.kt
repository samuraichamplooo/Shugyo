package dev.shugyo.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import dev.shugyo.data.Link
import dev.shugyo.data.Links
import dev.shugyo.data.Prefs
import dev.shugyo.scan.Basket
import dev.shugyo.scan.BasketItem
import dev.shugyo.scan.PageTools
import dev.shugyo.scan.WebScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.coroutines.resume

private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

/** One page you have open. Links that open a new window (like a textbook launched from Moodle) get their own tab. */
class Tab(val webView: WebView, val scanner: WebScanner) {
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var canGoBack by mutableStateOf(false)

    /** Errors the page ran into, to see why something didn't load. */
    val problems = mutableStateListOf<String>()

    fun problem(text: String) {
        problems.add(text)
        while (problems.size > 40) problems.removeAt(0)
    }

    val label: String
        get() = title.ifBlank { Link("", url).host.ifBlank { "New tab" } }
}

/** A link found on the page. [menu] means it sits in a menu or side panel. */
data class PageLink(val text: String, val url: String, val menu: Boolean)

/** The in-app browser. Kept alive while the app is open so your textbook stays where you left it. */
class BrowserState(val activity: Activity) {
    val tabs = mutableStateListOf<Tab>()
    var index by mutableIntStateOf(0)
    var desktop by mutableStateOf(Prefs.current.desktopSite)
    var showStart by mutableStateOf(false)
    var fileRequest by mutableStateOf<Intent?>(null)
    var fileCallback: ValueCallback<Array<Uri>>? = null
    private var mobileAgent = ""
    private var started = false

    val current: Tab get() = tabs[index.coerceIn(0, tabs.size - 1)]

    fun ensure() {
        if (tabs.isEmpty()) tabs.add(newTab())
    }

    /** The first page, loaded once the browser is on screen (pages laid out off screen can come out blank). */
    fun start() {
        if (started) return
        started = true
        val u = Prefs.current.lastUrl
        if (u.isBlank()) {
            showStart = true
            return
        }
        // Wait until the browser has its real size: some sites measure the screen once, as they load.
        current.webView.doOnLayout { (it as WebView).loadUrl(u) }
    }

    @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
    private fun newTab(): Tab {
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
            // Real new windows, like Chrome: sign-ins and textbook launches often post into one.
            setSupportMultipleWindows(true)
        }
        if (mobileAgent.isEmpty()) mobileAgent = w.settings.userAgentString
        if (desktop) w.settings.userAgentString = DESKTOP_UA
        // Don't announce the app on every request; some sites treat that as a background request.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
            try {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(w.settings, emptySet())
            } catch (e: Exception) {
                // older WebView
            }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        val sc = WebScanner(w)
        sc.install()
        val tab = Tab(w, sc)

        w.webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView, u: String, favicon: Bitmap?) {
                sc.pageStarted()
                tab.url = u
                tab.canGoBack = v.canGoBack()
            }

            override fun onPageFinished(v: WebView, u: String) {
                tab.url = u
                tab.canGoBack = v.canGoBack()
                Links.visited(v.title.orEmpty(), u)
                CookieManager.getInstance().flush()
                if (desktop) v.evaluateJavascript(PageTools.DESKTOP, null)
            }

            override fun doUpdateVisitedHistory(v: WebView, u: String, isReload: Boolean) {
                tab.url = u
                tab.canGoBack = v.canGoBack()
            }

            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme.orEmpty()
                if (scheme == "http" || scheme == "https" || scheme == "about" || scheme == "blob" || scheme == "data" || scheme == "javascript") {
                    return false
                }
                // mailto:, tel:, intent: and app links go to the phone's apps.
                try {
                    val intent = if (scheme == "intent") Intent.parseUri(request.url.toString(), Intent.URI_INTENT_SCHEME)
                    else Intent(Intent.ACTION_VIEW, request.url)
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    tab.problem("No app can open ${request.url}")
                }
                return true
            }

            override fun onReceivedError(v: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) tab.problem("Couldn't load ${request.url}: ${error.description}")
            }

            override fun onReceivedHttpError(v: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame || response.statusCode >= 500) {
                    tab.problem("HTTP ${response.statusCode} from ${request.url}")
                }
            }
        }
        w.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, p: Int) {
                tab.progress = p
            }

            override fun onReceivedTitle(v: WebView, t: String?) {
                tab.title = t.orEmpty()
            }

            override fun onCreateWindow(v: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val child = newTab()
                // Give the new tab the screen's size before it starts loading, so the page lays out right.
                val cur = current.webView
                if (cur.width > 0 && cur.height > 0) {
                    child.webView.measure(
                        android.view.View.MeasureSpec.makeMeasureSpec(cur.width, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(cur.height, android.view.View.MeasureSpec.EXACTLY),
                    )
                    child.webView.layout(0, 0, cur.width, cur.height)
                }
                tabs.add(child)
                index = tabs.size - 1
                showStart = false
                transport.webView = child.webView
                resultMsg.sendToTarget()
                return true
            }

            override fun onCloseWindow(window: WebView) {
                tabs.firstOrNull { it.webView === window }?.let { close(it) }
            }

            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    tab.problem("${m.message()} (${m.sourceId().substringAfterLast('/').take(60)}:${m.lineNumber()})")
                }
                return false
            }

            override fun onShowFileChooser(
                v: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: WebChromeClient.FileChooserParams,
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                val request = try {
                    params.createIntent()
                } catch (e: Exception) {
                    null
                }
                if (request == null) {
                    callback.onReceiveValue(null)
                    fileCallback = null
                    return false
                }
                fileCallback = callback
                fileRequest = request
                return true
            }
        }
        w.setDownloadListener { url, _, _, _, _ ->
            try {
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                tab.problem("Couldn't download $url")
            }
        }
        return tab
    }

    fun select(i: Int) {
        index = i.coerceIn(0, tabs.size - 1)
        showStart = false
    }

    fun close(tab: Tab) {
        val i = tabs.indexOf(tab)
        if (i < 0) return
        tabs.removeAt(i)
        (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
        tab.webView.destroy()
        if (tabs.isEmpty()) tabs.add(newTab())
        if (index >= i) index = (index - 1).coerceAtLeast(0)
        index = index.coerceIn(0, tabs.size - 1)
    }

    fun load(input: String) {
        val t = input.trim()
        if (t.isEmpty()) return
        val u = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            !t.contains(' ') && t.contains('.') -> "https://$t"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(t, "UTF-8")
        }
        ensure()
        started = true
        showStart = false
        current.webView.loadUrl(u)
    }

    /** Back: the page's own history, then close a tab that a link opened, then leave the browser. */
    fun goBack(): Boolean {
        if (showStart && current.url.isNotBlank()) {
            showStart = false
            return true
        }
        val tab = current
        if (tab.webView.canGoBack()) {
            tab.webView.goBack()
            return true
        }
        if (tabs.size > 1) {
            close(tab)
            return true
        }
        return false
    }

    fun reload() = current.webView.reload()

    fun toggleDesktop() {
        desktop = !desktop
        Prefs.update { it.copy(desktopSite = desktop) }
        tabs.forEach { it.webView.settings.userAgentString = if (desktop) DESKTOP_UA else mobileAgent }
        current.webView.reload()
    }

    /** Runs a script in the page you're looking at and returns its answer as text. */
    suspend fun runScript(js: String): String? = suspendCancellableCoroutine { cont ->
        current.webView.evaluateJavascript(js) { raw ->
            val text = try {
                if (raw == null || raw == "null") null else JSONArray("[" + raw + "]").getString(0)
            } catch (e: Exception) {
                null
            }
            cont.resume(text)
        }
    }

    suspend fun links(): List<PageLink> {
        val json = runScript(PageTools.LINKS) ?: return emptyList()
        return try {
            val a = JSONArray(json)
            (0 until a.length()).mapNotNull { i ->
                a.optJSONObject(i)?.let { PageLink(it.optString("t"), it.optString("u"), it.optBoolean("m")) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun check(): List<String> {
        val json = runScript(PageTools.CHECK) ?: return listOf("The page didn't answer.")
        return try {
            val o = JSONObject(json)
            o.keys().asSequence().map { k -> "$k: ${o.opt(k)}" }.toList()
        } catch (e: Exception) {
            listOf(json.take(500))
        }
    }

    fun destroy() {
        tabs.forEach {
            (it.webView.parent as? ViewGroup)?.removeView(it.webView)
            it.webView.destroy()
        }
        tabs.clear()
    }
}

@Composable
fun BrowserScreen(state: BrowserState) {
    remember { state.ensure(); 0 }
    val scope = rememberCoroutineScope()
    val items by Basket.items.collectAsState()
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf("") }
    var hideOverlays by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var problems by remember { mutableStateOf(false) }
    var checkLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var linkList by remember { mutableStateOf<List<PageLink>?>(null) }
    var linkSearch by remember { mutableStateOf("") }
    val incoming by Incoming.url
    val settings by Prefs.settings.collectAsState()
    val tab = state.current

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        state.fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        state.fileCallback = null
    }
    LaunchedEffect(state.fileRequest) {
        val req = state.fileRequest ?: return@LaunchedEffect
        state.fileRequest = null
        try {
            fileLauncher.launch(req)
        } catch (e: Exception) {
            state.fileCallback?.onReceiveValue(null)
            state.fileCallback = null
        }
    }
    LaunchedEffect(Unit) { state.start() }
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
                value = when {
                    editing -> field
                    state.showStart -> ""
                    else -> tab.url.removePrefix("https://").removePrefix("www.")
                },
                onValueChange = { field = it },
                singleLine = true,
                placeholder = { Text("Search or type a web address", fontSize = 14.sp) },
                textStyle = TextStyle(fontSize = 14.sp, color = Paper),
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
                            field = if (state.showStart) "" else tab.url
                            editing = true
                        } else if (!f.isFocused) editing = false
                    },
            )
            IconButton(onClick = { state.reload() }) { Icon(Icons.Filled.Refresh, "Reload", tint = Paper) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = Paper) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val marked = tab.url.isNotBlank() && Links.bookmarks(settings).any { it.url == tab.url }
                    if (!state.showStart && tab.url.startsWith("http")) {
                        DropdownMenuItem(
                            text = { Text(if (marked) "★  Remove bookmark" else "☆  Bookmark this page") },
                            onClick = {
                                menu = false
                                Links.toggleBookmark(tab.title, tab.url)
                                message = if (marked) "Bookmark removed." else "Bookmarked. It's on your start page."
                            },
                        )
                    }
                    if (!state.showStart && tab.url.startsWith("http")) {
                        DropdownMenuItem(
                            text = { Text("🔗  All links on this page (menus too)") },
                            onClick = {
                                menu = false
                                linkSearch = ""
                                scope.launch {
                                    val l = state.links()
                                    if (l.isEmpty()) message = "No links found on this page." else linkList = l
                                }
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("🏠  Start page") },
                        onClick = {
                            menu = false
                            state.showStart = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("📸  Snap whole page (scrolls for you)") },
                        onClick = {
                            menu = false
                            scope.launch {
                                busy = "Snapping the page…"
                                var last: Bitmap? = null
                                var count = 0
                                val t = state.current
                                for (i in 0 until 25) {
                                    hideOverlays = true
                                    delay(150)
                                    val bmp = WebScanner.snap(state.activity, t.webView)
                                    hideOverlays = false
                                    if (bmp == null) break
                                    if (last != null && WebScanner.same(last, bmp)) break
                                    withContext(Dispatchers.IO) { Basket.addSnap(bmp, t.title) }
                                    count++
                                    busy = "Snapped $count screens…"
                                    last = bmp
                                    if (!t.scanner.scrollDown()) break
                                    delay(800)
                                }
                                busy = ""
                                message = if (count > 0) "Added $count screen snaps. Keep going, or tap Build." else "Couldn't snap this page."
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (state.desktop) "📱  Mobile site" else "🖥  Desktop site (full menus)") },
                        onClick = {
                            menu = false
                            state.toggleDesktop()
                        },
                    )
                    if (tab.url.startsWith("http")) {
                        DropdownMenuItem(
                            text = { Text("🌐  Open in Chrome") },
                            onClick = {
                                menu = false
                                try {
                                    state.activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tab.url)))
                                } catch (e: Exception) {
                                    message = "No other browser found."
                                }
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("🩺  Page problems" + if (tab.problems.isNotEmpty()) " (${tab.problems.size})" else "") },
                        onClick = {
                            menu = false
                            problems = true
                        },
                    )
                }
            }
        }

        // Tabs, when a link opened a new window.
        if (state.tabs.size > 1 && !state.showStart) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.tabs.forEachIndexed { i, t ->
                    val on = i == state.index
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (on) CardHigh else Card)
                            .clickable { state.select(i) }
                            .padding(start = 12.dp, end = if (i > 0) 4.dp else 12.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            t.label, color = if (on) Paper else Faint, fontSize = 13.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 160.dp),
                        )
                        if (i > 0) {
                            Text(
                                "✕", color = Faint, fontSize = 13.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .clickable { state.close(t) }
                                    .padding(horizontal = 8.dp),
                            )
                        }
                    }
                }
            }
        }
        if (tab.progress < 100 && !state.showStart) Bar(tab.progress / 100f, Seal, height = 3.dp, track = Ink)

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            key(tab) {
                AndroidView(
                    factory = { tab.webView.also { (it.parent as? ViewGroup)?.removeView(it) } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (state.showStart) {
                StartPage(settings, onGo = { state.load(it) })
            }
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
                enabled = busy.isEmpty() && !state.showStart,
                onClick = {
                    scope.launch {
                        busy = "Reading the page…"
                        val t = state.current
                        val frames = t.scanner.scan()
                        val page = withContext(Dispatchers.IO) {
                            Basket.pageFromScan(frames, t.url, t.webView.settings.userAgentString.orEmpty())
                        }
                        busy = ""
                        message = when {
                            page == null || (page.words < 40 && page.images.isEmpty()) ->
                                "This page hides its text. Use 📸 Snap (or ⋮ → Snap whole page) instead."
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
                enabled = busy.isEmpty() && !state.showStart,
                onClick = {
                    scope.launch {
                        hideOverlays = true
                        delay(120)
                        val t = state.current
                        val bmp = WebScanner.snap(state.activity, t.webView)
                        hideOverlays = false
                        if (bmp != null) {
                            withContext(Dispatchers.IO) { Basket.addSnap(bmp, t.title) }
                            message = "Snapped this screen. Scroll and snap again, or tap Build."
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

    linkList?.let { l ->
        LinksSheet(
            l, linkSearch, { linkSearch = it },
            onPick = {
                linkList = null
                state.load(it.url)
            },
            onDismiss = { linkList = null },
        )
    }

    if (problems) {
        AlertDialog(
            onDismissRequest = { problems = false },
            containerColor = CardHigh,
            title = { Text("Page problems") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(tab.url.ifBlank { "No page open" }, color = Faint, fontSize = 12.sp)
                    if (checkLines.isNotEmpty()) {
                        Text("Page check", color = Gold, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        checkLines.forEach { Text(it, color = Soft, fontSize = 12.sp, lineHeight = 16.sp) }
                        Spacer(Modifier.padding(top = 4.dp))
                    }
                    if (tab.problems.isEmpty()) {
                        Text("No errors on this page so far.", color = Soft)
                    } else {
                        Text("If a page looks wrong, screenshot this and send it to Claude.", color = Gold, fontSize = 13.sp)
                        tab.problems.asReversed().forEach { Text("• $it", color = Soft, fontSize = 12.sp, lineHeight = 16.sp) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { problems = false }) { Text("Close", color = Gold) } },
            dismissButton = {
                TextButton(onClick = { scope.launch { checkLines = state.check() } }) { Text("Run page check", color = Faint) }
            },
        )
    }
}

@Composable
private fun LinksSheet(links: List<PageLink>, search: String, onSearch: (String) -> Unit, onPick: (PageLink) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Card) {
        val q = search.trim().lowercase()
        val shown = links.filter { q.isEmpty() || it.text.lowercase().contains(q) || it.url.lowercase().contains(q) }
        val menus = shown.filter { it.menu }
        val rest = shown.filter { !it.menu }
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Text("Links on this page", style = MaterialTheme.typography.titleLarge, color = Paper)
            Text("Menus that won't open still have their links here. Look for your course or textbook.", color = Faint, fontSize = 13.sp)
            Spacer(Modifier.padding(top = 8.dp))
            OutlinedTextField(
                search, onSearch, singleLine = true,
                placeholder = { Text("Search, e.g. course, textbook, Connect") },
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                if (menus.isNotEmpty()) item { SectionLabel("From menus") }
                items(menus, key = { "m" + it.url }) { l -> LinkItem(l, onPick) }
                if (rest.isNotEmpty()) item { SectionLabel("On the page") }
                items(rest, key = { "p" + it.url }) { l -> LinkItem(l, onPick) }
                if (shown.isEmpty()) item { Text("Nothing matches.", color = Faint, modifier = Modifier.padding(16.dp)) }
            }
        }
    }
}

@Composable
private fun LinkItem(l: PageLink, onPick: (PageLink) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onPick(l) }
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(l.text, color = Paper, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(Link("", l.url).host + Uri.parse(l.url).path.orEmpty(), color = Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Where the browser starts: your bookmarks and the sites you've used lately. */
@Composable
private fun StartPage(settings: dev.shugyo.data.Settings, onGo: (String) -> Unit) {
    val bookmarks = Links.bookmarks(settings)
    val recent = Links.recent(settings).filter { r -> bookmarks.none { it.url == r.url } }
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Where's your textbook?", style = MaterialTheme.typography.headlineSmall, color = Paper)
        Spacer(Modifier.padding(top = 6.dp))
        Text(
            "Type your class site above (like your Moodle), sign in and open the chapter. Then tap Scan page on each page. " +
                "Links that open in a new window get their own tab.",
            color = Faint, fontSize = 14.sp, lineHeight = 20.sp,
        )
        SectionLabel("Bookmarks")
        if (bookmarks.isEmpty()) {
            Text("None yet. On any page, tap ⋮ → Bookmark this page.", color = Faint, fontSize = 14.sp)
        }
        bookmarks.forEach { b -> LinkRow(b, "⭐", onGo, onRemove = { Links.removeBookmark(b.url) }) }
        if (recent.isNotEmpty()) {
            SectionLabel("Recent")
            recent.forEach { r -> LinkRow(r, "🕘", onGo, onRemove = null) }
        }
    }
}

@Composable
private fun LinkRow(link: Link, emoji: String, onGo: (String) -> Unit, onRemove: (() -> Unit)?) {
    InkCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = { onGo(link.url) }, padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 20.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(link.title, color = Paper, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(link.host, color = Faint, fontSize = 12.sp, maxLines = 1)
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove", tint = Faint) }
            }
        }
    }
}
