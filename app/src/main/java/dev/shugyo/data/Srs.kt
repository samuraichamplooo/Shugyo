package dev.shugyo.data

import java.time.LocalDate
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Spaced repetition: each card sits in a box. Knowing it moves it up a box and it comes back later;
 * missing it sends it back to the start so you see it again soon.
 */
object Srs {
    /** Days until a card in each box is due again. */
    private val intervals = intArrayOf(0, 1, 2, 4, 8, 16, 32, 64)
    const val MASTERED_BOX = 4

    enum class Grade { AGAIN, HARD, GOOD, EASY }

    fun today(): Long = LocalDate.now().toEpochDay()

    fun termCard(t: Term) = "t:${t.id}"
    fun figureCard(f: Figure) = "f:${f.id}"

    /** Every flashcard in a set: its key terms, then its explained diagrams. */
    fun cardIds(deck: Deck): List<String> =
        deck.terms.map { termCard(it) } + deck.figures.filter { it.explain.isNotBlank() }.map { figureCard(it) }

    fun grade(s: CardState?, g: Grade): CardState {
        val cur = s ?: CardState()
        val box = when (g) {
            Grade.AGAIN -> 0
            Grade.HARD -> maxOf(1, cur.box)
            Grade.GOOD -> cur.box + 1
            Grade.EASY -> cur.box + 2
        }.coerceIn(0, intervals.size - 1)
        val due = today() + intervals[box]
        return CardState(
            box = box,
            due = due,
            reps = cur.reps + 1,
            lapses = cur.lapses + if (g == Grade.AGAIN && cur.reps > 0) 1 else 0,
        )
    }

    fun dueCards(deck: Deck, p: Progress): List<String> {
        val t = today()
        return cardIds(deck).filter { id -> p.cards[id]?.let { it.due <= t } ?: false }
    }

    fun newCards(deck: Deck, p: Progress): List<String> = cardIds(deck).filter { it !in p.cards }

    /** How many new cards you can still start today in this set. */
    fun newLeftToday(p: Progress): Int {
        val used = if (p.newDay == today()) p.newToday else 0
        return (Prefs.current.newCardsPerDay - used).coerceAtLeast(0)
    }

    fun noteNewSeen(p: Progress) {
        val t = today()
        if (p.newDay != t) {
            p.newDay = t
            p.newToday = 0
        }
        p.newToday++
    }

    /** Cards waiting today: everything due plus today's share of new ones. */
    fun waitingToday(deck: Deck, p: Progress): Int =
        dueCards(deck, p).size + min(newCards(deck, p).size, newLeftToday(p))

    /** 0..1: how well you know a set, from cards, objectives and quiz answers. */
    fun mastery(deck: Deck, p: Progress): Float {
        val ids = cardIds(deck)
        val cards = if (ids.isEmpty()) 0f else
            ids.sumOf { min(p.cards[it]?.box ?: 0, MASTERED_BOX) }.toFloat() / (MASTERED_BOX * ids.size)
        val objectives = if (deck.objectives.isEmpty()) cards else
            deck.objectives.count { it.id in p.learned }.toFloat() / deck.objectives.size
        val quiz = if (deck.questions.isEmpty()) cards else
            deck.questions.count { (p.qRight[it.id] ?: 0) > 0 && (p.qRight[it.id] ?: 0) >= (p.qWrong[it.id] ?: 0) }
                .toFloat() / deck.questions.size
        return (0.45f * cards + 0.3f * objectives + 0.25f * quiz).coerceIn(0f, 1f)
    }
}

/** XP, trainer level and the daily streak, shared by every study set. */
object Stats {
    /** XP needed to reach a level: 0, 100, 300, 600, 1000… */
    fun xpFor(level: Int) = 50 * level * (level - 1)

    fun level(xp: Int): Int {
        var l = ((1 + sqrt(1 + xp / 12.5)) / 2).toInt().coerceAtLeast(1)
        while (xpFor(l + 1) <= xp) l++
        while (l > 1 && xpFor(l) > xp) l--
        return l
    }

    /** 0..1 progress through the current level. */
    fun levelProgress(xp: Int): Float {
        val l = level(xp)
        val a = xpFor(l)
        val b = xpFor(l + 1)
        return ((xp - a).toFloat() / (b - a)).coerceIn(0f, 1f)
    }

    fun rank(level: Int) = when {
        level >= 30 -> "Kensei 剣聖"
        level >= 20 -> "Master 師範"
        level >= 12 -> "Samurai 侍"
        level >= 6 -> "Rōnin 浪人"
        level >= 3 -> "Student 門弟"
        else -> "Beginner 初心"
    }

    /** XP today, with the streak kept up to date (a day with any study keeps it going). */
    fun xpToday(): Int {
        val s = Prefs.current
        return if (s.lastStudyDay == Srs.today()) s.xpToday else 0
    }

    fun streakNow(): Int {
        val s = Prefs.current
        val t = Srs.today()
        return if (s.lastStudyDay >= t - 1) s.streak else 0
    }

    fun award(xp: Int) {
        if (xp <= 0) return
        val t = Srs.today()
        Prefs.update { s ->
            val streak = when {
                s.lastStudyDay == t -> s.streak
                s.lastStudyDay == t - 1 -> s.streak + 1
                else -> 1
            }
            s.copy(
                xp = s.xp + xp,
                xpToday = (if (s.lastStudyDay == t) s.xpToday else 0) + xp,
                streak = streak,
                bestStreak = maxOf(s.bestStreak, streak),
                lastStudyDay = t,
            )
        }
    }
}
