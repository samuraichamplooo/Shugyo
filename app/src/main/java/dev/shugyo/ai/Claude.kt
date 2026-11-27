package dev.shugyo.ai

import android.util.Base64
import android.util.Log
import dev.shugyo.data.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * Claude through Anthropic's Messages API (a key from the Claude Console, billed there).
 * Streams the answer and can look at pictures (textbook pages and diagrams).
 */
object Claude {
    private const val TAG = "Claude"
    private const val URL_MESSAGES = "https://api.anthropic.com/v1/messages"

    val models = listOf(
        "claude-sonnet-5-5" to "Sonnet 5.5 · balanced (recommended)",
        "claude-opus-5-5" to "Opus 5.5 · best explanations",
        "claude-haiku-5-5" to "Haiku 5.5 · fastest, cheapest",
    )

    @Volatile
    var lastModel: String = ""
        private set

    fun stream(
        key: String,
        system: String,
        user: String,
        images: List<AiImage>,
        maxTokens: Int,
        quick: Boolean,
        cancelled: () -> Boolean,
        onText: (String) -> Unit,
    ) {
        if (key.isBlank()) throw AiException("Add your Claude API key in Settings first.")
        val chosen = Prefs.current.claudeModel.ifBlank { models.first().first }
        val order = listOf(chosen) + models.map { it.first }.filter { it != chosen }
        var detail = ""
        for (model in order) {
            if (cancelled()) return
            var r = attempt(key.trim(), model, system, user, images, maxTokens, cancelled, onText, quick)
            if (r is Result.Plainer) r = attempt(key.trim(), model, system, user, images, maxTokens, cancelled, onText, false)
            when (r) {
                Result.Ok -> {
                    lastModel = model
                    return
                }
                is Result.TryNext -> detail = r.why
                is Result.Fail -> throw AiException(r.why)
                Result.Plainer -> detail = "$model didn't accept the request"
            }
        }
        throw AiException("Claude couldn't answer: $detail")
    }

    private sealed interface Result {
        data object Ok : Result
        data class TryNext(val why: String) : Result
        data class Fail(val why: String) : Result
        data object Plainer : Result
    }

    private fun attempt(
        key: String,
        model: String,
        system: String,
        user: String,
        images: List<AiImage>,
        maxTokens: Int,
        cancelled: () -> Boolean,
        onText: (String) -> Unit,
        quick: Boolean,
    ): Result {
        val content = JSONArray()
        for (img in images) {
            content.put(JSONObject().put("type", "text").put("text", "Image ${img.label}:"))
            content.put(
                JSONObject().put("type", "image").put(
                    "source",
                    JSONObject().put("type", "base64").put("media_type", "image/jpeg")
                        .put("data", Base64.encodeToString(img.jpeg, Base64.NO_WRAP)),
                )
            )
        }
        content.put(JSONObject().put("type", "text").put("text", user))
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("system", system)
            .put("stream", true)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        if (quick) body.put("output_config", JSONObject().put("effort", "low"))

        val conn = (URL(URL_MESSAGES).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 120_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("anthropic-version", "2026-10-21")
        }
        var produced = false
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.use { String(it.readBytes()) }.orEmpty()
                Log.w(TAG, "HTTP $code: ${err.take(300)}")
                val msg = try {
                    JSONObject(err).optJSONObject("error")?.optString("message")
                } catch (e: Exception) {
                    null
                }.orEmpty()
                return when {
                    code == 400 && quick && (msg.contains("effort", true) || msg.contains("output_config", true)) -> Result.Plainer
                    code == 401 -> Result.Fail("Claude refused the key. Check it in the Claude Console.")
                    code == 403 -> Result.Fail("This Claude key isn't allowed to do that. $msg".trim())
                    msg.contains("credit balance", ignoreCase = true) ->
                        Result.Fail("Your Claude API credit balance is too low. Add credits in the Claude Console.")
                    code == 404 || (code == 400 && msg.contains("model", ignoreCase = true)) -> Result.TryNext("$model: $msg")
                    code == 429 -> Result.Fail("Claude's usage limit was reached for now. $msg".trim())
                    code == 529 || code >= 500 -> Result.Fail("Claude is busy right now. Try again in a moment.")
                    else -> Result.Fail("Claude: ${msg.ifBlank { "HTTP $code" }}")
                }
            }
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    if (cancelled()) return Result.Ok
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    val ev = try {
                        JSONObject(data)
                    } catch (e: Exception) {
                        continue
                    }
                    when (ev.optString("type")) {
                        "content_block_delta" -> {
                            val delta = ev.optJSONObject("delta") ?: continue
                            if (delta.optString("type") == "text_delta") {
                                val t = delta.optString("text")
                                if (t.isNotEmpty()) {
                                    produced = true
                                    onText(t)
                                }
                            }
                        }
                        "error" -> {
                            val m = ev.optJSONObject("error")?.optString("message").orEmpty()
                            if (!produced) return Result.Fail("Claude: ${m.ifBlank { "something went wrong" }}")
                            return Result.Ok
                        }
                        "message_stop" -> return Result.Ok
                    }
                }
            }
            return if (produced) Result.Ok else Result.Fail("Claude answered with nothing.")
        } catch (e: SocketTimeoutException) {
            return if (produced) Result.Ok else Result.Fail("Claude took too long to answer.")
        } catch (e: IOException) {
            return if (produced) Result.Ok else Result.Fail("Couldn't reach Claude (${e.javaClass.simpleName}).")
        }
    }
}
