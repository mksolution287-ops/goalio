package com.goalio.scores

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

data class MatchNotificationEvent(
    val id: String,
    val title: String,
    val message: String,
    val kickoffEpochMillis: Long? = null
)

data class MatchFeedResult(
    val matches: List<ScheduleMatch>,
    val scoreChanged: Boolean,
    val notificationEvents: List<MatchNotificationEvent> = emptyList()
)

object MatchRepository {
    val leagues = listOf(
        "fifa.world",
        "eng.1",
        "esp.1",
        "ita.1",
        "ger.1",
        "fra.1",
        "uefa.champions",
        "uefa.europa"
    )

    private const val PREFS = "goalio_match_cache"
    private const val CANONICAL_MATCHES = "matches_canonical"
    private val canonicalMatches = MutableStateFlow<Map<String, ScheduleMatch>>(emptyMap())
    private val canonicalDetails = MutableStateFlow<Map<String, MatchDetail>>(emptyMap())
    val matchUpdates = canonicalMatches.asStateFlow()
    val detailUpdates = canonicalDetails.asStateFlow()

    fun cachedFeed(context: Context, from: String, to: String): List<ScheduleMatch> {
        val range = context.cachePrefs().getString(feedKey(from, to), null)
            ?.let { runCatching { JSONArray(it).toScheduleMatches() }.getOrDefault(emptyList()) }
            .orEmpty()
        val persistedCanonical = context.cachePrefs().getString(CANONICAL_MATCHES, null)
            ?.let { runCatching { JSONArray(it).toScheduleMatches() }.getOrDefault(emptyList()) }
            .orEmpty()
        publishMatches(persistedCanonical, overwrite = false)
        publishMatches(range, overwrite = false)
        return range.map { canonicalMatches.value[matchKey(it.league, it.matchId)] ?: it }
    }

    suspend fun refreshFeed(context: Context, from: String, to: String): MatchFeedResult {
        val previousMatches = cachedFeed(context, from, to)
        val before = previousMatches.scoreSignature()
        val matches = coroutineScope {
            leagues.map { league ->
                async {
                    runCatching { GoalioBackendApi.getScheduleRange(league, from, to).matches }
                        .getOrDefault(emptyList())
                }
            }.flatMap { it.await() }
        }.distinctBy { "${it.league}:${it.matchId}" }
            .sortedWith(compareBy<ScheduleMatch> { stateRank(it.state) }.thenBy { it.kickoff.orEmpty() })
        val changed = before.isNotBlank() && before != matches.scoreSignature()
        val notificationEvents = matchNotificationEvents(previousMatches, matches)
        publishMatches(matches, overwrite = true)
        GoalioMatchNotifier.scheduleUpcoming(context, canonicalMatches.value.values.toList())
        val canonicalSnapshot = canonicalMatches.value.values.toList()
        withContext(Dispatchers.IO) {
            context.cachePrefs().edit()
                .putString(feedKey(from, to), JSONArray(matches.map { it.toJson() }).toString())
                .putString(CANONICAL_MATCHES, JSONArray(canonicalSnapshot.map { it.toJson() }).toString())
                .apply()
        }
        return MatchFeedResult(matches.map { canonicalMatches.value[matchKey(it.league, it.matchId)] ?: it }, changed, notificationEvents)
    }

    fun cachedDetail(context: Context, league: String, matchId: String): MatchDetail? {
        val cached = context.cachePrefs().getString(detailKey(league, matchId), null)
            ?.let { runCatching { JSONObject(it).toMatchDetail() }.getOrNull() }
        val reconciled = cached?.reconcile(canonicalMatches.value[matchKey(league, matchId)])
        if (reconciled != null) canonicalDetails.update { it + (matchKey(league, matchId) to reconciled) }
        return reconciled
    }

    suspend fun refreshDetail(context: Context, league: String, matchId: String): MatchDetail {
        val detail = GoalioBackendApi.getMatchDetail(league, matchId)
            .reconcile(canonicalMatches.value[matchKey(league, matchId)])
        canonicalDetails.update { it + (matchKey(league, matchId) to detail) }
        withContext(Dispatchers.IO) {
            context.cachePrefs().edit()
                .putString(detailKey(league, matchId), detail.toJson().toString())
                .apply()
        }
        return detail
    }

