package dev.shugyo.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.shugyo.build.Builder
import dev.shugyo.data.Store
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Things handed to the app from outside (the share sheet) for a screen to pick up. */
object Incoming {
    val url = mutableStateOf<String?>(null)
    val pdf = mutableStateOf<Uri?>(null)
}

/** Builds a study set in the background, so leaving the screen doesn't stop it. */
object BuildRunner {
    data class State(
        val running: Boolean = false,
        val status: String = "",
        val fraction: Float = 0f,
        val error: String = "",
        val doneDeck: String = "",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    fun start(items: List<dev.shugyo.scan.BasketItem>, title: String, subject: String) {
        if (_state.value.running) return
        _state.value = State(running = true, status = "Getting ready…", fraction = 0.01f)
        job = scope.launch {
            try {
                val deck = Builder.build(
                    items, title, subject,
                    onUpdate = { s, f -> if (_state.value.running) _state.value = _state.value.copy(status = s, fraction = f) },
                    cancelled = { job?.isCancelled == true },
                )
                _state.value = State(doneDeck = deck.id)
            } catch (e: CancellationException) {
                _state.value = State()
            } catch (e: Exception) {
                _state.value = State(error = e.message ?: "Something went wrong while building. Try again.")
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = State()
    }

    fun clear() {
        _state.value = State()
    }
}

@Composable
fun Root(activity: Activity) {
    val browser = remember { BrowserState(activity) }
    DisposableEffect(Unit) { onDispose { browser.destroy() } }
    val loaded by Store.loaded.collectAsState()

    BackHandler(enabled = Nav.stack.size > 1) { Nav.back() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        AnimatedContent(
            targetState = Nav.current,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screens",
        ) { route ->
            when (route) {
                Route.Home -> HomeScreen(loaded)
                Route.Browser -> BrowserScreen(browser)
                Route.Build -> BuildScreen()
                Route.Settings -> SettingsScreen()
                is Route.DeckHome -> DeckScreen(route.deckId)
                is Route.Learn -> LearnScreen(route.deckId)
                is Route.Lesson -> LessonScreen(route.deckId, route.index)
                is Route.Cards -> FlashcardsScreen(route.deckId, route.all, route.starred)
                is Route.Quiz -> QuizScreen(route.deckId, route.objectiveId, route.missedOnly)
                is Route.Notes -> NotesScreen(route.deckId, route.tab)
            }
        }
    }
}
