package com.goalio.scores

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ProfileCatalog(
    val teams: List<FavoriteTeam>,
    val players: List<FavoritePlayer>
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
            val teams = GoalioBackendApi.getTeams(limit = 200).items
            require(teams.isNotEmpty()) { "No teams are available" }

            val catalogPlayers = GoalioBackendApi.getPlayers(limit = 100).items
            val players = catalogPlayers
                .distinctBy { it.id }
                .map { it.withCompetitionIds(teams) }
            require(players.isNotEmpty()) { "No players are available" }

            ProfileCatalog(teams, players).also { cachedCatalog = it }
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
