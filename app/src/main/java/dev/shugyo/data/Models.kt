package dev.shugyo.data

import org.json.JSONArray
import org.json.JSONObject

/** One part of the chapter, in plain words. */
data class Section(val title: String, val summary: String, val points: List<String>)

/** One step of a lesson: a small idea, explained simply. */
data class Beat(
    val title: String,
    val text: String,
    val terms: List<String> = emptyList(),
    val figure: String = "",
)

/** A learning objective (outcome) from the book, with a plain-words version and a step-by-step lesson. */
data class Objective(
    val id: String,
    val text: String,
    val simple: String,
    val explain: String,
    val terms: List<String>,
    val lesson: List<Beat> = emptyList(),
    val recap: String = "",
)

/** A flashcard for a fact from the reading (a question on the front, the answer on the back). */
data class FactCard(val id: String, val front: String, val back: String, val objective: String)

data class Term(
    val id: String,
    val term: String,
    val definition: String,
    val simple: String,
    val mnemonic: String,
    val example: String,
    val objective: String,
)

data class TableBlock(
    val id: String,
    val title: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val takeaway: String,
    val objective: String,
)

/** A diagram or picture saved with the study set (the file lives in the set's folder). */
data class Figure(
    val id: String,
    val file: String,
    val caption: String,
    val explain: String,
    val labels: List<String>,
    val objective: String,
)

object QType {
    const val MC = "mc"       // one right answer
    const val SATA = "sata"   // select all that apply
    const val TF = "tf"       // true / false
}

data class Question(
    val id: String,
    val type: String,
    val prompt: String,
    val choices: List<String>,
    val answer: List<Int>,
    val hint: String,
    val explain: String,
    val objective: String,
    val term: String,
    val level: Int,
) {
    val isMulti get() = type == QType.SATA
    fun isRight(picked: Set<Int>) = picked == answer.toSet()
    val typeLabel
        get() = when (type) {
            QType.SATA -> "Select all that apply"
            QType.TF -> "True or false"
            else -> "Multiple choice"
        }
}

