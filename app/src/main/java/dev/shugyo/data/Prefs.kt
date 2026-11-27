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
    val lastUrl: String = "https://connect.mheducation.com",
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
            lastUrl = sp.getString("lastUrl", d.lastUrl) ?: d.lastUrl,
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
            .putInt("xp", s.xp)
            .putInt("streak", s.streak)
            .putInt("bestStreak", s.bestStreak)
            .putLong("lastStudyDay", s.lastStudyDay)
            .putInt("xpToday", s.xpToday)
            .putInt("dailyGoal", s.dailyGoal)
            .apply()
    }
}
