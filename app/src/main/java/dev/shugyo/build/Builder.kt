package dev.shugyo.build

import android.graphics.Bitmap
import android.util.Log
import dev.shugyo.ai.Ai
import dev.shugyo.ai.AiException
import dev.shugyo.ai.AiImage
import dev.shugyo.data.Deck
import dev.shugyo.data.Figure
import dev.shugyo.data.Objective
import dev.shugyo.data.Prefs
import dev.shugyo.data.QType
import dev.shugyo.data.Question
import dev.shugyo.data.Section
import dev.shugyo.data.Store
import dev.shugyo.data.TableBlock
import dev.shugyo.data.Term
import dev.shugyo.data.arrays
import dev.shugyo.data.ints
import dev.shugyo.data.objects
import dev.shugyo.data.str
import dev.shugyo.data.strList
import dev.shugyo.data.stringsOf
import dev.shugyo.scan.BasketItem
import dev.shugyo.scan.Basket
import dev.shugyo.scan.Images
import dev.shugyo.scan.PageImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.math.min

/**
 * Turns what you scanned into a study set: the AI reads the pages (in parts for long chapters),
 * and the parts are joined into one set with its diagrams saved alongside.
 */
object Builder {
    private const val TAG = "Builder"
    private const val CHUNK_CHARS = 38_000
    private const val SNAPS_PER_CALL = 8
    private const val IMAGES_PER_CALL = 12

    private class Part(val text: String, val images: List<PageImage>, val snaps: List<BasketItem.Snap>)