/** A study set: usually one chapter (or part of one). */
data class Deck(
    val id: String,
    val title: String,
    val subject: String,
    val source: String,
    val created: Long,
    val overview: String,
    val sections: List<Section>,
    val objectives: List<Objective>,
    val terms: List<Term>,
    val tables: List<TableBlock>,
    val figures: List<Figure>,
    val questions: List<Question>,
    val cards: List<FactCard> = emptyList(),
) {
    val hasLessons get() = objectives.any { it.lesson.isNotEmpty() }
    fun term(id: String) = terms.firstOrNull { it.id == id }
    fun figure(id: String) = figures.firstOrNull { it.id == id }
    fun card(id: String) = cards.firstOrNull { it.id == id }
    fun cardsFor(o: Objective) = cards.filter { it.objective == o.id }
    fun objective(id: String) = objectives.firstOrNull { it.id == id }
    fun termsFor(o: Objective): List<Term> {
        val linked = o.terms.mapNotNull { term(it) }
        val back = terms.filter { it.objective == o.id }
        return (linked + back).distinctBy { it.id }
    }
    fun tablesFor(o: Objective) = tables.filter { it.objective == o.id }
    fun figuresFor(o: Objective) = figures.filter { it.objective == o.id }
    fun questionsFor(o: Objective) = questions.filter { it.objective == o.id }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("subject", subject).put("source", source)
        .put("created", created).put("overview", overview)
        .put("sections", JSONArray(sections.map {
            JSONObject().put("title", it.title).put("summary", it.summary).put("points", strings(it.points))
        }))
        .put("objectives", JSONArray(objectives.map {
            JSONObject().put("id", it.id).put("text", it.text).put("simple", it.simple)
                .put("explain", it.explain).put("terms", strings(it.terms)).put("recap", it.recap)
                .put("lesson", JSONArray(it.lesson.map { b ->
                    JSONObject().put("title", b.title).put("text", b.text).put("terms", strings(b.terms)).put("figure", b.figure)
                }))
        }))
        .put("cards", JSONArray(cards.map {
            JSONObject().put("id", it.id).put("front", it.front).put("back", it.back).put("objective", it.objective)
        }))
        .put("terms", JSONArray(terms.map {
            JSONObject().put("id", it.id).put("term", it.term).put("definition", it.definition)
                .put("simple", it.simple).put("mnemonic", it.mnemonic).put("example", it.example)
                .put("objective", it.objective)
        }))
        .put("tables", JSONArray(tables.map {
            JSONObject().put("id", it.id).put("title", it.title).put("headers", strings(it.headers))
                .put("rows", JSONArray(it.rows.map { r -> strings(r) }))
                .put("takeaway", it.takeaway).put("objective", it.objective)
        }))
        .put("figures", JSONArray(figures.map {
            JSONObject().put("id", it.id).put("file", it.file).put("caption", it.caption)
                .put("explain", it.explain).put("labels", strings(it.labels)).put("objective", it.objective)
        }))
        .put("questions", JSONArray(questions.map {
            JSONObject().put("id", it.id).put("type", it.type).put("prompt", it.prompt)
                .put("choices", strings(it.choices)).put("answer", JSONArray(it.answer))
                .put("hint", it.hint).put("explain", it.explain).put("objective", it.objective)
                .put("term", it.term).put("level", it.level)
        }))

    companion object {
        fun fromJson(o: JSONObject): Deck = Deck(
            id = o.optString("id"),
            title = o.optString("title"),
            subject = o.optString("subject"),
            source = o.optString("source"),
            created = o.optLong("created"),
            overview = o.optString("overview"),
            sections = objects(o.optJSONArray("sections")).map {
                Section(it.str("title"), it.str("summary"), it.strList("points"))
            },
            objectives = objects(o.optJSONArray("objectives")).map {
                Objective(
                    it.str("id"), it.str("text"), it.str("simple"), it.str("explain"), it.strList("terms"),
                    objects(it.optJSONArray("lesson")).map { b -> Beat(b.str("title"), b.str("text"), b.strList("terms"), b.str("figure")) },
                    it.str("recap"),
                )
            },
            terms = objects(o.optJSONArray("terms")).map {
                Term(
                    it.str("id"), it.str("term"), it.str("definition"), it.str("simple"),
                    it.str("mnemonic"), it.str("example"), it.str("objective"),
                )
            },
            tables = objects(o.optJSONArray("tables")).map {
                TableBlock(
                    it.str("id"), it.str("title"), it.strList("headers"),
                    arrays(it.optJSONArray("rows")).map { r -> stringsOf(r) },
                    it.str("takeaway"), it.str("objective"),
                )
            },
            figures = objects(o.optJSONArray("figures")).map {
                Figure(
                    it.str("id"), it.str("file"), it.str("caption"), it.str("explain"),
                    it.strList("labels"), it.str("objective"),
                )
            },
            questions = objects(o.optJSONArray("questions")).map {
                Question(
                    it.str("id"), it.str("type"), it.str("prompt"), it.strList("choices"),
                    ints(it.optJSONArray("answer")), it.str("hint"), it.str("explain"),
                    it.str("objective"), it.str("term"), it.optInt("level", 1),
                )
            },
            cards = objects(o.optJSONArray("cards")).map {
                FactCard(it.str("id"), it.str("front"), it.str("back"), it.str("objective"))
            },
        )
    }
}

/** Spaced-repetition state of one flashcard. */
data class CardState(val box: Int = 0, val due: Long = 0, val reps: Int = 0, val lapses: Int = 0)

