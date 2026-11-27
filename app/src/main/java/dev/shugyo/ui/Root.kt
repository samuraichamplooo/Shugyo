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
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        /** The finished job improved an existing set (nothing was built from the basket). */
        val upgrade: Boolean = false,
        /** The set being upgraded. */
        val upgradeDeck: String = "",
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    fun start(
        context: android.content.Context,
        items: List<dev.shugyo.scan.BasketItem>,
        title: String,
        subject: String,
        into: String? = null,
    ) {
        if (_state.value.running) return
        _state.value = State(running = true, status = "Getting ready…", fraction = 0.01f)
        // Keeps the build alive (and online) if you switch apps or lock the phone.
        dev.shugyo.BuildService.start(context)
        job = scope.launch {
            try {
                val deck = Builder.build(
                    items, title, subject,
                    onUpdate = { s, f -> if (_state.value.running) _state.value = _state.value.copy(status = s, fraction = f) },
                    cancelled = { job?.isCancelled == true },
                    intoDeckId = into,
                )
                _state.value = State(doneDeck = deck.id)
            } catch (e: CancellationException) {
                _state.value = State()
            } catch (e: Exception) {
                _state.value = State(error = e.message ?: "Something went wrong while building. Try again.")
            }
        }
    }

    /** Adds lessons, fact cards and questions to a set built earlier. */
    fun upgrade(context: android.content.Context, deckId: String) {
        if (_state.value.running) return
        _state.value = State(running = true, status = "Getting ready…", fraction = 0.01f, upgrade = true, upgradeDeck = deckId)
        dev.shugyo.BuildService.start(context)
        job = scope.launch {
            try {
                val deck = Builder.upgrade(
                    deckId,
                    onUpdate = { s, f -> if (_state.value.running) _state.value = _state.value.copy(status = s, fraction = f) },
                    cancelled = { job?.isCancelled == true },
                )
                _state.value = State(doneDeck = deck.id, upgrade = true)
            } catch (e: CancellationException) {
                _state.value = State()
            } catch (e: Exception) {
                _state.value = State(error = e.message ?: "Something went wrong. Try again.", upgrade = true)
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

    // The app closed unexpectedly last time: show what went wrong, once.
    val crashFile = remember { java.io.File(activity.filesDir, dev.shugyo.App.CRASH_FILE) }
    var crash by remember { mutableStateOf(if (crashFile.exists()) crashFile.readText() else null) }

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
                is Route.Player -> LessonPlayer(route.deckId, route.index)
                is Route.Cards -> FlashcardsScreen(route.deckId, route.all, route.starred)
                is Route.Quiz -> QuizScreen(route.deckId, route.objectiveId, route.missedOnly)
                is Route.Notes -> NotesScreen(route.deckId, route.tab)
                is Route.Battle -> BattleScreen(route.deckId, route.objectiveId)
                is Route.Codex -> CodexScreen(route.deckId)
                Route.Partner -> Screen("Your partner") { StarterPicker(Modifier.weight(1f)) { Nav.back() } }
            }
        }
    }

    crash?.let { text ->
        CrashDialog(text) {
            crashFile.delete()
            crash = null
        }
    }
}

@Composable
private fun CrashDialog(text: String, onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = CardHigh,
        title = { Text("Shugyō closed unexpectedly") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Here's what went wrong. Tap Copy and paste it to Claude (or screenshot it) so it can be fixed.",
                    color = Gold, fontSize = 13.sp,
                )
                Text(
                    text.lines().take(60).joinToString("\n"),
                    color = Soft, fontSize = 11.sp, lineHeight = 14.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(text))
                copied = true
            }) { Text(if (copied) "Copied ✓" else "Copy", color = Gold) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close", color = Faint) } },
    )
}