    suspend fun build(
        items: List<BasketItem>,
        titleHint: String,
        subjectHint: String,
        onUpdate: (status: String, fraction: Float) -> Unit,
        cancelled: () -> Boolean,
    ): Deck = withContext(Dispatchers.IO) {
        val pages = items.filterIsInstance<BasketItem.Page>()
        val snaps = items.filterIsInstance<BasketItem.Snap>()
        val allImages = pages.flatMap { it.images }.associateBy { it.id }
        val parts = plan(pages, snaps, allImages)
        if (parts.isEmpty()) throw AiException("There's nothing to build from yet. Scan a page first.")

        val results = ArrayList<JSONObject>()
        var lastError: Exception? = null
        for ((i, part) in parts.withIndex()) {
            if (cancelled()) throw CancellationException("cancelled")
            val base = i.toFloat() / parts.size
            val share = 1f / parts.size
            val partName = if (parts.size > 1) "part ${i + 1} of ${parts.size}" else "your pages"
            onUpdate("Reading $partName…", base + share * 0.02f)
            val images = part.images.mapNotNull { img -> Images.forAi(img.file)?.let { AiImage(img.id, it) } } +
                part.snaps.mapNotNull { s -> Images.forAi(s.file, 2000)?.let { AiImage(s.label, it) } }
            val user = Prompts.buildUser(
                part.text, part.snaps.map { it.label }, i + 1, parts.size, titleHint, subjectHint,
            )
            val system = Prompts.build(Prefs.current.questionsPerObjective)
            var obj: JSONObject? = null
            for (attempt in 1..2) {
                try {
                    val raw = Ai.ask(
                        system, user, images, maxTokens = 32_000, json = true, quick = false,
                        cancelled = cancelled,
                        onStatus = { onUpdate(it, base + share * 0.05f) },
                        onProgress = { chars ->
                            val f = min(0.97f, chars / 26_000f)
                            onUpdate(writingLine(chars, partName), base + share * (0.05f + 0.9f * f))
                        },
                    )
                    obj = JsonFix.parse(raw)
                    if (obj != null) break
                    Log.w(TAG, "unreadable JSON (${raw.length} chars): ${raw.take(300)}")
                    onUpdate("That answer came out garbled. Asking again…", base + share * 0.05f)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "part ${i + 1} failed", e)
                    break
                }
            }
            if (obj != null) results.add(obj)
        }
        if (results.isEmpty()) {
            throw AiException(lastError?.message ?: "The AI's answer couldn't be read. Try building again.")
        }
        onUpdate("Saving your study set…", 0.98f)
        val id = Store.newId()
        val deck = merge(id, results, pages, snaps, allImages, titleHint, subjectHint)
        Store.save(deck)
        onUpdate("Done!", 1f)
        deck
    }

    private fun writingLine(chars: Int, partName: String): String {
        val lines = listOf(
            "Finding the learning objectives",
            "Pulling out key terms",
            "Making hard ideas simple",
            "Inventing memory tricks",
            "Copying the tables",
            "Studying the diagrams",
            "Writing practice questions",
            "Sharpening the tricky answers",
        )
        val step = (chars / 3500).coerceIn(0, lines.size - 1)
        val k = chars / 1000
        return "${lines[step]}… ($partName · ${k}k characters)"
    }

    private fun plan(
        pages: List<BasketItem.Page>,
        snaps: List<BasketItem.Snap>,
        allImages: Map<String, PageImage>,
    ): List<Part> {
        val text = pages.joinToString("\n\n") { Basket.render(it) }
        val chunks = if (text.isBlank()) emptyList() else chunk(text)
        val figRef = Regex("\\[FIGURE (img\\d+)")
        val textParts = chunks.map { c ->
            val imgs = figRef.findAll(c).mapNotNull { allImages[it.groupValues[1]] }.distinct().take(IMAGES_PER_CALL).toList()
            Part(c, imgs, emptyList())
        }
        val snapGroups = snaps.chunked(SNAPS_PER_CALL)
        if (textParts.size == 1 && snapGroups.size == 1 && textParts[0].text.length < 20_000) {
            return listOf(Part(textParts[0].text, textParts[0].images, snapGroups[0]))
        }
        return textParts + snapGroups.map { Part("", emptyList(), it) }
    }

    /** Splits long text into parts, preferring to break before a heading or a new page. */
    private fun chunk(text: String): List<String> {
        if (text.length <= CHUNK_CHARS) return listOf(text)
        val out = ArrayList<String>()
        val cur = StringBuilder()
        for (line in text.split('\n')) {
            val heading = line.startsWith("#") || line.startsWith("=== Page")
            val tooBig = cur.length + line.length > CHUNK_CHARS
            val niceBreak = heading && cur.length > CHUNK_CHARS * 0.7
            if (cur.isNotEmpty() && (tooBig || niceBreak)) {
                out.add(cur.toString())
                cur.setLength(0)
            }
            cur.append(line).append('\n')
        }
        if (cur.isNotBlank()) out.add(cur.toString())
        return out
    }

    // ---------------------------------------------------------------- joining the parts

    private fun merge(
        deckId: String,
        results: List<JSONObject>,
        pages: List<BasketItem.Page>,
        snaps: List<BasketItem.Snap>,
        allImages: Map<String, PageImage>,
        titleHint: String,
        subjectHint: String,
    ): Deck {
        var title = ""
        var subject = ""
        val overview = ArrayList<String>()
        val sections = ArrayList<Section>()
        val objectives = ArrayList<Objective>()
        val terms = ArrayList<Term>()
        val tables = ArrayList<TableBlock>()
        val figures = ArrayList<Figure>()
        val questions = ArrayList<Question>()
        val termByName = HashMap<String, String>()
        val snapByLabel = snaps.associateBy { it.label }
        val snapBitmaps = HashMap<String, Bitmap?>()
        val dir = Store.dir(deckId)

        for ((pi, o) in results.withIndex()) {
            val prefix = if (results.size > 1) "p${pi + 1}_" else ""
            val remap = HashMap<String, String>()
            fun id(raw: String, kind: String, n: Int): String {
                val r = raw.ifBlank { "$kind$n" }
                return remap.getOrPut(r) { prefix + r }
            }
            fun ref(raw: String) = if (raw.isBlank()) "" else remap[raw] ?: (prefix + raw)

            if (title.isBlank()) title = o.str("title")
            if (subject.isBlank()) subject = o.str("subject")
            o.str("overview").takeIf { it.isNotBlank() }?.let { overview.add(it) }

            // Ids first, so links resolve whatever order things come in.
            val objs = objects(o.optJSONArray("objectives"))
            objs.forEachIndexed { i, it -> id(it.str("id"), "o", i + 1) }
            val ts = objects(o.optJSONArray("terms"))
            ts.forEachIndexed { i, t ->
                val name = t.str("term").lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
                val raw = t.str("id").ifBlank { "t${i + 1}" }
                val existing = termByName[name]
                if (existing != null) remap[raw] = existing else id(raw, "t", i + 1)
            }

            for (s in objects(o.optJSONArray("sections"))) {
                val t = s.str("title")
                if (t.isNotBlank() || s.str("summary").isNotBlank()) sections.add(Section(t, s.str("summary"), s.strList("points")))
            }
            objs.forEachIndexed { i, it ->
                val text = it.str("text")
                if (text.isBlank()) return@forEachIndexed
                objectives.add(
                    Objective(
                        id(it.str("id"), "o", i + 1), text, it.str("simple"), it.str("explain"),
                        it.strList("terms").map { t -> ref(t) },
                    )
                )
            }
            ts.forEachIndexed { i, t ->
                val name = t.str("term")
                val key = name.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
                if (name.isBlank() || termByName.containsKey(key)) return@forEachIndexed
                val tid = id(t.str("id"), "t", i + 1)
                termByName[key] = tid
                terms.add(
                    Term(
                        tid, name, t.str("definition"), t.str("simple"), t.str("mnemonic"), t.str("example"),
                        ref(t.str("objective")),
                    )
                )
            }
            objects(o.optJSONArray("tables")).forEachIndexed { i, t ->
                val rows = arrays(t.optJSONArray("rows")).map { stringsOf(it) }.filter { it.isNotEmpty() }
                if (rows.isEmpty()) return@forEachIndexed
                tables.add(
                    TableBlock(
                        id(t.str("id"), "tb", i + 1), t.str("title"), t.strList("headers"), rows,
                        t.str("takeaway"), ref(t.str("objective")),
                    )
                )
            }
            objects(o.optJSONArray("figures")).forEachIndexed { i, f ->
                val fid = id(f.str("id"), "f", i + 1)
                val src = f.str("src").lowercase().trim()
                val file = File(dir, "fig_$fid.jpg")
                val ok = when {
                    src.startsWith("img") -> allImages[src]?.let { img ->
                        Images.decodeFile(img.file)?.let { Images.save(it, file); true }
                    } ?: false
                    src.startsWith("snap") -> snapByLabel[src]?.let { snap ->
                        val page = snapBitmaps.getOrPut(src) { Images.decodeFile(snap.file, 2200) }
                        if (page == null) false else {
                            val crop = Images.crop(page, ints(f.optJSONArray("box"))) ?: page
                            Images.save(crop, file)
                            true
                        }
                    } ?: false
                    else -> false
                }
                if (!ok) return@forEachIndexed
                figures.add(
                    Figure(
                        fid, file.name, f.str("caption").ifBlank { allImages[src]?.caption.orEmpty() },
                        f.str("explain"), f.strList("labels"), ref(f.str("objective")),
                    )
                )
            }
            objects(o.optJSONArray("questions")).forEachIndexed { i, q ->
                clean(q, id("q${i + 1}", "q", i + 1), ::ref)?.let { questions.add(it) }
            }
        }

        // Fill in links the AI left out.
        val fixedTerms = terms.map { t ->
            if (t.objective.isNotBlank() && objectives.any { it.id == t.objective }) t
            else t.copy(objective = objectives.firstOrNull { t.id in it.terms }?.id.orEmpty())
        }
        if (objectives.isEmpty()) {
            sections.forEachIndexed { i, s ->
                objectives.add(Objective("o${i + 1}", "Explain: ${s.title}", s.summary, s.points.joinToString(" "), emptyList()))
            }
        }
        val termObjective = fixedTerms.associate { it.id to it.objective }
        val fixedQuestions = questions.map { q ->
            if (q.objective.isNotBlank()) q else q.copy(objective = termObjective[q.term].orEmpty())
        }

        val firstPage = pages.firstOrNull()
        return Deck(
            id = deckId,
            title = titleHint.ifBlank { title.ifBlank { firstPage?.title ?: "New study set" } },
            subject = subjectHint.ifBlank { subject },
            source = firstPage?.url?.takeIf { it.isNotBlank() } ?: if (snaps.isNotEmpty()) "Pictures of pages" else "Pasted text",
            created = System.currentTimeMillis(),
            overview = overview.joinToString(" "),
            sections = sections,
            objectives = objectives,
            terms = fixedTerms,
            tables = tables,
            figures = figures,
            questions = fixedQuestions,
        )
    }

    /** A question as the AI wrote it, checked and tidied. Null if it can't be used. */
    private fun clean(q: JSONObject, id: String, ref: (String) -> String): Question? {
        val prompt = q.str("prompt")
        var type = q.str("type").lowercase()
        var choices = q.strList("choices")
        var answer = ints(q.optJSONArray("answer"))
        if (answer.isEmpty() && q.opt("answer") is Number) answer = listOf(q.optInt("answer"))
        if (type == QType.TF || (choices.size == 2 && choices.map { it.lowercase() }.toSet() == setOf("true", "false"))) {
            type = QType.TF
            // Always True first, False second.
            if (choices.size == 2 && choices[0].equals("false", true)) answer = answer.map { 1 - it }
            choices = listOf("True", "False")
        }
        answer = answer.filter { it in choices.indices }.distinct().sorted()
        if (prompt.isBlank() || choices.size < 2 || answer.isEmpty()) return null
        type = when {
            type == QType.TF -> QType.TF
            answer.size > 1 -> QType.SATA
            type == QType.SATA -> QType.SATA
            else -> QType.MC
        }
        return Question(
            id = id, type = type, prompt = prompt, choices = choices, answer = answer,
            hint = q.str("hint"), explain = q.str("explain"),
            objective = ref(q.str("objective")), term = ref(q.str("term")),
            level = q.optInt("level", 1).coerceIn(1, 3),
        )
    }
}
