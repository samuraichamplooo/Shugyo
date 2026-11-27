package dev.shugyo.scan

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.io.File

/** One piece of a scanned page. */
data class Block(
    val kind: String,          // h, p, li, table, img
    val text: String,
    val level: Int = 0,        // heading level
    val rows: List<List<String>> = emptyList(),
    val imageId: String = "",  // for pictures: img1, img2…
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
    }

    /** A picture of a page: a screen snap, a photo, a screenshot or a PDF page. */
    data class Snap(override val id: String, val label: String, val file: File, val from: String) : BasketItem()
}

/**
 * Everything scanned so far for the next study set. You can scan several pages of a chapter (and snap
 * screens or add photos) before building.
 */
object Basket {
    private lateinit var dir: File
    private val _items = MutableStateFlow<List<BasketItem>>(emptyList())
    val items: StateFlow<List<BasketItem>> = _items
    private var imageCounter = 0
    private var snapCounter = 0

    fun init(context: Context) {
        dir = File(context.cacheDir, "basket").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    fun clear() {
        _items.value = emptyList()
        imageCounter = 0
        snapCounter = 0
        dir.listFiles()?.forEach { it.deleteRecursively() }
    }

    fun remove(id: String) {
        _items.value = _items.value.filter { it.id != id }
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
        _items.value = _items.value + item
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
        _items.value = _items.value + page
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
        return sb.toString()
    }
}