    fun cachedStandings(context: Context, league: String): LeagueStandings? =
        context.cachePrefs().getString(standingsKey(league), null)
            ?.let { runCatching { JSONObject(it).toLeagueStandings() }.getOrNull() }

    suspend fun refreshStandings(context: Context, league: String): LeagueStandings {
        val standings = GoalioBackendApi.getStandings(league)
        withContext(Dispatchers.IO) {
            context.cachePrefs().edit().putString(standingsKey(league), standings.toJson().toString()).apply()
        }
        return standings
    }

    fun nextRefreshDelayMillis(matches: List<ScheduleMatch>): Long =
        if (matches.any { it.state == "in" }) 2 * 60 * 1000L else 15 * 60 * 1000L

    fun canonical(match: ScheduleMatch): ScheduleMatch =
        canonicalMatches.value[matchKey(match.league, match.matchId)] ?: match

    fun seedExternal(matches: List<ScheduleMatch>) {
        publishMatches(matches, overwrite = false)
    }

    suspend fun synchronizeExternal(context: Context, matches: List<ScheduleMatch>) {
        publishMatches(matches, overwrite = true)
        GoalioMatchNotifier.scheduleUpcoming(context, canonicalMatches.value.values.toList())
        val snapshot = canonicalMatches.value.values.toList()
        withContext(Dispatchers.IO) {
            context.cachePrefs().edit()
                .putString(CANONICAL_MATCHES, JSONArray(snapshot.map { it.toJson() }).toString())
                .apply()
        }
    }

    private fun Context.cachePrefs() = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun feedKey(from: String, to: String) = "feed_${from}_$to"

    private fun detailKey(league: String, matchId: String) = "detail_${league}_$matchId"

    private fun standingsKey(league: String) = "standings_$league"

    private fun publishMatches(matches: List<ScheduleMatch>, overwrite: Boolean) {
        if (matches.isEmpty()) return
        canonicalMatches.update { current ->
            current.toMutableMap().apply {
                matches.forEach { match ->
                    val key = matchKey(match.league, match.matchId)
                    if (overwrite || key !in this) this[key] = match
                }
            }
        }
        val latest = canonicalMatches.value
        canonicalDetails.update { details ->
            details.mapValues { (key, detail) -> detail.reconcile(latest[key]) }
        }
    }
}

private fun matchKey(league: String, matchId: String) = "$league:$matchId"

private fun MatchDetail.reconcile(match: ScheduleMatch?): MatchDetail {
    if (match == null) return this
    return copy(
        status = match.status ?: status,
        statusDescription = match.statusDescription ?: statusDescription,
        kickoff = match.kickoff ?: kickoff,
        homeTeam = homeTeam?.copy(score = match.homeTeam?.score ?: homeTeam.score) ?: match.homeTeam,
        awayTeam = awayTeam?.copy(score = match.awayTeam?.score ?: awayTeam.score) ?: match.awayTeam,
        venue = venue ?: match.venue
    )
}

private fun matchNotificationEvents(
    previous: List<ScheduleMatch>,
    current: List<ScheduleMatch>
): List<MatchNotificationEvent> {
    if (previous.isEmpty()) return emptyList()
    val previousByKey = previous.associateBy { "${it.league}:${it.matchId}" }
    return current.mapNotNull { match ->
        val previousMatch = previousByKey["${match.league}:${match.matchId}"] ?: return@mapNotNull null
        when {
            previousMatch.state != "in" && match.state == "in" -> MatchNotificationEvent(
                id = "start_${match.league}_${match.matchId}",
                title = "Match started",
                message = "${match.compactNotificationName()} is live now",
                kickoffEpochMillis = match.kickoffEpochMillis()
            )
            match.state == "in" && previousMatch.scorePair() != match.scorePair() -> MatchNotificationEvent(
                id = "goal_${match.league}_${match.matchId}_${match.scorePair()}",
                title = "Goal update",
                message = "${match.compactNotificationName()} ${match.scorePair().replace(":", " - ")}",
                kickoffEpochMillis = match.kickoffEpochMillis()
            )
            else -> null
        }
    }
}

private fun ScheduleMatch.scorePair(): String =
    "${homeTeam?.score ?: "-"}:${awayTeam?.score ?: "-"}"

