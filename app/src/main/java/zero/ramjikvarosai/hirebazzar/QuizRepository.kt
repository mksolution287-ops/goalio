package zero.ramjikvarosai.hirebazzar

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object QuizRepository {
    private const val PREFS = "goalio_quiz_cache"
    private const val LEADERBOARD = "leaderboard"
    suspend fun start(): QuizSessionInfo {
        val session = GoalioBackendApi.startQuiz()
        val language = AppLanguageState.current
        if (TranslationManager.isEnglish(language)) return session
        val source = session.questions.flatMap { listOf(it.category, it.prompt) + it.options }
        val translations = TranslationManager.get(GoalioApplication.instance).translateBatch(source, language)
        return session.copy(questions = session.questions.map { question ->
            question.copy(
                category = translations[question.category] ?: question.category,
                prompt = translations[question.prompt] ?: question.prompt,
                options = question.options.map { translations[it] ?: it }
            )
        })
    }

    suspend fun answer(sessionId: String, questionId: String, answerIndex: Int): QuizAnswerInfo {
        val result = GoalioBackendApi.answerQuiz(sessionId, questionId, answerIndex)
        val language = AppLanguageState.current
        if (TranslationManager.isEnglish(language)) return result
        return result.copy(
            explanation = TranslationManager.get(GoalioApplication.instance)
                .translateText(result.explanation, language)
        )
    }
    fun cachedLeaderboard(context: Context): QuizLeaderboardInfo? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LEADERBOARD, null)?.let { raw ->
        runCatching { JSONObject(raw).toLeaderboard() }.getOrNull()
    }
    suspend fun leaderboard(context: Context): QuizLeaderboardInfo {
        val fresh = GoalioBackendApi.getQuizLeaderboard()
        withContext(Dispatchers.IO) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(LEADERBOARD, fresh.toJson().toString()).apply() }
        return fresh
    }
}

private fun QuizLeaderboardInfo.toJson() = JSONObject().apply {
    put("entries", JSONArray(entries.map { JSONObject().put("rank", it.rank).put("username", it.username).put("xp", it.xp).put("isMe", it.isMe) }))
    me?.let { put("me", JSONObject().put("rank", it.rank).put("username", it.username).put("xp", it.xp).put("isMe", true)) }
}
private fun JSONObject.toLeaderboard(): QuizLeaderboardInfo {
    fun parse(item: JSONObject) = QuizLeaderInfo(item.getInt("rank"), item.getString("username"), item.getInt("xp"), item.optBoolean("isMe"))
    val entries = buildList { val source = getJSONArray("entries"); for (i in 0 until source.length()) add(parse(source.getJSONObject(i))) }
    return QuizLeaderboardInfo(entries, optJSONObject("me")?.let(::parse))
}
