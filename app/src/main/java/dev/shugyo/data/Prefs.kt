package dev.shugyo.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Settings(
    val geminiKey: String = "",        // free key from Google AI Studio
    val geminiModel: String = "",      // last model that worked
    val claudeKey: String = "",        // optional Claude API key (Claude Console)
    val claudeModel: String = "claude-sonnet-5-5",
    val preferClaude: Boolean = false, // build study sets with Claude first when both keys are set
    val audience: String = "a nursing student who wants hard ideas made simple and a little fun",
    val questionsPerObjective: Int = 4,
    val newCardsPerDay: Int = 25,
    val lastUrl: String = "",                // the in-app browser opens where you left off
    val bookmarks: String = "[]",            // JSON: [{"t": title, "u": url}]
    val recent: String = "[]",               // recently visited sites, same shape
    val desktopSite: Boolean = false,        // the browser shows sites' full desktop layout
    val readPopups: Boolean = true,          // Scan page also taps linked terms to read their pop-up definitions
    // Progress across every study set.
    val xp: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lastStudyDay: Long = -1,
    val xpToday: Int = 0,
    val dailyGoal: Int = 150,
)

object Prefs {
    private lateinit var sp: SharedPreferences
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings
    val current: Settings get() = _settings.value

    fun init(context: Context) {
        sp = context.getSharedPreferences("shugyo", Context.MODE_PRIVATE)
        val d = Settings()
        _settings.value = Settings(
            geminiKey = sp.getString("geminiKey", d.geminiKey) ?: "",
            geminiModel = sp.getString("geminiModel", d.geminiModel) ?: "",
            claudeKey = sp.getString("claudeKey", d.claudeKey) ?: "",
            claudeModel = sp.getString("claudeModel", d.claudeModel) ?: d.claudeModel,
            preferClaude = sp.getBoolean("preferClaude", d.preferClaude),
            audience = sp.getString("audience", d.audience) ?: d.audience,
            questionsPerObjective = sp.getInt("questionsPerObjective", d.questionsPerObjective),
            newCardsPerDay = sp.getInt("newCardsPerDay", d.newCardsPerDay),
            lastUrl = (sp.getString("lastUrl", d.lastUrl) ?: d.lastUrl)
                // Early builds started on McGraw Hill Connect; that's no longer the default.
                .let { if (it.trimEnd('/') == "https://connect.mheducation.com") "" else it },
            bookmarks = sp.getString("bookmarks", d.bookmarks) ?: d.bookmarks,
            recent = sp.getString("recent", d.recent) ?: d.recent,
            desktopSite = sp.getBoolean("desktopSite", d.desktopSite),
            readPopups = sp.getBoolean("readPopups", d.readPopups),
            xp = sp.getInt("xp", d.xp),
            streak = sp.getInt("streak", d.streak),
            bestStreak = sp.getInt("bestStreak", d.bestStreak),
            lastStudyDay = sp.getLong("lastStudyDay", d.lastStudyDay),
            xpToday = sp.getInt("xpToday", d.xpToday),
            dailyGoal = sp.getInt("dailyGoal", d.dailyGoal),
        )
    }

    @Synchronized
    fun update(change: (Settings) -> Settings) {
        val s = change(_settings.value)
        _settings.value = s
        sp.edit()
            .putString("geminiKey", s.geminiKey)
            .putString("geminiModel", s.geminiModel)
            .putString("claudeKey", s.claudeKey)
            .putString("claudeModel", s.claudeModel)
            .putBoolean("preferClaude", s.preferClaude)
            .putString("audience", s.audience)
            .putInt("questionsPerObjective", s.questionsPerObjective)
            .putInt("newCardsPerDay", s.newCardsPerDay)
            .putString("lastUrl", s.lastUrl)
            .putString("bookmarks", s.bookmarks)
            .putString("recent", s.recent)
            .putBoolean("desktopSite", s.desktopSite)
            .putBoolean("readPopups", s.readPopups)
            .putInt("xp", s.xp)
            .putInt("streak", s.streak)
            .putInt("bestStreak", s.bestStreak)
            .putLong("lastStudyDay", s.lastStudyDay)
            .putInt("xpToday", s.xpToday)
            .putInt("dailyGoal", s.dailyGoal)
            .apply()
    }
}

/** A saved web page: bookmarks and recently visited sites for the in-app browser. */
data class Link(val title: String, val url: String) {
    val host: String get() = try {
        android.net.Uri.parse(url).host.orEmpty().removePrefix("www.")
    } catch (e: Exception) {
        url
    }
}

object Links {
    private fun read(json: String): List<Link> = try {
        val a = org.json.JSONArray(json)
        (0 until a.length()).mapNotNull { i ->
            a.optJSONObject(i)?.let { Link(it.optString("t"), it.optString("u")) }?.takeIf { it.url.isNotBlank() }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun write(list: List<Link>): String =
        org.json.JSONArray(list.map { org.json.JSONObject().put("t", it.title).put("u", it.url) }).toString()

    fun bookmarks(s: Settings = Prefs.current) = read(s.bookmarks)
    fun recent(s: Settings = Prefs.current) = read(s.recent)

    fun isBookmarked(url: String) = bookmarks().any { it.url == url }

    fun toggleBookmark(title: String, url: String) {
        if (url.isBlank()) return
        Prefs.update { s ->
            val list = read(s.bookmarks)
            val next = if (list.any { it.url == url }) list.filter { it.url != url }
            else list + Link(title.ifBlank { url }, url)
            s.copy(bookmarks = write(next))
        }
    }

    fun removeBookmark(url: String) = Prefs.update { s -> s.copy(bookmarks = write(read(s.bookmarks).filter { it.url != url })) }

    /** Remembers the last page of each site (newest first, six sites). */
    fun visited(title: String, url: String) {
        if (!url.startsWith("http")) return
        val link = Link(title.ifBlank { url }, url)
        Prefs.update { s ->
            val list = listOf(link) + read(s.recent).filter { it.host != link.host }
            s.copy(recent = write(list.take(6)), lastUrl = url)
        }
    }
}
