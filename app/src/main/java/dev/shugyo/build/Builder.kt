package dev.shugyo.build

import android.graphics.Bitmap
import android.util.Log
import dev.shugyo.ai.Ai
import dev.shugyo.ai.AiException
import dev.shugyo.ai.AiImage
import dev.shugyo.data.Beat
import dev.shugyo.data.Deck
import dev.shugyo.data.FactCard
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
    /** Objectives per lessons or questions request: small enough to answer quickly and completely. */
    private const val BATCH = 4

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

        // Per part: notes, then lessons and fact cards, then questions, a few objectives at a time.
        // Each finished step is saved, so if the build stops (no internet, busy servers) the next try
        // picks up where it left off.
        var steps = parts.size * 3
        var stepsDone = 0
        val used = ArrayList<File>()
        val results = ArrayList<JSONObject>()
        val perObjective = Prefs.current.questionsPerObjective
        for ((i, part) in parts.withIndex()) {
            if (cancelled()) throw CancellationException("cancelled")
            val base = i.toFloat() / parts.size
            val share = 1f / parts.size
            val partName = if (parts.size > 1) "part ${i + 1} of ${parts.size}" else "your pages"

            // Notes, from the text and pictures.
            onUpdate("Reading $partName…", base + share * 0.02f)
            val images = part.images.mapNotNull { img -> Images.forAi(img.file)?.let { AiImage(img.id, it) } } +
                part.snaps.mapNotNull { s -> Images.forAi(s.file, 2000)?.let { AiImage(s.label, it) } }
            val notesUser = Prompts.buildUser(part.text, part.snaps.map { it.label }, i + 1, parts.size, titleHint, subjectHint)
            val notes = try {
                step(
                    "n", Prompts.notes(), notesUser, images, 32_000, cancelled, used,
                    onStatus = { onUpdate(it, base + share * 0.05f) },
                    onChars = { chars ->
                        val f = min(0.97f, chars / 20_000f)
                        onUpdate(writingLine(chars, partName, 0), base + share * (0.05f + 0.3f * f))
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
                stepsDone += 2
                continue
            }
            val compact = compactNotes(notes)
            val objIds = objects(notes.optJSONArray("objectives")).map { it.str("id") }.filter { it.isNotBlank() }
            val batches = objIds.chunked(BATCH).ifEmpty { listOf(emptyList()) }
            steps += 2 * batches.size - 2

            // Lessons and fact cards.
            val lessons = org.json.JSONArray()
            for ((b, batch) in batches.withIndex()) {
                if (batch.isEmpty()) break
                val from = base + share * (0.36f + 0.24f * b / batches.size)
                val span = share * 0.24f / batches.size
                onUpdate("Writing lessons for $partName (${b + 1} of ${batches.size})…", from)
                val got = try {
                    step(
                        "l", Prompts.lessons(), Prompts.lessonsUser(compact, part.text, i + 1, parts.size, batch),
                        emptyList(), 24_000, cancelled, used,
                        onStatus = { onUpdate(it, from) },
                        onChars = { chars ->
                            onUpdate(writingLine(chars, partName, 1), from + span * min(0.97f, chars / 14_000f))
                        },
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw stopped(e, stepsDone, steps)
                }
                stepsDone++
                objects(got?.optJSONArray("lessons")).forEach { lessons.put(it) }
            }

            // Coverage check: anything in the pages the lessons missed goes into the right objective.
            if (lessons.length() > 0) {
                val from = base + share * 0.6f
                onUpdate("Checking nothing in $partName was missed…", from)
                val snapImages = if (part.text.isBlank()) images else emptyList()
                val got = try {
                    step(
                        "g", Prompts.gaps(),
                        Prompts.gapsUser(lessonsText(lessons), part.text, i + 1, parts.size, part.snaps.map { it.label }),
                        snapImages, 20_000, cancelled, used,
                        onStatus = { onUpdate(it, from) },
                        onChars = { chars ->
                            onUpdate("Filling in what was missed… (${chars / 1000}k characters)", from + share * 0.08f * min(0.97f, chars / 8_000f))
                        },
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw stopped(e, stepsDone, steps)
                }
                addGaps(lessons, objects(got?.optJSONArray("additions")))
            }

            // Questions.
            val questions = org.json.JSONArray()
            for ((b, batch) in batches.withIndex()) {
                val from = base + share * (0.68f + 0.31f * b / batches.size)
                val span = share * 0.31f / batches.size
                onUpdate("Writing practice questions for $partName (${b + 1} of ${batches.size})…", from)
                val got = try {
                    step(
                        "q", Prompts.questions(perObjective),
                        Prompts.questionsUser(compact, part.text, i + 1, parts.size, batch),
                        emptyList(), 20_000, cancelled, used,
                        onStatus = { onUpdate(it, from) },
                        onChars = { chars ->
                            onUpdate(writingLine(chars, partName, 2), from + span * min(0.97f, chars / 12_000f))
                        },
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw stopped(e, stepsDone, steps)
                }
                stepsDone++
                objects(got?.optJSONArray("questions")).forEach { questions.put(it) }
            }
            notes.put("lessons", lessons)
            notes.put("questions", questions)
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
        // Keep the reading itself, so the set can be improved later without scanning again.
        try {
            val text = parts.joinToString("\n\n") { it.text }.trim()
            if (text.isNotEmpty()) java.io.FileOutputStream(File(Store.dir(id), "source.txt"), existing != null).use {
                it.write(("\n\n" + text).toByteArray())
            }
        } catch (e: Exception) {
            Log.w(TAG, "couldn't keep the source text", e)
        }
        used.forEach { it.delete() }
        onUpdate("Done!", 1f)
        deck
    }

    /**
     * Fills out a set built earlier: a step-by-step lesson and fact flashcards for every objective that
     * doesn't have them yet, and more practice questions where there are few. Uses the saved reading when
     * there is one, otherwise the set's own notes.
     */
    suspend fun upgrade(
        deckId: String,
        onUpdate: (status: String, fraction: Float) -> Unit,
        cancelled: () -> Boolean,
    ): Deck = withContext(Dispatchers.IO) {
        val deck = Store.deck(deckId) ?: throw AiException("That study set is gone.")
        val perObjective = Prefs.current.questionsPerObjective
        val srcFile = File(Store.dir(deckId), "source.txt")
        val source = if (srcFile.exists()) srcFile.readText().trim() else ""
        val text = if (source.isNotEmpty() && source.length <= 60_000) source else deckText(deck)
        val compact = compactDeck(deck)
        val needLessons = deck.objectives.filter { it.lesson.isEmpty() }.map { it.id }.chunked(BATCH)
        val needQuestions = deck.objectives.filter { deck.questionsFor(it).size < perObjective }.map { it.id }.chunked(BATCH)
        val total = (needLessons.size + needQuestions.size).coerceAtLeast(1)
        if (needLessons.isEmpty() && needQuestions.isEmpty()) return@withContext deck
        var done = 0
        val used = ArrayList<File>()
        val lessons = ArrayList<JSONObject>()
        val newQuestions = ArrayList<JSONObject>()
        try {
            for ((b, batch) in needLessons.withIndex()) {
                val from = done.toFloat() / total
                onUpdate("Writing lessons (${b + 1} of ${needLessons.size})…", from)
                val got = step(
                    "l", Prompts.lessons(), Prompts.lessonsUser(compact, text, 1, 1, batch),
                    emptyList(), 24_000, cancelled, used,
                    onStatus = { onUpdate(it, from) },
                    onChars = { chars ->
                        onUpdate(writingLine(chars, "your set", 1), from + min(0.97f, chars / 14_000f) / total)
                    },
                )
                lessons.addAll(objects(got?.optJSONArray("lessons")))
                done++
            }
            // With the reading kept, check nothing in it was missed.
            if (lessons.isNotEmpty() && source.isNotEmpty()) {
                onUpdate("Checking nothing in the reading was missed…", done.toFloat() / total)
                val arr = org.json.JSONArray()
                lessons.forEach { arr.put(it) }
                val got = step(
                    "g", Prompts.gaps(), Prompts.gapsUser(lessonsText(arr), text, 1, 1, emptyList()),
                    emptyList(), 20_000, cancelled, used,
                    onStatus = { onUpdate(it, done.toFloat() / total) },
                    onChars = {},
                )
                addGaps(arr, objects(got?.optJSONArray("additions")))
                lessons.clear()
                lessons.addAll(objects(arr))
            }
            for ((b, batch) in needQuestions.withIndex()) {
                val from = done.toFloat() / total
                onUpdate("Writing more practice questions (${b + 1} of ${needQuestions.size})…", from)
                val already = deck.questions.filter { it.objective in batch }.map { it.prompt.take(140) }
                val got = step(
                    "q", Prompts.questions(perObjective), Prompts.questionsUser(compact, text, 1, 1, batch, already),
                    emptyList(), 20_000, cancelled, used,
                    onStatus = { onUpdate(it, from) },
                    onChars = { chars ->
                        onUpdate(writingLine(chars, "your set", 2), from + min(0.97f, chars / 12_000f) / total)
                    },
                )
                newQuestions.addAll(objects(got?.optJSONArray("questions")))
                done++
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw stopped(e, done, total)
        }

        onUpdate("Saving your study set…", 0.98f)
        val tag = "u" + System.currentTimeMillis().toString(36).takeLast(5) + "_"
        val validTerm = deck.terms.map { it.id }.toSet()
        val validFigure = deck.figures.map { it.id }.toSet()
        val keep: (String) -> String = { it }
        val byObjective = lessons.associateBy { it.str("objective") }
        val objectives = deck.objectives.map { o ->
            val l = byObjective[o.id] ?: return@map o
            if (o.lesson.isNotEmpty()) return@map o
            val steps = beats(l, keep).map { b ->
                b.copy(terms = b.terms.filter { it in validTerm }, figure = b.figure.takeIf { it in validFigure }.orEmpty())
            }
            o.copy(lesson = steps, recap = l.str("recap"))
        }
        val cards = ArrayList<FactCard>()
        for (l in lessons) {
            val oid = l.str("objective")
            for (c in objects(l.optJSONArray("cards"))) {
                if (c.str("front").isBlank() || c.str("back").isBlank()) continue
                cards.add(FactCard("$tag" + "c${cards.size + 1}", c.str("front"), c.str("back"), oid))
            }
        }
        val questions = newQuestions.mapIndexedNotNull { i, q -> clean(q, "${tag}q${i + 1}", keep) }
        val updated = deck.copy(objectives = objectives, cards = deck.cards + cards, questions = deck.questions + questions)
        Store.save(updated)
        used.forEach { it.delete() }
        onUpdate("Done!", 1f)
        updated
    }

    /** A set's notes as plain text, for improving a set whose reading wasn't kept. */
    private fun deckText(d: Deck): String {
        val sb = StringBuilder()
        sb.append(d.title).append('\n').append(d.overview).append("\n\n")
        for (s in d.sections) {
            sb.append("## ").append(s.title).append('\n').append(s.summary).append('\n')
            s.points.forEach { sb.append("- ").append(it).append('\n') }
            sb.append('\n')
        }
        for (o in d.objectives) sb.append("Objective: ").append(o.text).append("\n").append(o.explain).append("\n\n")
        for (t in d.terms) {
            sb.append("**").append(t.term).append("**: ").append(t.definition)
            if (t.example.isNotBlank()) sb.append(" Example: ").append(t.example)
            sb.append('\n')
        }
        for (tb in d.tables) {
            sb.append("\nTABLE: ").append(tb.title).append('\n').append("| ").append(tb.headers.joinToString(" | ")).append(" |\n")
            tb.rows.forEach { sb.append("| ").append(it.joinToString(" | ")).append(" |\n") }
        }
        for (f in d.figures) sb.append("\nFIGURE ").append(f.id).append(": ").append(f.caption).append(" — ").append(f.explain).append('\n')
        return sb.toString()
    }

    /** The set's notes in the same shape the AI got while building, with the set's own ids. */
    private fun compactDeck(d: Deck): String {
        val o = JSONObject()
        o.put("objectives", org.json.JSONArray(d.objectives.map {
            JSONObject().put("id", it.id).put("text", it.text).put("explain", it.explain)
        }))
        o.put("terms", org.json.JSONArray(d.terms.map {
            JSONObject().put("id", it.id).put("term", it.term).put("definition", it.definition).put("objective", it.objective)
        }))
        o.put("tables", org.json.JSONArray(d.tables.map {
            JSONObject().put("id", it.id).put("title", it.title).put("objective", it.objective)
        }))
        o.put("figures", org.json.JSONArray(d.figures.map {
            JSONObject().put("id", it.id).put("caption", it.caption).put("explain", it.explain).put("objective", it.objective)
        }))
        return o.toString()
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
            JSONObject().put("id", it.str("id")).put("title", it.str("title")).put("headers", it.optJSONArray("headers"))
                .put("rows", it.optJSONArray("rows")).put("objective", it.str("objective"))
        }))
        o.put("figures", org.json.JSONArray(objects(notes.optJSONArray("figures")).map {
            JSONObject().put("id", it.str("id")).put("caption", it.str("caption")).put("explain", it.str("explain"))
                .put("objective", it.str("objective"))
        }))
        return o.toString()
    }

    private fun writingLine(chars: Int, partName: String, stage: Int): String {
        val lines = when (stage) {
            2 -> listOf(
                "Writing practice questions",
                "Making believable wrong answers",
                "Adding hints",
                "Writing scenario questions",
                "Sharpening the tricky answers",
            )
            1 -> listOf(
                "Planning the lessons",
                "Making hard ideas simple",
                "Finding everyday analogies",
                "Writing step by step",
                "Making flashcards",
            )
            else -> listOf(
                "Finding the learning objectives",
                "Pulling out key terms",
                "Inventing memory tricks",
                "Copying the tables",
                "Studying the diagrams",
            )
        }
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
        val cards = ArrayList<FactCard>()
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
            val lessonFor = objects(o.optJSONArray("lessons")).associateBy { it.str("objective") }
            objs.forEachIndexed { i, it ->
                val text = it.str("text")
                if (text.isBlank()) return@forEachIndexed
                val lesson = lessonFor[it.str("id")]
                objectives.add(
                    Objective(
                        id(it.str("id"), "o", i + 1), text, it.str("simple"), it.str("explain"),
                        it.strList("terms").map { t -> ref(t) },
                        lesson = beats(lesson, ::ref),
                        recap = lesson?.str("recap").orEmpty(),
                    )
                )
            }
            var cardNo = 0
            for (l in objects(o.optJSONArray("lessons"))) {
                val oid = ref(l.str("objective"))
                for (c in objects(l.optJSONArray("cards"))) {
                    val front = c.str("front")
                    val back = c.str("back")
                    if (front.isBlank() || back.isBlank()) continue
                    cardNo++
                    cards.add(FactCard("${prefix}c$cardNo", front, back, oid))
                }
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
            cards = unique(cards, { it.id }) { c, id -> c.copy(id = id) },
        )
    }

    /** The lessons as text, for the coverage check. */
    private fun lessonsText(lessons: org.json.JSONArray): String {
        val sb = StringBuilder()
        for (l in objects(lessons)) {
            sb.append("Objective ").append(l.str("objective")).append(":\n")
            for (st in objects(l.optJSONArray("steps"))) sb.append("- ").append(st.str("title")).append(": ").append(st.str("text")).append('\n')
            for (c in objects(l.optJSONArray("cards"))) sb.append("  card: ").append(c.str("front")).append(" / ").append(c.str("back")).append('\n')
        }
        return sb.toString()
    }

    /** Adds the coverage check's extra steps and cards to the lessons they belong to. */
    private fun addGaps(lessons: org.json.JSONArray, additions: List<JSONObject>) {
        for (a in additions) {
            val oid = a.str("objective")
            if (oid.isBlank()) continue
            val target: JSONObject = objects(lessons).firstOrNull { it.str("objective") == oid }
                ?: JSONObject().put("objective", oid).also { lessons.put(it) }
            val steps: org.json.JSONArray = target.optJSONArray("steps") ?: org.json.JSONArray().also { target.put("steps", it) }
            val cards: org.json.JSONArray = target.optJSONArray("cards") ?: org.json.JSONArray().also { target.put("cards", it) }
            objects(a.optJSONArray("steps")).forEach { steps.put(it) }
            objects(a.optJSONArray("cards")).forEach { cards.put(it) }
        }
    }

    /** A lesson's steps as the AI wrote them, with ids mapped to the set's own. */
    private fun beats(lesson: JSONObject?, ref: (String) -> String): List<Beat> =
        objects(lesson?.optJSONArray("steps")).mapNotNull { b ->
            val text = b.str("text")
            if (text.isBlank()) null
            else Beat(b.str("title"), text, b.strList("terms").map { ref(it) }, b.str("figure").let { if (it.isBlank()) "" else ref(it) })
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
            cards = old.cards + add.cards,
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
