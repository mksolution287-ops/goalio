package zero.ramjikvarosai.hirebazzar

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ProfileCatalog(
    val teams: List<FavoriteTeam>,
    val players: List<FavoritePlayer>,
    val nextTeamCursor: String? = null,
    val nextPlayerCursor: String? = null,
    val teamError: String? = null,
    val playerError: String? = null
)

object ProfileCatalogRepository {
    private const val PREFS = "goalio_profile_catalog_cache"
    private const val CATALOG = "catalog"
    private val mutex = Mutex()
    private var cachedCatalog: ProfileCatalog? = null

    fun cached(context: Context): ProfileCatalog? {
        cachedCatalog?.let { return it }
        return context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(CATALOG, null)
            ?.let { runCatching { JSONObject(it).toProfileCatalog() }.getOrNull() }
            ?.also { cachedCatalog = it }
    }

    suspend fun preload(context: Context, force: Boolean = false): ProfileCatalog {
        val catalog = mutex.withLock {
            val persistent = cached(context)
            if (!force) persistent?.let { return@withLock it }

            // Loading teams first also establishes the anonymous Firebase session before
            // the parallel player requests begin.
            var teamError: String? = null
            var playerError: String? = null
            val teamPage = runCatching { GoalioBackendApi.getTeams(limit = 6) }
                .onFailure { teamError = it.catalogMessage("Could not load teams.") }
                .getOrElse { BackendPage(emptyList(), null) }
            val teams = teamPage.items

            val playerPage = runCatching { GoalioBackendApi.getPlayers(limit = 6) }
                .onFailure { playerError = it.catalogMessage("Could not load players.") }
                .getOrElse { BackendPage(emptyList(), null) }
            val players = playerPage.items
                .distinctBy { it.id }
                .map { it.withCompetitionIds(teams) }

            if (teams.isEmpty() && players.isEmpty() && persistent != null) {
                return@withLock persistent.copy(teamError = teamError, playerError = playerError)
            }

            ProfileCatalog(
                teams = teams,
                players = players,
                nextTeamCursor = teamPage.nextCursor,
                nextPlayerCursor = playerPage.nextCursor,
                teamError = teamError ?: if (teams.isEmpty()) "No teams are available right now." else null,
                playerError = playerError ?: if (players.isEmpty()) "No players are available right now." else null
            ).also {
                cachedCatalog = it
                withContext(Dispatchers.IO) {
                    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit().putString(CATALOG, it.toJson().toString()).apply()
                }
            }
        }

        warmImages(context, catalog)
        return catalog
    }

    private fun warmImages(context: Context, catalog: ProfileCatalog) {
        runCatching {
            val imageLoader = SingletonImageLoader.get(context)
            val urls = (catalog.teams.take(6).mapNotNull { it.imageUrl } +
                catalog.players.take(6).mapNotNull { it.imageUrl }).distinct()
            urls.forEach { url ->
                imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
            }
        }
    }
}

private fun ProfileCatalog.toJson() = JSONObject().apply {
    put("teams", JSONArray(teams.map { team -> JSONObject().apply {
        put("id", team.id); put("name", team.name); put("shortName", team.shortName)
        put("color", team.primaryColor.value.toString()); putNullable("imageUrl", team.imageUrl)
        put("competitionIds", JSONArray(team.competitionIds.toList()))
    } }))
    put("players", JSONArray(players.map { player -> JSONObject().apply {
        put("id", player.id); put("name", player.name); put("team", player.team); put("initials", player.initials)
        put("color", player.accent.value.toString()); putNullable("imageUrl", player.imageUrl)
        put("competitionIds", JSONArray(player.competitionIds.toList()))
    } }))
    putNullable("nextTeamCursor", nextTeamCursor)
    putNullable("nextPlayerCursor", nextPlayerCursor)
}

private fun JSONObject.toProfileCatalog() = ProfileCatalog(
    teams = buildList {
        val source = optJSONArray("teams")
        if (source != null) for (index in 0 until source.length()) source.getJSONObject(index).run {
            add(FavoriteTeam(
                getString("id"), getString("name"), getString("shortName"),
                androidx.compose.ui.graphics.Color(getString("color").toULong()), nullableString("imageUrl"),
                optJSONArray("competitionIds").toIntSet()
            ))
        }
    },
    players = buildList {
        val source = optJSONArray("players")
        if (source != null) for (index in 0 until source.length()) source.getJSONObject(index).run {
            add(FavoritePlayer(
                getString("id"), getString("name"), getString("team"), getString("initials"),
                androidx.compose.ui.graphics.Color(getString("color").toULong()), nullableString("imageUrl"),
                optJSONArray("competitionIds").toIntSet()
            ))
        }
    },
    nextTeamCursor = nullableString("nextTeamCursor"), nextPlayerCursor = nullableString("nextPlayerCursor")
)

private fun JSONArray?.toIntSet(): Set<Int> = buildSet {
    if (this@toIntSet != null) for (index in 0 until length()) add(getInt(index))
}

private fun JSONObject.putNullable(key: String, value: Any?) {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
}

private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else optString(key).ifBlank { null }

private fun Throwable.catalogMessage(prefix: String): String =
    if (this is BackendException && statusCode == 503) {
        "$prefix Server is busy right now. Please retry later."
    } else {
        "$prefix Check your internet connection and retry later."
    }

internal fun FavoritePlayer.withCompetitionIds(teams: List<FavoriteTeam>): FavoritePlayer {
    if (competitionIds.isNotEmpty()) return this
    val inferred = teams.asSequence()
        .filter { team -> this.team.contains(team.name, ignoreCase = true) }
        .flatMap { it.competitionIds.asSequence() }
        .toSet()
    return copy(competitionIds = inferred)
}