internal fun ScheduleMatch.compactNotificationName(): String {
    val home = homeTeam?.abbreviation ?: homeTeam?.shortName ?: homeTeam?.name ?: "Home"
    val away = awayTeam?.abbreviation ?: awayTeam?.shortName ?: awayTeam?.name ?: "Away"
    return "$home vs $away"
}

internal fun ScheduleMatch.kickoffEpochMillis(): Long? =
    runCatching { java.time.OffsetDateTime.parse(kickoff).toInstant().toEpochMilli() }.getOrNull()

fun stateRank(state: String?): Int = when (state) {
    "in" -> 0
    "pre" -> 1
    "post" -> 2
    else -> 3
}

private fun List<ScheduleMatch>.scoreSignature(): String = filter { it.state == "in" }
    .joinToString("|") { "${it.league}:${it.matchId}:${it.homeTeam?.score}:${it.awayTeam?.score}" }

private fun ScheduleMatch.toJson() = JSONObject().apply {
    put("matchId", matchId)
    put("league", league)
    putNullable("name", name)
    putNullable("shortName", shortName)
    putNullable("status", status)
    putNullable("statusDescription", statusDescription)
    putNullable("state", state)
    putNullable("kickoff", kickoff)
    putNullable("homeTeam", homeTeam?.toJson())
    putNullable("awayTeam", awayTeam?.toJson())
    putNullable("venue", venue?.toJson())
    put("detailApi", detailApi)
}

private fun MatchTeamInfo.toJson() = JSONObject().apply {
    put("id", id)
    put("name", name)
    putNullable("shortName", shortName)
    putNullable("abbreviation", abbreviation)
    putNullable("logo", logo)
    putNullable("score", score)
}

private fun MatchVenueInfo.toJson() = JSONObject().apply {
    putNullable("name", name)
    putNullable("city", city)
}

private fun LeagueStandings.toJson() = JSONObject().apply {
    put("league", league)
    putNullable("season", season)
    put("groups", JSONArray(groups))
    put("teams", JSONArray(teams.map { team -> JSONObject().apply {
        putNullable("rank", team.rank); put("teamId", team.teamId); put("name", team.name)
        putNullable("abbreviation", team.abbreviation); putNullable("logo", team.logo)
        putNullable("group", team.group); putNullable("stage", team.stage); putNullable("played", team.played)
        putNullable("wins", team.wins); putNullable("draws", team.draws); putNullable("losses", team.losses)
        putNullable("goalsFor", team.goalsFor); putNullable("goalsAgainst", team.goalsAgainst)
        putNullable("goalDifference", team.goalDifference); putNullable("points", team.points)
    } }))
}

private fun MatchDetail.toJson() = JSONObject().apply {
    put("matchId", matchId)
    put("league", league)
    putNullable("status", status)
    putNullable("statusDescription", statusDescription)
    putNullable("kickoff", kickoff)
    putNullable("homeTeam", homeTeam?.toJson())
    putNullable("awayTeam", awayTeam?.toJson())
    putNullable("venue", venue?.toJson())
    put("officials", JSONArray(officials.map { it.toJson() }))
    putNullable("weather", weather?.toJson())
    put("teamStats", JSONArray(teamStats.map { it.toJson() }))
    put("playerLeaders", JSONArray(playerLeaders.map { it.toJson() }))
    put("lineups", JSONArray(lineups.map { it.toJson() }))
    put("events", JSONArray(events.map { it.toJson() }))
    putNullable("summary", summary)
    putNullable("winProbability", winProbability?.toJson())
}

private fun TeamStatsBlock.toJson() = JSONObject().apply {
    putNullable("teamId", teamId)
    put("stats", JSONArray(stats.map { it.toJson() }))
}

private fun MatchStat.toJson() = JSONObject().apply {
    putNullable("name", name)
    putNullable("label", label)
    putNullable("value", value)
}

private fun MatchLeaderGroup.toJson() = JSONObject().apply {
    putNullable("category", category)
    put("players", JSONArray(players.map { it.toJson() }))
}

private fun MatchLeaderPlayer.toJson() = JSONObject().apply {
    putNullable("id", id)
    putNullable("name", name)
    putNullable("position", position)
    putNullable("jersey", jersey)
    putNullable("espnUrl", espnUrl)
    putNullable("mainStat", mainStat)
    put("stats", JSONArray(stats.map { it.toJson() }))
}

private fun MatchOfficialInfo.toJson() = JSONObject().apply {
    putNullable("name", name)
    putNullable("role", role)
}

