package dev.shugyo.scan

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/** One piece of a scanned page. */
data class Block(
    val kind: String,          // h, p, li, table, img
    val text: String,
    val level: Int = 0,        // heading level
    val rows: List<List<String>> = emptyList(),
    val imageId: String = "",  // for pictures: img1, img2…
    val extra: String = "",    // for glossary entries: the definition
)

/** A picture found on a page, saved in the basket folder. */
data class PageImage(val id: String, val file: File, val caption: String)

sealed class BasketItem {
    abstract val id: String

    /** A web page (or pasted text) read as text, with its pictures. */
    data class Page(
        override val id: String,
        val title: String,
        val url: String,
        val blocks: List<Block>,
        val images: List<PageImage>,
        val words: Int,
    ) : BasketItem() {
        val tables get() = blocks.count { it.kind == "table" }
        val definitions get() = blocks.count { it.kind == "gloss" }
    }

    /** A picture of a page: a screen snap, a photo, a screenshot or a PDF page. */
    data class Snap(override val id: String, val label: String, val file: File, val from: String) : BasketItem()
}

/**
 * Everything scanned so far for the next study set. You can scan several pages of a chapter (and snap
 * screens or add photos) before building. Kept on the phone, so closing the app doesn't lose it.
 */
object Basket {
    private const val TAG = "Basket"
    private lateinit var dir: File
    private val _items = MutableStateFlow<List<BasketItem>>(emptyList())
    val items: StateFlow<List<BasketItem>> = _items
    private var imageCounter = 0
    private var snapCounter = 0
    private val saver = Executors.newSingleThreadExecutor()

    fun init(context: Context) {
        File(context.cacheDir, "basket").deleteRecursively()   // where early builds kept it
        dir = File(context.filesDir, "basket").apply { mkdirs() }
        load()
    }

    private fun setItems(list: List<BasketItem>) {
        _items.value = list
        val json = toJson(list)
        saver.execute {
            try {
                File(dir, "basket.json").writeText(json)
            } catch (e: Exception) {
                Log.w(TAG, "couldn't save the basket", e)
            }
        }
    }

    fun clear() {
        setItems(emptyList())
        imageCounter = 0
        snapCounter = 0
        saver.execute { dir.listFiles()?.forEach { if (it.name != "basket.json") it.deleteRecursively() } }
    }

    fun remove(id: String) {
        setItems(_items.value.filter { it.id != id })
    }

    private fun toJson(list: List<BasketItem>): String {
        val a = JSONArray()
        for (it in list) {
            when (it) {
                is BasketItem.Page -> a.put(
                    JSONObject().put("type", "page").put("id", it.id).put("title", it.title).put("url", it.url)
                        .put("words", it.words)
                        .put("blocks", JSONArray(it.blocks.map { b ->
                            JSONObject().put("k", b.kind).put("t", b.text).put("l", b.level).put("img", b.imageId)
                                .put("x", b.extra).put("rows", JSONArray(b.rows.map { r -> JSONArray(r) }))
                        }))
                        .put("images", JSONArray(it.images.map { im ->
                            JSONObject().put("id", im.id).put("file", im.file.name).put("cap", im.caption)
                        }))
                )
                is BasketItem.Snap -> a.put(
                    JSONObject().put("type", "snap").put("id", it.id).put("label", it.label)
                        .put("file", it.file.name).put("from", it.from)
                )
            }
        }
        return a.toString()
    }

