package com.goalio.scores

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class BackendProfile(
    val userId: String,
    val name: String,
    val username: String,
    val favoriteTeamIds: List<String>,
    val favoritePlayerIds: List<String>,
    val favoriteTeams: List<String>,
    val favoritePlayers: List<String>,
    val onboardingCompleted: Boolean,
    val profileCompleted: Boolean
)

data class BackendHome(val greeting: String, val profile: BackendProfile)

data class BackendPage<T>(val items: List<T>, val nextCursor: String?)

object GoalioBackendApi {
    private val baseUrl: String
        get() = FirebaseRemoteConfig.getInstance().getString("backend_base_url")
            .trim().trimEnd('/')
            .takeIf { it.startsWith("https://") }
            ?: BuildConfig.BACKEND_BASE_URL

    suspend fun saveProfile(draft: ProfileDraft): BackendProfile = request(
        method = "POST",
        path = "/api/v1/users/profile",
        body = JSONObject().apply {
            put("name", draft.fullName)
            put("username", draft.username.lowercase())
            put("favoriteTeamIds", JSONArray(draft.teamIds.toList()))
            put("favoritePlayerIds", JSONArray(draft.playerIds.toList()))
            put("onboardingCompleted", true)
        }
    ) { parseProfile(it) }

    suspend fun getProfile(): BackendProfile = request("GET", "/api/v1/users/profile") { parseProfile(it) }

    suspend fun getHome(): BackendHome = request("GET", "/api/v1/home") { json ->
        BackendHome(json.getString("greeting"), parseProfile(json.getJSONObject("profile")))
    }

    suspend fun isUsernameAvailable(username: String): Boolean = request(
        "GET", "/api/v1/users/username/availability?username=${encode(username)}"
    ) { it.getBoolean("available") }

    suspend fun getTeams(limit: Int = 100, cursor: String? = null): BackendPage<FavoriteTeam> = request(
        "GET", pagedPath("/api/v1/football/teams", limit, cursor)
    ) { json ->
        BackendPage(
            items = json.getJSONArray("items").toTeamList(),
            nextCursor = json.nullableString("nextCursor")
        )
    }

    suspend fun getPlayers(limit: Int = 100, cursor: String? = null): BackendPage<FavoritePlayer> = request(
        "GET", pagedPath("/api/v1/football/players", limit, cursor)
    ) { json ->
        BackendPage(
            items = json.getJSONArray("items").toPlayerList(),
            nextCursor = json.nullableString("nextCursor")
        )
    }

    suspend fun searchTeams(query: String): List<FavoriteTeam> = request(
        "GET", "/api/v1/football/teams/search?q=${encode(query)}"
    ) { it.getJSONArray("items").toTeamList() }

    suspend fun searchPlayers(query: String): List<FavoritePlayer> = request(
        "GET", "/api/v1/football/players/search?q=${encode(query)}"
    ) { it.getJSONArray("items").toPlayerList() }

    private suspend fun <T> request(
        method: String,
        path: String,
        body: JSONObject? = null,
        parse: (JSONObject) -> T
    ): T = withContext(Dispatchers.IO) {
        val connection = URL("$baseUrl$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 8_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Authorization", "Bearer ${firebaseIdToken()}")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(text).optString("detail") }.getOrNull()
                throw BackendException(code, detail?.takeIf(String::isNotBlank) ?: "Request failed")
            }
            val json = if (text.trimStart().startsWith("[")) JSONObject().put("items", JSONArray(text)) else JSONObject(text)
            parse(json)
        } finally {
            connection.disconnect()
        }
    }

    private fun firebaseIdToken(): String {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: Tasks.await(auth.signInAnonymously()).user
            ?: error("Firebase anonymous sign-in did not return a user")
        return Tasks.await(user.getIdToken(false)).token ?: error("Firebase did not return an ID token")
    }

    private fun parseProfile(json: JSONObject) = BackendProfile(
        userId = json.getString("userId"),
        name = json.getString("name"),
        username = json.getString("username"),
        favoriteTeamIds = json.optJSONArray("favoriteTeamIds").toStrings(),
        favoritePlayerIds = json.optJSONArray("favoritePlayerIds").toStrings(),
        favoriteTeams = json.optJSONArray("favoriteTeams").toStrings(),
        favoritePlayers = json.optJSONArray("favoritePlayers").toStrings(),
        onboardingCompleted = json.optBoolean("onboardingCompleted"),
        profileCompleted = json.optBoolean("profileCompleted", true)
    )

    private fun JSONArray?.toStrings(): List<String> = buildList {
        if (this@toStrings != null) for (index in 0 until length()) add(getString(index))
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }

    private fun JSONArray.toTeamList() = buildList {
        for (index in 0 until length()) getJSONObject(index).run {
            val id = getString("id")
            add(FavoriteTeam(id, getString("name"), getString("shortName"), resultColor(id), optString("imageUrl").ifBlank { null }))
        }
    }

    private fun JSONArray.toPlayerList() = buildList {
        for (index in 0 until length()) getJSONObject(index).run {
            val name = getString("name")
            val id = getString("id")
            val initials = name.split(' ').mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
            add(FavoritePlayer(id, name, getString("team"), initials, resultColor(id), optString("imageUrl").ifBlank { null }))
        }
    }

    private fun resultColor(id: String) = when (id) {
        "arg", "messi" -> androidx.compose.ui.graphics.Color(0xFF75B9E7)
        "bra", "neymar" -> androidx.compose.ui.graphics.Color(0xFFF7D34A)
        "fra", "mbappe" -> androidx.compose.ui.graphics.Color(0xFF3159A7)
        "por", "ronaldo" -> androidx.compose.ui.graphics.Color(0xFF2B9A62)
        else -> androidx.compose.ui.graphics.Color(0xFFB0B0B0)
    }

    private fun pagedPath(path: String, limit: Int, cursor: String?): String = buildString {
        append(path)
        append("?limit=")
        append(limit.coerceIn(1, 200))
        if (!cursor.isNullOrBlank()) {
            append("&cursor=")
            append(encode(cursor))
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
}

class BackendException(val statusCode: Int, message: String) : Exception(message)
