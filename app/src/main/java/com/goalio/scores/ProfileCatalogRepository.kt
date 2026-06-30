package com.goalio.scores

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ProfileCatalog(
    val teams: List<FavoriteTeam>,
    val players: List<FavoritePlayer>,
    val nextTeamCursor: String? = null,
    val nextPlayerCursor: String? = null
)

object ProfileCatalogRepository {
    private val mutex = Mutex()
    private var cachedCatalog: ProfileCatalog? = null

    fun cached(): ProfileCatalog? = cachedCatalog

    suspend fun preload(context: Context, force: Boolean = false): ProfileCatalog {
        val catalog = mutex.withLock {
            if (!force) cachedCatalog?.let { return@withLock it }

            // Loading teams first also establishes the anonymous Firebase session before
            // the parallel player requests begin.
            val teamPage = GoalioBackendApi.getTeams(limit = 6)
            val teams = teamPage.items
            require(teams.isNotEmpty()) { "No teams are available" }

            val playerPage = GoalioBackendApi.getPlayers(limit = 6)
            val catalogPlayers = playerPage.items
            val players = catalogPlayers
                .distinctBy { it.id }
                .map { it.withCompetitionIds(teams) }
            require(players.isNotEmpty()) { "No players are available" }

            ProfileCatalog(
                teams = teams,
                players = players,
                nextTeamCursor = teamPage.nextCursor,
                nextPlayerCursor = playerPage.nextCursor
            ).also { cachedCatalog = it }
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

internal fun FavoritePlayer.withCompetitionIds(teams: List<FavoriteTeam>): FavoritePlayer {
    if (competitionIds.isNotEmpty()) return this
    val inferred = teams.asSequence()
        .filter { team -> this.team.contains(team.name, ignoreCase = true) }
        .flatMap { it.competitionIds.asSequence() }
        .toSet()
    return copy(competitionIds = inferred)
}
