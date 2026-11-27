package dev.shugyo.ai

import dev.shugyo.build.Prompts
import dev.shugyo.data.Deck
import dev.shugyo.data.Objective
import dev.shugyo.data.Store
import dev.shugyo.data.Term
import dev.shugyo.scan.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Re-explains things on request: simpler, as an analogy, a story, a memory trick, and so on. */
object Coach {
    private val cache = ConcurrentHashMap<String, String>()

    fun cached(key: String): String? = cache[key]

    fun objectiveContext(deck: Deck, o: Objective): String {
        val sb = StringBuilder()
        sb.append("Objective: ").append(o.text).append('\n')
        if (o.explain.isNotBlank()) sb.append("Notes: ").append(o.explain).append('\n')
        for (t in deck.termsFor(o).take(12)) sb.append("- ").append(t.term).append(": ").append(t.definition).append('\n')
        for (tb in deck.tablesFor(o).take(2)) {
            sb.append("Table ").append(tb.title).append(": ").append(tb.headers.joinToString(" | ")).append('\n')
            tb.rows.take(12).forEach { sb.append(it.joinToString(" | ")).append('\n') }
        }
        return sb.toString()
    }

    fun termContext(t: Term): String =
        "${t.term}: ${t.definition}" + if (t.example.isNotBlank()) "\nExample: ${t.example}" else ""

    suspend fun explain(key: String, topic: String, context: String, style: Prompts.Style): String =
        cache[key] ?: Ai.ask(Prompts.coachSystem(), Prompts.coachUser(topic, context, style.ask), maxTokens = 2048)
            .also { cache[key] = it }

    suspend fun answer(topic: String, context: String, question: String): String =
        Ai.ask(
            Prompts.coachSystem(),
            Prompts.coachUser(topic, context, "My question: $question\nAnswer it simply and accurately, in a few short sentences."),
            maxTokens = 2048,
        )

    /** Looks at a diagram and explains it. */
    suspend fun figure(deckId: String, file: String, caption: String): String {
        val key = "fig:$deckId:$file"
        cache[key]?.let { return it }
        val bytes = withContext(Dispatchers.IO) { Images.forAi(java.io.File(Store.dir(deckId), file)) }
            ?: throw AiException("The picture is missing.")
        return Ai.ask(Prompts.coachSystem(), Prompts.figureUser(caption), listOf(AiImage("figure", bytes)), maxTokens = 2048)
            .also { cache[key] = it }
    }
}
