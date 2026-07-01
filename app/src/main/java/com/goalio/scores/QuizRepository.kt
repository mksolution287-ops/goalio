package com.goalio.scores

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object QuizRepository {
    private const val PREFS = "goalio_quiz_cache"
    private const val LEADERBOARD = "leaderboard"
    suspend fun start() = GoalioBackendApi.startQuiz()
    suspend fun answer(sessionId: String, questionId: String, answerIndex: Int) = GoalioBackendApi.answerQuiz(sessionId, questionId, answerIndex)
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