    private fun load() {
        val f = File(dir, "basket.json")
        if (!f.exists()) return
        try {
            val a = JSONArray(f.readText())
            val list = ArrayList<BasketItem>()
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                if (o.optString("type") == "snap") {
                    val file = File(dir, o.optString("file"))
                    if (!file.exists()) continue
                    val label = o.optString("label")
                    label.removePrefix("snap").toIntOrNull()?.let { snapCounter = maxOf(snapCounter, it) }
                    list.add(BasketItem.Snap(o.optString("id"), label, file, o.optString("from")))
                } else {
                    val images = ArrayList<PageImage>()
                    val ia = o.optJSONArray("images")
                    if (ia != null) for (k in 0 until ia.length()) {
                        val im = ia.optJSONObject(k) ?: continue
                        val file = File(dir, im.optString("file"))
                        if (!file.exists()) continue
                        val id = im.optString("id")
                        id.removePrefix("img").toIntOrNull()?.let { imageCounter = maxOf(imageCounter, it) }
                        images.add(PageImage(id, file, im.optString("cap")))
                    }
                    val blocks = ArrayList<Block>()
                    val ba = o.optJSONArray("blocks")
                    if (ba != null) for (k in 0 until ba.length()) {
                        val b = ba.optJSONObject(k) ?: continue
                        val rows = ArrayList<List<String>>()
                        val ra = b.optJSONArray("rows")
                        if (ra != null) for (r in 0 until ra.length()) {
                            val row = ra.optJSONArray(r) ?: continue
                            rows.add((0 until row.length()).map { row.optString(it) })
                        }
                        blocks.add(Block(b.optString("k"), b.optString("t"), b.optInt("l"), rows, b.optString("img"), b.optString("x")))
                    }
                    list.add(
                        BasketItem.Page(
                            o.optString("id"), o.optString("title"), o.optString("url"), blocks, images, o.optInt("words"),
                        )
                    )
                }
            }
            _items.value = list
        } catch (e: Exception) {
            Log.w(TAG, "couldn't read the saved basket", e)
        }
    }

    val words get() = _items.value.filterIsInstance<BasketItem.Page>().sumOf { it.words }
    val snaps get() = _items.value.count { it is BasketItem.Snap }

    @Synchronized
    fun addSnap(bmp: Bitmap, from: String): BasketItem.Snap {
        snapCounter++
        val label = "snap$snapCounter"
        val f = File(dir, "$label.jpg")
        // Big enough for the AI to read small print on the page.
        Images.save(bmp, f, 2200)
        val item = BasketItem.Snap("s$snapCounter-${System.nanoTime()}", label, f, from)
        setItems(_items.value + item)
        return item
    }

    @Synchronized
    fun nextImage(bytes: ByteArray): Pair<String, File>? {
        val bmp = Images.decode(bytes) ?: return null
        if (bmp.width < 140 || bmp.height < 100) return null
        imageCounter++
        val id = "img$imageCounter"
        val f = File(dir, "$id.jpg")
        Images.save(bmp, f)
        return id to f
    }

    fun addPage(page: BasketItem.Page) {
        setItems(_items.value + page)
    }

    /** Pasted or shared text, split into paragraphs. */
    fun addText(text: String, title: String) {
        val blocks = text.split(Regex("\\n\\s*\\n|\\r\\n\\s*\\r\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { Block("p", it.replace(Regex("\\s+"), " ")) }
        if (blocks.isEmpty()) return
        val words = blocks.sumOf { it.text.split(' ').size }
        addPage(BasketItem.Page("t${System.nanoTime()}", title.ifBlank { "Pasted text" }, "", blocks, emptyList(), words))
    }

    /**
     * Turns the browser's scan of one or more frames into a page. Pictures are saved here:
     * ones the page could hand over directly, and the rest downloaded with your sign-in.
     */
    fun pageFromScan(frames: List<JSONObject>, referer: String, userAgent: String): BasketItem.Page? {
        val blocks = ArrayList<Block>()
        val images = ArrayList<PageImage>()
        var title = ""
        var url = ""
        var words = 0
        // The frame with the most text first: that's the book.
        val useful = frames.filter { it.optInt("words") >= 25 || (it.optJSONArray("images")?.length() ?: 0) > 0 }
            .sortedByDescending { it.optInt("words") }
        for (fr in useful) {
            if (title.isBlank()) title = fr.optString("title")
            if (url.isBlank() || fr.optBoolean("top")) url = fr.optString("url")
            words += fr.optInt("words")
            // Pictures of this frame: its id → our id.
            val ids = HashMap<String, String>()
            val caps = HashMap<String, String>()
            val arr = fr.optJSONArray("images")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val im = arr.optJSONObject(i) ?: continue
                    val data = im.optString("data")
                    val src = im.optString("src")
                    val bytes = when {
                        data.isNotBlank() -> Images.fromDataUrl(data)
                        src.startsWith("data:") -> Images.fromDataUrl(src)
                        src.isNotBlank() -> Images.download(src, referer, userAgent)
                        else -> null
                    } ?: continue
                    val saved = nextImage(bytes) ?: continue
                    ids[im.optString("id")] = saved.first
                    caps[saved.first] = im.optString("cap")
                    images.add(PageImage(saved.first, saved.second, im.optString("cap")))
                }
            }
            val bs = fr.optJSONArray("blocks") ?: continue
            for (i in 0 until bs.length()) {
                val b = bs.optJSONObject(i) ?: continue
                when (val k = b.optString("k")) {
                    "img" -> {
                        val our = ids[b.optString("id")] ?: continue
                        blocks.add(Block("img", caps[our].orEmpty(), imageId = our))
                    }
                    "table" -> {
                        val rows = ArrayList<List<String>>()
                        val ra = b.optJSONArray("rows")
                        if (ra != null) for (r in 0 until ra.length()) {
                            val row = ra.optJSONArray(r) ?: continue
                            rows.add((0 until row.length()).map { row.optString(it) })
                        }
                        if (rows.isNotEmpty()) blocks.add(Block("table", b.optString("t"), rows = rows))
                    }
                    else -> {
                        val t = b.optString("t")
                        if (t.isNotBlank()) blocks.add(Block(k, t, level = b.optInt("l")))
                    }
                }
            }
        }
        // The book's own definitions for linked terms (some read from pop-ups), once per term.
        val seenTerms = HashSet<String>()
        for (fr in frames) {
            val g = fr.optJSONArray("gloss") ?: continue
            for (i in 0 until g.length()) {
                val e = g.optJSONObject(i) ?: continue
                val term = e.optString("t").trim()
                val def = e.optString("d").trim()
                if (term.isEmpty() || def.isEmpty() || !seenTerms.add(term.lowercase())) continue
                blocks.add(Block("gloss", term, extra = def))
            }
        }
        if (blocks.none { it.kind != "img" } && images.isEmpty()) return null
        return BasketItem.Page("p${System.nanoTime()}", title.ifBlank { "Web page" }, url, blocks, images, words)
    }

    /** Page text the way the AI reads it. */
    fun render(page: BasketItem.Page): String {
        val sb = StringBuilder()
        sb.append("=== Page: ").append(page.title)
        if (page.url.isNotBlank()) sb.append(" (").append(page.url).append(')')
        sb.append(" ===\n")
        for (b in page.blocks) {
            when (b.kind) {
                "gloss" -> continue
                "h" -> sb.append('\n').append("#".repeat(b.level.coerceIn(1, 6))).append(' ').append(b.text).append('\n')
                "li" -> sb.append("- ").append(b.text).append('\n')
                "img" -> sb.append("[FIGURE ").append(b.imageId).append(": ").append(b.text.ifBlank { "no caption" }).append("]\n")
                "table" -> {
                    sb.append("\nTABLE").append(if (b.text.isNotBlank()) ": ${b.text}" else "").append('\n')
                    for (r in b.rows) sb.append("| ").append(r.joinToString(" | ")).append(" |\n")
                    sb.append('\n')
                }
                else -> sb.append(b.text).append('\n')
            }
        }
        val gloss = page.blocks.filter { it.kind == "gloss" }
        if (gloss.isNotEmpty()) {
            sb.append("\nGLOSSARY (the book's own pop-up definitions of linked terms on this page):\n")
            for (g in gloss) sb.append("- ").append(g.text).append(": ").append(g.extra).append('\n')
        }
        return sb.toString()
    }
}
