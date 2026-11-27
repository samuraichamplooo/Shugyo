package dev.shugyo.game

import androidx.compose.ui.graphics.Color
import dev.shugyo.data.Deck
import dev.shugyo.data.Objective
import dev.shugyo.data.Prefs
import dev.shugyo.data.Srs
import dev.shugyo.data.Stats
import dev.shugyo.data.Store
import dev.shugyo.data.Term
import java.util.Random
import java.util.zip.CRC32

/** Each dojo (objective) has an element; its creatures share it. */
enum class Element(
    val label: String,
    val emoji: String,
    val main: Color,
    val dark: Color,
    val light: Color,
    val accent: Color,
    val move: String,
) {
    EMBER("Ember", "🔥", Color(0xFFFF7A45), Color(0xFFB93A1E), Color(0xFFFFD9C4), Color(0xFFFFD166), "Ember Dash"),
    TIDE("Tide", "💧", Color(0xFF4FA3E8), Color(0xFF1F5C99), Color(0xFFD3EAFB), Color(0xFF9EE7FF), "Bubble Burst"),
    LEAF("Leaf", "🌿", Color(0xFF59C173), Color(0xFF2E7D45), Color(0xFFDAF5E0), Color(0xFFFFE08A), "Leaf Blade"),
    SPARK("Spark", "⚡", Color(0xFFF5C518), Color(0xFFA88300), Color(0xFFFFF3C4), Color(0xFFFF8A3D), "Static Pop"),
    STONE("Stone", "🪨", Color(0xFFB08A6A), Color(0xFF6E5440), Color(0xFFEEE0D2), Color(0xFF7FC8A9), "Pebble Slam"),
    MIST("Mist", "🌫", Color(0xFFA78BFA), Color(0xFF6D4FC4), Color(0xFFEEE8FF), Color(0xFFFF9EC7), "Haze Swirl"),
    BLOOM("Bloom", "🌸", Color(0xFFFF8FB8), Color(0xFFC2457A), Color(0xFFFFE3EE), Color(0xFF8FE3C0), "Petal Storm"),
    FROST("Frost", "❄️", Color(0xFF7ADFE8), Color(0xFF2B9AA6), Color(0xFFE3FBFD), Color(0xFFB9C4FF), "Frost Nip"),
}

/** One creature's look, worked out from a key so the same term always gets the same creature. */
data class Species(
    val key: String,
    val name: String,
    val element: Element,
    val body: Int,      // 0 round, 1 egg, 2 wide, 3 pear
    val ears: Int,      // 0 round, 1 pointed, 2 horns, 3 antennae, 4 sprout, 5 long
    val eyes: Int,      // 0 round, 1 determined, 2 happy, 3 sparkly
    val tail: Int,      // 0 none, 1 puff, 2 flame, 3 leaf, 4 fin
    val pattern: Int,   // 0 none, 1 spots, 2 stripes, 3 forehead mark
    val mouth: Int,     // 0 smile, 1 fang, 2 open
    val headband: Boolean = false,
)

/** A partner you can pick: three stages, each with its own name. */
data class Starter(val species: Species, val stages: List<String>, val blurb: String)

object Creatures {
    private val suffixes = listOf("ki", "maru", "zu", "bo", "ra", "ko", "ji", "ta", "mi", "ri", "suke", "chi")
    private val syllables = listOf(
        "ka", "ro", "mi", "ze", "tsu", "ha", "no", "ri", "ku", "sa", "yo", "ma", "te", "shi", "ga", "ra", "do", "ken",
    )

    fun hash(s: String): Long = CRC32().apply { update(s.toByteArray()) }.value

    fun element(key: String): Element = Element.values()[(hash(key) % Element.values().size).toInt()]

    fun species(key: String, name: String, element: Element): Species {
        val r = Random(hash(key))
        return Species(
            key, name, element,
            body = r.nextInt(4), ears = r.nextInt(6), eyes = r.nextInt(4), tail = r.nextInt(5),
            pattern = r.nextInt(4), mouth = r.nextInt(3),
        )
    }