/** What you've done with one study set. Saved next to it. */
class Progress(
    val cards: MutableMap<String, CardState> = HashMap(),
    val learned: MutableSet<String> = HashSet(),
    val qRight: MutableMap<String, Int> = HashMap(),
    val qWrong: MutableMap<String, Int> = HashMap(),
    val starred: MutableSet<String> = HashSet(),
    /** Term creatures befriended in battle, and ones you've met. */
    val friends: MutableSet<String> = HashSet(),
    val seen: MutableSet<String> = HashSet(),
    var newDay: Long = -1,
    var newToday: Int = 0,
    var lastStudied: Long = 0,
) {
    fun toJson(): JSONObject {
        val c = JSONObject()
        for ((k, v) in cards) {
            c.put(k, JSONObject().put("box", v.box).put("due", v.due).put("reps", v.reps).put("lapses", v.lapses))
        }
        return JSONObject()
            .put("cards", c)
            .put("learned", JSONArray(learned.toList()))
            .put("qRight", JSONObject(qRight as Map<*, *>))
            .put("qWrong", JSONObject(qWrong as Map<*, *>))
            .put("starred", JSONArray(starred.toList()))
            .put("friends", JSONArray(friends.toList()))
            .put("seen", JSONArray(seen.toList()))
            .put("newDay", newDay)
            .put("newToday", newToday)
            .put("lastStudied", lastStudied)
    }

    companion object {
        fun fromJson(o: JSONObject): Progress {
            val p = Progress()
            o.optJSONObject("cards")?.let { c ->
                for (k in c.keys()) {
                    val v = c.optJSONObject(k) ?: continue
                    p.cards[k] = CardState(v.optInt("box"), v.optLong("due"), v.optInt("reps"), v.optInt("lapses"))
                }
            }
            p.learned.addAll(stringsOf(o.optJSONArray("learned")))
            o.optJSONObject("qRight")?.let { m -> for (k in m.keys()) p.qRight[k] = m.optInt(k) }
            o.optJSONObject("qWrong")?.let { m -> for (k in m.keys()) p.qWrong[k] = m.optInt(k) }
            p.starred.addAll(stringsOf(o.optJSONArray("starred")))
            p.friends.addAll(stringsOf(o.optJSONArray("friends")))
            p.seen.addAll(stringsOf(o.optJSONArray("seen")))
            p.newDay = o.optLong("newDay", -1)
            p.newToday = o.optInt("newToday")
            p.lastStudied = o.optLong("lastStudied")
            return p
        }
    }
}

// ---------------------------------------------------------------- JSON helpers

fun strings(list: List<String>) = JSONArray().also { a -> list.forEach { a.put(it) } }

fun stringsOf(a: JSONArray?): List<String> {
    if (a == null) return emptyList()
    val out = ArrayList<String>(a.length())
    for (i in 0 until a.length()) {
        val v = a.opt(i) ?: continue
        if (v == JSONObject.NULL) continue
        val s = v.toString().trim()
        if (s.isNotEmpty()) out.add(s)
    }
    return out
}

fun ints(a: JSONArray?): List<Int> {
    if (a == null) return emptyList()
    val out = ArrayList<Int>()
    for (i in 0 until a.length()) {
        val v = a.opt(i)
        val n = when (v) {
            is Number -> v.toInt()
            is String -> v.trim().toIntOrNull()
            else -> null
        }
        if (n != null) out.add(n)
    }
    return out
}

fun objects(a: JSONArray?): List<JSONObject> {
    if (a == null) return emptyList()
    val out = ArrayList<JSONObject>(a.length())
    for (i in 0 until a.length()) a.optJSONObject(i)?.let { out.add(it) }
    return out
}

fun arrays(a: JSONArray?): List<JSONArray> {
    if (a == null) return emptyList()
    val out = ArrayList<JSONArray>(a.length())
    for (i in 0 until a.length()) a.optJSONArray(i)?.let { out.add(it) }
    return out
}

/** A string field, with JSON nulls treated as empty. */
fun JSONObject.str(key: String): String {
    val v = opt(key) ?: return ""
    if (v == JSONObject.NULL) return ""
    return v.toString().trim()
}

/** A list of strings, also accepting a single string. */
fun JSONObject.strList(key: String): List<String> {
    val v = opt(key) ?: return emptyList()
    return when (v) {
        is JSONArray -> stringsOf(v)
        JSONObject.NULL -> emptyList()
        else -> listOf(v.toString()).filter { it.isNotBlank() }
    }
}
