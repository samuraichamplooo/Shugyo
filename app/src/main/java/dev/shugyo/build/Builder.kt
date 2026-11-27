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
        /** Add the new pages to this study set instead of making a new one. */
        intoDeckId: String? = null,
    ): Deck = withContext(Dispatchers.IO) {
        val pages = items.filterIsInstance<BasketItem.Page>()
        val snaps = items.filterIsInstance<BasketItem.Snap>()
        val allImages = pages.flatMap { it.images }.associateBy { it.id }
        val parts = plan(pages, snaps, allImages)
        if (parts.isEmpty()) throw AiException("There's nothing to build from yet. Scan a page first.")

        // Two steps per part (notes, then questions). Each finished step is saved, so if the build
        // stops (no internet, busy servers) the next try picks up where it left off.
        val steps = parts.size * 2
        var stepsDone = 0
        val used = ArrayList<File>()
        val results = ArrayList<JSONObject>()
        val perObjective = Prefs.current.questionsPerObjective
        for ((i, part) in parts.withIndex()) {
            if (cancelled()) throw CancellationException("cancelled")
            val base = i.toFloat() / parts.size
            val share = 1f / parts.size
            val partName = if (parts.size > 1) "part ${i + 1} of ${parts.size}" else "your pages"

            // Step 1: notes, from the text and pictures.
            onUpdate("Reading $partName…", base + share * 0.02f)
            val images = part.images.mapNotNull { img -> Images.forAi(img.file)?.let { AiImage(img.id, it) } } +
                part.snaps.mapNotNull { s -> Images.forAi(s.file, 2000)?.let { AiImage(s.label, it) } }
            val notesSystem = Prompts.notes()
            val notesUser = Prompts.buildUser(part.text, part.snaps.map { it.label }, i + 1, parts.size, titleHint, subjectHint)
            val notes = try {
                step(
                    "n", notesSystem, notesUser, images, 24_000, cancelled, used,
                    onStatus = { onUpdate(it, base + share * 0.05f) },
                    onChars = { chars ->
                        val f = min(0.97f, chars / 16_000f)
                        onUpdate(writingLine(chars, partName, false), base + share * (0.05f + 0.5f * f))
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw stopped(e, stepsDone, steps)
            }
            stepsDone++
            if (notes == null) {
                // Garbled twice: skip this part rather than getting stuck on it.
                stepsDone++
                continue
            }

            // Step 2: questions, from the notes and the text (no pictures needed, so it's quicker).
            onUpdate("Writing practice questions for $partName…", base + share * 0.57f)
            val questions = try {
                step(
                    "q", Prompts.questions(perObjective),
                    Prompts.questionsUser(compactNotes(notes), part.text, i + 1, parts.size),
                    emptyList(), 16_000, cancelled, used,
                    onStatus = { onUpdate(it, base + share * 0.6f) },
                    onChars = { chars ->
                        val f = min(0.97f, chars / 12_000f)
                        onUpdate(writingLine(chars, partName, true), base + share * (0.6f + 0.38f * f))
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw stopped(e, stepsDone, steps)
            }
            stepsDone++
            notes.put("questions", questions?.optJSONArray("questions") ?: org.json.JSONArray())
            results.add(notes)
        }
        if (results.isEmpty()) throw AiException("The AI's answers couldn't be read. Try building again.")
        onUpdate("Saving your study set…", 0.98f)
        val existing = intoDeckId?.let { Store.deck(it) }
        val id = existing?.id ?: Store.newId()
        // New ids get their own prefix when added to an existing set, so nothing clashes with what's there.
        val prefix = if (existing != null) "x" + System.currentTimeMillis().toString(36).takeLast(5) + "_" else ""
        val fresh = merge(id, results, pages, snaps, allImages, titleHint, subjectHint, prefix)
        val deck = if (existing != null) append(existing, fresh) else fresh
        Store.save(deck)
        used.forEach { it.delete() }
        onUpdate("Done!", 1f)
        deck
    }

    /** Saved answers of finished steps, kept until the set is built. */
    private val cacheDir: File by lazy { File(dev.shugyo.App.instance.filesDir, "aicache").apply { mkdirs() } }

    private fun cacheKey(kind: String, system: String, user: String, images: List<AiImage>): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        md.update(kind.toByteArray())
        md.update(system.toByteArray())
        md.update(user.toByteArray())
        images.forEach { md.update(it.label.toByteArray()); md.update(it.jpeg.size.toString().toByteArray()) }
        return md.digest().take(16).joinToString("") { "%02x".format(it) }
    }

    /**
     * One AI request, reusing a saved answer when this exact step already finished before.
     * Null if the answer came out unreadable twice.
     */
    private suspend fun step(
        kind: String,
        system: String,
        user: String,
        images: List<AiImage>,
        maxTokens: Int,
        cancelled: () -> Boolean,
        used: MutableList<File>,
        onStatus: (String) -> Unit,
        onChars: (Int) -> Unit,
    ): JSONObject? {
        val file = File(cacheDir, "${cacheKey(kind, system, user, images)}.json")
        used.add(file)
        if (file.exists()) {
            JsonFix.parse(file.readText())?.let {
                onStatus("Already done earlier, reusing it…")
                return it
            }
        }
        for (attempt in 1..2) {
            val raw = Ai.ask(
                system, user, images, maxTokens = maxTokens, json = true, quick = false,
                cancelled = cancelled, onStatus = onStatus, onProgress = onChars,
            )
            val obj = JsonFix.parse(raw)
            if (obj != null) {
                try {
                    file.writeText(obj.toString())
                } catch (e: Exception) {
                    Log.w(TAG, "couldn't save step", e)
                }
                return obj
            }
            Log.w(TAG, "unreadable JSON (${raw.length} chars): ${raw.take(300)}")
            onStatus("That answer came out garbled. Asking again…")
        }
        return null
    }

    /** The error to show when a build stops part-way, saying that finished steps are kept. */
    private fun stopped(e: Exception, done: Int, total: Int): AiException {
        val why = e.message ?: "The AI didn't answer."
        return AiException(
            if (done > 0) "$why\n\n$done of $total steps are saved. Tap Build again to continue from there."
            else why
        )
    }

    /** The notes, shortened to what the question writer needs. */
    private fun compactNotes(notes: JSONObject): String {
        val o = JSONObject()
        o.put("objectives", org.json.JSONArray(objects(notes.optJSONArray("objectives")).map {
            JSONObject().put("id", it.str("id")).put("text", it.str("text")).put("explain", it.str("explain"))
        }))
        o.put("terms", org.json.JSONArray(objects(notes.optJSONArray("terms")).map {
            JSONObject().put("id", it.str("id")).put("term", it.str("term")).put("definition", it.str("definition"))
                .put("objective", it.str("objective"))
        }))
        o.put("tables", org.json.JSONArray(objects(notes.optJSONArray("tables")).map {
            JSONObject().put("title", it.str("title")).put("headers", it.optJSONArray("headers")).put("rows", it.optJSONArray("rows"))
        }))
        o.put("figures", org.json.JSONArray(objects(notes.optJSONArray("figures")).map {
            JSONObject().put("caption", it.str("caption")).put("explain", it.str("explain"))
        }))
        return o.toString()
    }

    private fun writingLine(chars: Int, partName: String, questions: Boolean): String {
        val lines = if (questions) listOf(
            "Writing practice questions",
            "Making believable wrong answers",
            "Adding hints",
            "Writing scenario questions",
            "Sharpening the tricky answers",
        ) else listOf(
            "Finding the learning objectives",
            "Pulling out key terms",
            "Making hard ideas simple",
            "Inventing memory tricks",
            "Copying the tables",
            "Studying the diagrams",
        )
        val step = (chars / 3000).coerceIn(0, lines.size - 1)
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
        idPrefix: String = "",
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
            val prefix = idPrefix + if (results.size > 1) "p${pi + 1}_" else ""
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
                // Each figure gets its own file, even if the AI reused an id.
                val fid = id(f.str("id"), "f", i + 1).let { base ->
                    if (figures.none { it.id == base }) base else "${base}_${i + 1}"
                }
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
                objectives.add(Objective("${idPrefix}o${i + 1}", "Explain: ${s.title}", s.summary, s.points.joinToString(" "), emptyList()))
            }
        }
        val termObjective = fixedTerms.associate { it.id to it.objective }
        val fixedQuestions = questions.map { q ->
            if (q.objective.isNotBlank()) q else q.copy(objective = termObjective[q.term].orEmpty())
        }

        val firstPage = pages.firstOrNull()
        // Lists on screen are keyed by id, so every id has to be different.
        return Deck(
            id = deckId,
            title = titleHint.ifBlank { title.ifBlank { firstPage?.title ?: "New study set" } },
            subject = subjectHint.ifBlank { subject },
            source = firstPage?.url?.takeIf { it.isNotBlank() } ?: if (snaps.isNotEmpty()) "Pictures of pages" else "Pasted text",
            created = System.currentTimeMillis(),
            overview = overview.joinToString(" "),
            sections = sections,
            objectives = unique(objectives, { it.id }) { o, id -> o.copy(id = id) },
            terms = unique(fixedTerms, { it.id }) { t, id -> t.copy(id = id) },
            tables = unique(tables, { it.id }) { t, id -> t.copy(id = id) },
            figures = figures,
            questions = unique(fixedQuestions, { it.id }) { q, id -> q.copy(id = id) },
        )
    }

    /** A new part of a chapter added to a set you already have. Terms it already has aren't repeated. */
    private fun append(old: Deck, add: Deck): Deck {
        fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        val byName = old.terms.associateBy { norm(it.term) }
        val same = HashMap<String, String>()
        val newTerms = add.terms.filter { t ->
            val match = byName[norm(t.term)]
            if (match != null) same[t.id] = match.id
            match == null
        }
        fun tid(x: String) = same[x] ?: x
        return old.copy(
            overview = listOf(old.overview, add.overview).filter { it.isNotBlank() }.joinToString(" "),
            sections = old.sections + add.sections,
            objectives = old.objectives + add.objectives.map { o -> o.copy(terms = o.terms.map { tid(it) }) },
            terms = old.terms + newTerms,
            tables = old.tables + add.tables,
            figures = old.figures + add.figures,
            questions = old.questions + add.questions.map { q -> q.copy(term = tid(q.term)) },
        )
    }

    private fun <T> unique(list: List<T>, idOf: (T) -> String, withId: (T, String) -> T): List<T> {
        val seen = HashSet<String>()
        return list.map { x ->
            val id = idOf(x)
            if (seen.add(id)) x else {
                var n = 2
                while (!seen.add("${id}_$n")) n++
                withId(x, "${id}_$n")
            }
        }
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