    /** A creature name made from the term, so the name itself is a little memory hook. */
    fun nameFor(term: String, key: String): String {
        val word = term.split(Regex("[^A-Za-z]+")).maxByOrNull { it.length }.orEmpty().lowercase()
        if (word.isEmpty()) return senseiName(key)
        val vowels = "aeiouy"
        val sb = StringBuilder()
        var groups = 0
        var inVowel = false
        for (c in word) {
            val v = c in vowels
            if (v && !inVowel) groups++
            if (!v && inVowel && groups >= 2) break
            inVowel = v
            sb.append(c)
            if (sb.length >= 6 && !v) break
        }
        val root = sb.toString().take(7)
        val suffix = suffixes[(hash(key) % suffixes.size).toInt()]
        return (root + suffix).replaceFirstChar { it.uppercase() }
    }

    private fun senseiName(key: String): String {
        val r = Random(hash("name:$key"))
        val n = 2 + r.nextInt(2)
        return (1..n).joinToString("") { syllables[r.nextInt(syllables.size)] }.replaceFirstChar { it.uppercase() }
    }

    fun dojoElement(o: Objective): Element = element("dojo:" + o.id + ":" + o.text.take(40))

    /** The creature that a key term becomes. */
    fun forTerm(deck: Deck, t: Term): Species {
        val o = deck.objective(t.objective)
        val el = if (o != null) dojoElement(o) else element("term:" + t.id)
        val key = deck.id + ":" + t.id + ":" + t.term
        return species(key, nameFor(t.term, key), el)
    }

    /** A creature for any flashcard or question, from its key and objective. */
    fun forKey(deck: Deck, key: String, objectiveId: String): Species {
        val o = deck.objective(objectiveId)
        val el = if (o != null) dojoElement(o) else element(key)
        return species(deck.id + ":" + key, senseiName(deck.id + key), el)
    }

    /** The Sensei who runs an objective's dojo. */
    fun sensei(deck: Deck, o: Objective): Species {
        val key = deck.id + ":sensei:" + o.id
        return species(key, "Sensei " + senseiName(key), dojoElement(o)).copy(eyes = 1)
    }

    // ---------------------------------------------------------------- your partner

    val starters = listOf(
        Starter(
            Species("starter:0", "Hinoko", Element.EMBER, body = 0, ears = 1, eyes = 1, tail = 2, pattern = 3, mouth = 1, headband = true),
            listOf("Hinoko", "Kagaribi", "Homura"),
            "A fiery fox kit with a big heart. Hits hard on tricky questions.",
        ),
        Starter(
            Species("starter:1", "Shizuku", Element.TIDE, body = 1, ears = 3, eyes = 3, tail = 4, pattern = 0, mouth = 0, headband = true),
            listOf("Shizuku", "Seseragi", "Ōnami"),
            "A calm droplet spirit. Steady, patient and always curious.",
        ),
        Starter(
            Species("starter:2", "Kodama", Element.LEAF, body = 3, ears = 4, eyes = 0, tail = 3, pattern = 1, mouth = 2, headband = true),
            listOf("Kodama", "Komorebi", "Shinboku"),
            "A sprout from the old forest. Grows a little with every lesson.",
        ),
    )

    /** 1, 2 or 3: your partner evolves at levels 10 and 25. */
    fun stage(level: Int) = when {
        level >= 25 -> 3
        level >= 10 -> 2
        else -> 1
    }

    fun nextEvolution(level: Int) = when {
        level < 10 -> 10
        level < 25 -> 25
        else -> 0
    }

    fun hasPartner() = Prefs.current.partner in starters.indices

    /** Your partner as it looks now (its stage follows your trainer level). */
    fun partner(): Pair<Species, Int> {
        val s = Prefs.current
        val st = starters[s.partner.coerceIn(0, starters.size - 1)]
        val stage = stage(Stats.level(s.xp))
        val name = s.partnerName.ifBlank { st.stages[stage - 1] }
        return st.species.copy(name = name) to stage
    }

    fun partnerStageName(): String {
        val s = Prefs.current
        val st = starters[s.partner.coerceIn(0, starters.size - 1)]
        return st.stages[stage(Stats.level(s.xp)) - 1]
    }

    // ---------------------------------------------------------------- codex

    /** Whether you've befriended a term's creature: by mastering its card or winning a battle with it. */
    fun befriended(deck: Deck, t: Term): Boolean {
        val p = Store.progress(deck.id)
        return t.id in p.friends || (p.cards["t:${t.id}"]?.box ?: 0) >= Srs.MASTERED_BOX
    }

    fun seen(deck: Deck, t: Term): Boolean {
        val p = Store.progress(deck.id)
        return befriended(deck, t) || t.id in p.seen || "t:${t.id}" in p.cards
    }
}
