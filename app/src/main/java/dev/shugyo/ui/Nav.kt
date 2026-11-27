package dev.shugyo.ui

import androidx.compose.runtime.mutableStateListOf

/** The screens, as a simple back stack. */
sealed interface Route {
    data object Home : Route
    data object Browser : Route
    data object Build : Route
    data object Settings : Route
    data class DeckHome(val deckId: String) : Route
    data class Learn(val deckId: String) : Route
    data class Lesson(val deckId: String, val index: Int) : Route
    /** The step-by-step lesson for one objective. */
    data class Player(val deckId: String, val index: Int) : Route
    data class Cards(val deckId: String, val all: Boolean = false, val starred: Boolean = false) : Route
    data class Quiz(val deckId: String, val objectiveId: String = "", val missedOnly: Boolean = false) : Route
    data class Notes(val deckId: String, val tab: Int = 0) : Route
    data class Battle(val deckId: String, val objectiveId: String = "") : Route
    data class Codex(val deckId: String) : Route
    data object Partner : Route
}

object Nav {
    val stack = mutableStateListOf<Route>(Route.Home)

    val current: Route get() = stack.last()

    fun go(r: Route) {
        if (stack.last() != r) stack.add(r)
    }

    /** Swaps the current screen for another (no stop on the way back). */
    fun replace(r: Route) {
        if (stack.size > 1) stack.removeAt(stack.size - 1)
        stack.add(r)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.size - 1)
        return true
    }

    fun home() {
        while (stack.size > 1) stack.removeAt(stack.size - 1)
    }
}
