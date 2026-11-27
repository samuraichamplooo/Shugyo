package dev.shugyo.ai

import android.os.SystemClock
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
import java.net.UnknownHostException

class GeminiException(message: String) : AiException(message)

/**
 * Google's Gemini API with your own key (free from Google AI Studio). Replies are streamed.
 *
 * The free tier is often "overloaded" (HTTP 503) or out of quota for one model (429) while
 * another model still works, so this retries and falls back through several free models.
 */
object Gemini {
    private const val TAG = "Gemini"
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"

    // Model names change often: "latest" aliases plus recent fixed names, Flash first, then Flash-Lite.
    private val models = listOf(
        "gemini-flash-latest", "gemini-3.8-flash", "gemini-3.6-flash",
        "gemini-flash-lite-latest", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite",
    )

    private sealed interface Result {
        data object Ok : Result
        data object NotFound : Result
        data object BadConfig : Result
        data class Busy(val detail: String) : Result
        data class Quota(val detail: String) : Result
    }

    /** The thinking level each model accepted ("" for none), so a refused one isn't asked for again. */
    private val levels = java.util.concurrent.ConcurrentHashMap<String, String>()

    @Volatile
    var lastModel: String = ""
        private set

    /** Streams a reply into [onText]. Blocking: call from a background thread. */
    fun stream(
        key: String,
        system: String,
        user: String,
        images: List<AiImage>,
        maxTokens: Int,
        json: Boolean,
        quick: Boolean,
        cancelled: () -> Boolean,
        timeBudgetMs: Long,
        onStatus: (String) -> Unit,
        onText: (String) -> Unit,
    ) {
        if (key.isBlank()) throw GeminiException("Add your free Gemini API key in Settings first.")
        val deadline = SystemClock.elapsedRealtime() + timeBudgetMs
        val saved = Prefs.current.geminiModel
        val order = (listOf(saved) + models).filter { it.isNotBlank() }.distinct()
        val firstLevel = if (quick) "minimal" else "low"
        var busy = 0
        var quota = 0
        var lastDetail = ""
        for ((mi, model) in order.withIndex()) {
            var level: String? = if (levels.containsKey(model)) levels[model]?.ifEmpty { null } else firstLevel
            var tries = 0
            while (tries < 3) {
                if (cancelled()) return
                if (SystemClock.elapsedRealtime() > deadline) break
                tries++
                val r = attempt(key.trim(), model, level, system, user, images, maxTokens, json, cancelled, onText)
                when (r) {
                    Result.Ok -> {
                        levels[model] = level ?: ""
                        lastModel = model
                        if (model != saved) Prefs.update { it.copy(geminiModel = model) }
                        return
                    }
                    Result.NotFound -> break
                    Result.BadConfig -> {
                        level = when (level) {
                            "minimal" -> "low"
                            "low" -> null
                            else -> break
                        }
                        tries--
                    }
                    is Result.Quota -> {
                        quota++
                        lastDetail = r.detail
                        break
                    }
                    is Result.Busy -> {
                        busy++
                        lastDetail = r.detail
                        if (tries >= 2) break
                        onStatus("Gemini is busy, trying again…")
                        Thread.sleep(1500)
                    }
                }
            }
            if (SystemClock.elapsedRealtime() > deadline) break
            if (mi < order.size - 1 && (busy > 0 || quota > 0)) onStatus("Gemini is busy, trying another model…")
        }
        val why = if (lastDetail.isNotBlank()) " ($lastDetail)" else ""
        throw GeminiException(
            when {
                busy > 0 && quota == 0 -> "Google's free Gemini models are overloaded right now. Try again in a minute.$why"
                quota > 0 && busy == 0 -> "Your free Gemini limit is used up for now. It resets daily.$why"
                busy > 0 -> "Gemini is busy and some free limits are used up. Try again in a minute.$why"
                else -> "Gemini didn't accept any of the models Shugyō knows.$why"
            }
        )
    }

    private fun attempt(
        key: String,
        model: String,
        thinking: String?,
        system: String,
        user: String,
        images: List<AiImage>,
        maxTokens: Int,
        json: Boolean,
        cancelled: () -> Boolean,
        onText: (String) -> Unit,
    ): Result {
        val parts = JSONArray()
        for (img in images) {
            parts.put(JSONObject().put("text", "Image ${img.label}:"))
            parts.put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().put("mime_type", "image/jpeg")
                        .put("data", Base64.encodeToString(img.jpeg, Base64.NO_WRAP)),
                )
            )
        }
        parts.put(JSONObject().put("text", user))
        val config = JSONObject().put("temperature", 0.5).put("maxOutputTokens", maxTokens)
        if (thinking != null) config.put("thinkingConfig", JSONObject().put("thinkingLevel", thinking))
        if (json) config.put("responseMimeType", "application/json")
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", config)
        var gotText = false
        try {
            val c = URL("$BASE$model:streamGenerateContent?alt=sse").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 20_000
            c.readTimeout = 120_000
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.setRequestProperty("x-goog-api-key", key)
            c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code != 200) {
                val err = try {
                    c.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                } catch (e: Exception) {
                    ""
                }
                Log.w(TAG, "HTTP $code from $model: ${err.take(500)}")
                val msg = try {
                    JSONObject(err).optJSONObject("error")?.optString("message") ?: ""
                } catch (e: Exception) {
                    ""
                }
                val detail = "$code: ${msg.take(140)}".trimEnd(' ', ':')
                return when {
                    code == 404 -> Result.NotFound
                    code == 400 && msg.contains("API key", true) ->
                        throw GeminiException("Your Gemini API key didn't work. Copy it again from Google AI Studio. ($detail)")
                    code == 400 && thinking != null -> Result.BadConfig
                    code == 401 || code == 403 ->
                        throw GeminiException("Your Gemini API key was rejected. Copy it again from Google AI Studio. ($detail)")
                    code == 429 -> Result.Quota(detail)
                    code >= 500 -> Result.Busy(detail)
                    else -> throw GeminiException("Gemini error ($detail)")
                }
            }
            BufferedReader(InputStreamReader(c.inputStream, Charsets.UTF_8)).use { reader ->
                while (true) {
                    if (cancelled()) return Result.Ok
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.substring(5).trim()
                    if (data.isEmpty() || data == "[DONE]") continue
                    val obj = try {
                        JSONObject(data)
                    } catch (e: Exception) {
                        continue
                    }
                    obj.optJSONObject("error")?.let { e ->
                        val detail = "${e.optInt("code")}: ${e.optString("message").take(140)}"
                        if (!gotText) return Result.Busy(detail)
                        return Result.Ok
                    }
                    val ps = obj.optJSONArray("candidates")?.optJSONObject(0)
                        ?.optJSONObject("content")?.optJSONArray("parts") ?: continue
                    for (i in 0 until ps.length()) {
                        val p = ps.optJSONObject(i) ?: continue
                        if (p.optBoolean("thought", false)) continue
                        val t = p.optString("text", "")
                        if (t.isNotEmpty()) {
                            gotText = true
                            onText(t)
                        }
                    }
                }
            }
            return if (gotText) Result.Ok else Result.Busy("empty reply")
        } catch (e: UnknownHostException) {
            throw GeminiException("Couldn't reach Gemini. Check your internet connection.")
        } catch (e: SocketTimeoutException) {
            return if (gotText) Result.Ok else Result.Busy("timed out")
        } catch (e: IOException) {
            if (gotText) return Result.Ok
            Log.w(TAG, "connection problem with $model", e)
            return Result.Busy(e.javaClass.simpleName)
        }
    }
}