private fun MatchWeatherInfo.toJson() = JSONObject().apply {
    putNullable("displayValue", displayValue)
    putNullable("temperature", temperature)
    putNullable("condition", condition)
}

private fun TeamLineupInfo.toJson() = JSONObject().apply {
    putNullable("teamId", teamId)
    putNullable("teamName", teamName)
    putNullable("formation", formation)
    putNullable("coach", coach)
    put("starters", JSONArray(starters.map { it.toJson() }))
    put("substitutes", JSONArray(substitutes.map { it.toJson() }))
}

private fun LineupPlayerInfo.toJson() = JSONObject().apply {
    putNullable("id", id)
    put("name", name)
    putNullable("position", position)
    putNullable("jersey", jersey)
    put("starter", starter)
    put("captain", captain)
    put("substitute", substitute)
    putNullable("formationPlace", formationPlace)
}

private fun MatchTimelineEvent.toJson() = JSONObject().apply {
    putNullable("minute", minute)
    putNullable("type", type)
    putNullable("text", text)
    putNullable("team", team)
}

private fun WinProbabilityInfo.toJson() = JSONObject().apply {
    put("homeWinPercentage", homeWinPercentage)
    put("awayWinPercentage", awayWinPercentage)
    putNullable("drawPercentage", drawPercentage)
}

private fun JSONObject.putNullable(key: String, value: Any?) {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
}

private fun JSONArray.toScheduleMatches() = buildList {
    for (index in 0 until length()) getJSONObject(index).run {
        add(ScheduleMatch(
            matchId = getString("matchId"),
            league = getString("league"),
            name = nullableString("name"),
            shortName = nullableString("shortName"),
            status = nullableString("status"),
            statusDescription = nullableString("statusDescription"),
            state = nullableString("state"),
            kickoff = nullableString("kickoff"),
            homeTeam = optJSONObject("homeTeam")?.toMatchTeamInfo(),
            awayTeam = optJSONObject("awayTeam")?.toMatchTeamInfo(),
            venue = optJSONObject("venue")?.toMatchVenueInfo(),
            detailApi = getString("detailApi")
        ))
    }
}

private fun JSONObject.toMatchDetail() = MatchDetail(
    matchId = getString("matchId"),
    league = getString("league"),
    status = nullableString("status"),
    statusDescription = nullableString("statusDescription"),
    kickoff = nullableString("kickoff"),
    homeTeam = optJSONObject("homeTeam")?.toMatchTeamInfo(),
    awayTeam = optJSONObject("awayTeam")?.toMatchTeamInfo(),
    venue = optJSONObject("venue")?.toMatchVenueInfo(),
    officials = optJSONArray("officials").toMatchOfficials(),
    weather = optJSONObject("weather")?.toMatchWeatherInfo(),
    teamStats = optJSONArray("teamStats").toTeamStatsBlocks(),
    playerLeaders = optJSONArray("playerLeaders").toLeaderGroups(),
    lineups = optJSONArray("lineups").toTeamLineups(),
    events = optJSONArray("events").toTimelineEvents(),
    summary = nullableString("summary"),
    winProbability = optJSONObject("winProbability")?.toWinProbabilityInfo()
)

private fun JSONObject.toLeagueStandings() = LeagueStandings(
    league = getString("league"),
    season = if (isNull("season")) null else optInt("season"),
    groups = optJSONArray("groups").toStrings(),
    teams = buildList {
        val source = optJSONArray("teams")
        if (source != null) for (index in 0 until source.length()) source.getJSONObject(index).run {
            add(StandingTeamInfo(
                rank = nullableInt("rank"), teamId = getString("teamId"), name = getString("name"),
                abbreviation = nullableString("abbreviation"), logo = nullableString("logo"),
                group = nullableString("group"), stage = nullableString("stage"), played = nullableInt("played"),
                wins = nullableInt("wins"), draws = nullableInt("draws"), losses = nullableInt("losses"),
                goalsFor = nullableInt("goalsFor"), goalsAgainst = nullableInt("goalsAgainst"),
                goalDifference = nullableInt("goalDifference"), points = nullableInt("points")
            ))
        }
    }
)

private fun JSONObject.toWinProbabilityInfo() = WinProbabilityInfo(
    homeWinPercentage = optInt("homeWinPercentage", 50),
    awayWinPercentage = optInt("awayWinPercentage", 50),
    drawPercentage = if (isNull("drawPercentage")) null else optInt("drawPercentage")
)

