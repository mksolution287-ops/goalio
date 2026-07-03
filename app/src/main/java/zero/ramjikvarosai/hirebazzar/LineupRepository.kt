package zero.ramjikvarosai.hirebazzar

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

object LineupRepository {
    private const val PREFS = "goalio_lineup_cache"

    fun cached(context: Context, league: String, eventId: String): MatchLineupInfo? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(cacheKey(league, eventId), null)
            ?.let { runCatching { JSONObject(it).toMatchLineup() }.getOrNull() }

    suspend fun refresh(context: Context, league: String, eventId: String): MatchLineupInfo {
        val fresh = GoalioBackendApi.getMatchLineup(league, eventId)
        withContext(Dispatchers.IO) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(cacheKey(league, eventId), fresh.toJson().toString()).apply()
        }
        return fresh
    }

    fun refreshDelayMillis(lineup: MatchLineupInfo?): Long {
        if (lineup?.status == "FINAL") return 24 * 60 * 60 * 1000L
        val due = lineup?.nextRefreshAt?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }
        return due?.let { Duration.between(Instant.now(), it).toMillis().coerceIn(60_000L, 30 * 60 * 1000L) }
            ?: if (lineup?.status == "LIVE") 5 * 60 * 1000L else 30 * 60 * 1000L
    }

    private fun cacheKey(league: String, eventId: String) = "${league}_$eventId"
}

private fun MatchLineupInfo.toJson() = JSONObject().apply {
    put("eventId", eventId); put("status", status); put("source", source); put("formationStatus", formationStatus)
    put("lastUpdated", lastUpdated); putNullable("nextRefreshAt", nextRefreshAt); putNullable("kickoff", kickoff)
    put("isStale", isStale); put("home", home.toJson()); put("away", away.toJson())
}

private fun NormalizedTeamLineupInfo.toJson() = JSONObject().apply {
    putNullable("teamId", teamId); putNullable("teamName", teamName); putNullable("teamLogo", teamLogo)
    putNullable("formation", formation)
    putNullable("manager", manager?.let { JSONObject().apply { put("name", it.name); putNullable("photo", it.photo) } })
    put("startingXI", JSONArray(startingXI.map { it.toJson() }))
    put("bench", JSONArray(bench.map { it.toJson() }))
    put("unavailable", JSONArray(unavailable.map { JSONObject().apply { put("name", it.name); put("reason", it.reason) } }))
}

private fun PitchLineupPlayerInfo.toJson() = JSONObject().apply {
    putNullable("id", id); put("name", name); putNullable("number", number); putNullable("position", position)
    putNullable("role", role); putNullable("photo", photo); put("captain", captain); putNullable("x", x); putNullable("y", y)
}

private fun JSONObject.toMatchLineup() = MatchLineupInfo(
    eventId = getString("eventId"), status = getString("status"), source = getString("source"),
    formationStatus = getString("formationStatus"), lastUpdated = getString("lastUpdated"),
    nextRefreshAt = nullableString("nextRefreshAt"), kickoff = nullableString("kickoff"),
    isStale = optBoolean("isStale", false), home = getJSONObject("home").toTeam(), away = getJSONObject("away").toTeam()
)

private fun JSONObject.toTeam() = NormalizedTeamLineupInfo(
    teamId = nullableString("teamId"), teamName = nullableString("teamName"), teamLogo = nullableString("teamLogo"),
    formation = nullableString("formation"),
    manager = optJSONObject("manager")?.run { LineupManagerInfo(getString("name"), nullableString("photo")) },
    startingXI = optJSONArray("startingXI").toPlayers(), bench = optJSONArray("bench").toPlayers(),
    unavailable = buildList {
        val source = optJSONArray("unavailable")
        if (source != null) for (index in 0 until source.length()) source.getJSONObject(index).run {
            add(UnavailablePlayerInfo(getString("name"), getString("reason")))
        }
    }
)

private fun JSONArray?.toPlayers(): List<PitchLineupPlayerInfo> = buildList {
    if (this@toPlayers != null) for (index in 0 until length()) getJSONObject(index).run {
        add(PitchLineupPlayerInfo(
            id = nullableString("id"), name = getString("name"), number = nullableInt("number"),
            position = nullableString("position"), role = nullableString("role"), photo = nullableString("photo"),
            captain = optBoolean("captain", false), x = nullableFloat("x"), y = nullableFloat("y")
        ))
    }
}

private fun JSONObject.putNullable(key: String, value: Any?) {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
}

private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }
private fun JSONObject.nullableInt(key: String): Int? = if (isNull(key)) null else optInt(key)
private fun JSONObject.nullableFloat(key: String): Float? = if (isNull(key)) null else optDouble(key).toFloat()
