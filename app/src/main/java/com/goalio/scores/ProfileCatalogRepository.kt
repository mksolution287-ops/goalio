package com.goalio.scores

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    suspend fun preload(context: Context, force: Boolean = false): ProfileCatalog = mutex.withLock {
        if (!force) cachedCatalog?.let { return@withLock it }

        // Loading teams first also establishes the anonymous Firebase session before
        // the parallel player requests begin.
        val teams = GoalioBackendApi.getTeams(limit = 200).items
        require(teams.isNotEmpty()) { "No teams are available" }

        val featuredPlayers = coroutineScope {
            FEATURED_PLAYER_QUERIES.map { query ->
                async {
                    runCatching { GoalioBackendApi.searchPlayers(query) }
                        .getOrDefault(emptyList())
                        .firstOrNull()
                }
            }.awaitAll().filterNotNull()
        }
        val catalogPlayers = GoalioBackendApi.getPlayers(limit = 100).items
        val players = (featuredPlayers + catalogPlayers)
            .distinctBy { it.id }
            .map { it.withCompetitionIds(teams) }
        require(players.isNotEmpty()) { "No players are available" }

        ProfileCatalog(teams, players).also { catalog ->
            cachedCatalog = catalog
            preloadImages(context, catalog)
        }
    }

    private suspend fun preloadImages(context: Context, catalog: ProfileCatalog) {
        val imageLoader = SingletonImageLoader.get(context)
        val initialUrls = (catalog.teams.take(6).mapNotNull { it.imageUrl } +
            catalog.players.take(6).mapNotNull { it.imageUrl }).distinct()

        coroutineScope {
            initialUrls.map { url ->
                async {
                    imageLoader.execute(ImageRequest.Builder(context).data(url).build())
                }
            }.awaitAll()
        }

        val remainingUrls = (catalog.teams.mapNotNull { it.imageUrl } +
            catalog.players.mapNotNull { it.imageUrl })
            .distinct()
            .filterNot { it in initialUrls }
        remainingUrls.forEach { url ->
            imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
        }
    }
}

internal val FEATURED_PLAYER_QUERIES = listOf(
    "Lionel Messi",
    "Cristiano Ronaldo",
    "Kylian Mbappe",
    "Erling Haaland",
    "Mohamed Salah",
    "Neymar"
)

internal fun FavoritePlayer.withCompetitionIds(teams: List<FavoriteTeam>): FavoritePlayer {
    if (competitionIds.isNotEmpty()) return this
    val inferred = teams.asSequence()
        .filter { team -> this.team.contains(team.name, ignoreCase = true) }
        .flatMap { it.competitionIds.asSequence() }
        .toSet()
    return copy(competitionIds = inferred)
}