private fun JSONObject.toMatchTeamInfo() = MatchTeamInfo(
    id = getString("id"),
    name = getString("name"),
    shortName = nullableString("shortName"),
    abbreviation = nullableString("abbreviation"),
    logo = nullableString("logo"),
    score = if (isNull("score")) null else optInt("score")
)

private fun JSONObject.toMatchVenueInfo() = MatchVenueInfo(
    name = nullableString("name"),
    city = nullableString("city")
)

private fun JSONObject.toMatchWeatherInfo() = MatchWeatherInfo(
    displayValue = nullableString("displayValue"),
    temperature = nullableString("temperature"),
    condition = nullableString("condition")
)

private fun JSONArray?.toMatchOfficials(): List<MatchOfficialInfo> = buildList {
    if (this@toMatchOfficials != null) for (index in 0 until length()) getJSONObject(index).run {
        add(MatchOfficialInfo(nullableString("name"), nullableString("role")))
    }
}

private fun JSONArray?.toTeamStatsBlocks(): List<TeamStatsBlock> = buildList {
    if (this@toTeamStatsBlocks != null) for (index in 0 until length()) getJSONObject(index).run {
        add(TeamStatsBlock(nullableString("teamId"), optJSONArray("stats").toMatchStats()))
    }
}

private fun JSONArray?.toMatchStats(): List<MatchStat> = buildList {
    if (this@toMatchStats != null) for (index in 0 until length()) getJSONObject(index).run {
        add(MatchStat(nullableString("name"), nullableString("label"), nullableString("value")))
    }
}

private fun JSONArray?.toLeaderGroups(): List<MatchLeaderGroup> = buildList {
    if (this@toLeaderGroups != null) for (index in 0 until length()) getJSONObject(index).run {
        add(MatchLeaderGroup(nullableString("category"), optJSONArray("players").toLeaderPlayers()))
    }
}

private fun JSONArray?.toLeaderPlayers(): List<MatchLeaderPlayer> = buildList {
    if (this@toLeaderPlayers != null) for (index in 0 until length()) getJSONObject(index).run {
        add(MatchLeaderPlayer(
            id = nullableString("id"),
            name = nullableString("name"),
            position = nullableString("position"),
            jersey = nullableString("jersey"),
            espnUrl = nullableString("espnUrl"),
            mainStat = nullableString("mainStat"),
            stats = optJSONArray("stats").toMatchStats()
        ))
    }
}

private fun JSONArray?.toTeamLineups(): List<TeamLineupInfo> = buildList {
    if (this@toTeamLineups != null) for (index in 0 until length()) getJSONObject(index).run {
        add(TeamLineupInfo(
            teamId = nullableString("teamId"),
            teamName = nullableString("teamName"),
            formation = nullableString("formation"),
            coach = nullableString("coach"),
            starters = optJSONArray("starters").toLineupPlayers(),
            substitutes = optJSONArray("substitutes").toLineupPlayers()
        ))
    }
}

private fun JSONArray?.toLineupPlayers(): List<LineupPlayerInfo> = buildList {
    if (this@toLineupPlayers != null) for (index in 0 until length()) getJSONObject(index).run {
        add(LineupPlayerInfo(
            id = nullableString("id"),
            name = nullableString("name") ?: "Player",
            position = nullableString("position"),
            jersey = nullableString("jersey"),
            starter = optBoolean("starter", false),
            captain = optBoolean("captain", false),
            substitute = optBoolean("substitute", false),
            formationPlace = nullableString("formationPlace")
        ))
    }
}

private fun JSONArray?.toTimelineEvents(): List<MatchTimelineEvent> = buildList {
    if (this@toTimelineEvents != null) for (index in 0 until length()) getJSONObject(index).run {
        add(MatchTimelineEvent(
            minute = nullableString("minute"),
            type = nullableString("type"),
            text = nullableString("text"),
            team = nullableString("team")
        ))
    }
}

private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else optString(key).ifBlank { null }

private fun JSONObject.nullableInt(key: String): Int? = if (isNull(key)) null else optInt(key)

private fun JSONArray?.toStrings(): List<String> = buildList {
    if (this@toStrings != null) for (index in 0 until length()) add(getString(index))
}
