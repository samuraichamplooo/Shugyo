package dev.shugyo.ai

import android.os.SystemClock
import dev.shugyo.data.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class AiException(message: String) : Exception(message)

/** A picture for the AI to look at, with the name the prompt uses for it (e.g. "snap3"). */
class AiImage(val label: String, val jpeg: ByteArray)

enum class Engine(val label: String) { GEMINI("Gemini"), CLAUDE("Claude") }

/**
 * Picks the AI: Gemini (free key) first unless you chose Claude, and the other one if the first fails.
 */
object Ai {
    private const val COOL_DOWN_MS = 10 * 60_000L
    private val coolDown = HashMap<Engine, Long>()

    fun configured(): List<Engine> {
        val s = Prefs.current
        val list = ArrayList<Engine>()
        if (s.geminiKey.isNotBlank()) list.add(Engine.GEMINI)
        if (s.claudeKey.isNotBlank()) {
            if (s.preferClaude) list.add(0, Engine.CLAUDE) else list.add(Engine.CLAUDE)
        }
        return list
    }

    fun ready() = configured().isNotEmpty()

    private fun ordered(): List<Engine> {
        val now = SystemClock.elapsedRealtime()
        return configured().sortedBy { if ((coolDown[it] ?: 0L) > now) 1 else 0 }
    }

    /**
     * Asks for a whole answer. [onProgress] hears how many characters have arrived so far and
     * status lines such as retries. [quick] is for short explanations; building sets uses more thought.
     */
    suspend fun ask(
        system: String,
        user: String,
        images: List<AiImage> = emptyList(),
        maxTokens: Int = 4096,
        json: Boolean = false,
        quick: Boolean = true,
        cancelled: () -> Boolean = { false },
        onStatus: (String) -> Unit = {},
        onProgress: (Int) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val engines = ordered()
        if (engines.isEmpty()) throw AiException("Add a Gemini (free) or Claude API key in Settings first.")
        var lastError: Exception? = null
        for ((i, engine) in engines.withIndex()) {
            if (cancelled()) throw CancellationException("cancelled")
            val sb = StringBuilder()
            val sink: (String) -> Unit = {
                sb.append(it)
                onProgress(sb.length)
            }
            try {
                when (engine) {
                    Engine.GEMINI -> Gemini.stream(
                        Prefs.current.geminiKey, system, user, images, maxTokens, json, quick, cancelled,
                        timeBudgetMs = if (quick) 60_000 else 240_000,
                        onStatus = onStatus, onText = sink,
                    )
                    Engine.CLAUDE -> Claude.stream(
                        Prefs.current.claudeKey, system, user, images, maxTokens, quick, cancelled, sink,
                    )
                }
                coolDown.remove(engine)
                val out = sb.toString().trim()
                if (out.isNotEmpty()) return@withContext out
                lastError = AiException("${engine.label} answered with nothing.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                coolDown[engine] = SystemClock.elapsedRealtime() + COOL_DOWN_MS
            }
            if (i < engines.size - 1) onStatus("${engine.label} isn't answering. Trying ${engines[i + 1].label}…")
        }
        throw AiException(lastError?.message ?: "The AI didn't answer. Try again in a minute.")
    }

    /** Tries each configured AI with a tiny request. One line per AI. */
    suspend fun testAll(): List<String> = withContext(Dispatchers.IO) {
        val s = Prefs.current
        Engine.values().map { engine ->
            val key = if (engine == Engine.GEMINI) s.geminiKey else s.claudeKey
            if (key.isBlank()) return@map "${engine.label}: no key added"
            val sb = StringBuilder()
            try {
                when (engine) {
                    Engine.GEMINI -> Gemini.stream(
                        key, "Reply with one word.", "Say ready.", emptyList(), 64, false, true, { false },
                        45_000, {}, { sb.append(it) },
                    )
                    Engine.CLAUDE -> Claude.stream(
                        key, "Reply with one word.", "Say ready.", emptyList(), 64, true, { false }, { sb.append(it) },
                    )
                }
                if (sb.isBlank()) "${engine.label}: ✗ answered with nothing"
                else "${engine.label}: ✓ works (${if (engine == Engine.GEMINI) Gemini.lastModel else Claude.lastModel})"
            } catch (e: Exception) {
                "${engine.label}: ✗ ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }
}
