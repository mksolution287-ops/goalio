package com.goalio.scores

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object WorldCupRepository {
    private const val PREFS = "goalio_world_cup_cache"
    private const val BOOTSTRAP = "bootstrap"

    fun cached(context: Context): WorldCupBootstrapInfo? {
        val cached = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(BOOTSTRAP, null)
            ?.let { runCatching { JSONObject(it).toWorldCupBootstrapInfo() }.getOrNull() }
        if (cached != null) MatchRepository.seedExternal(cached.allMatches())
        return cached?.reconciled()
    }

    suspend fun refresh(context: Context): WorldCupBootstrapInfo {
        val fresh = GoalioBackendApi.getWorldCupBootstrap()
        MatchRepository.synchronizeExternal(context, fresh.allMatches())
        withContext(Dispatchers.IO) {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(BOOTSTRAP, fresh.toJson().toString())
                .apply()
        }
        return fresh.reconciled()
    }

    fun reconcile(value: WorldCupBootstrapInfo): WorldCupBootstrapInfo = value.reconciled()
}

private fun WorldCupBootstrapInfo.allMatches() = liveMatches + todayMatches + upcomingMatches + recentResults

private fun WorldCupBootstrapInfo.reconciled() = copy(
    liveMatches = liveMatches.map(MatchRepository::canonical),
    todayMatches = todayMatches.map(MatchRepository::canonical),
    upcomingMatches = upcomingMatches.map(MatchRepository::canonical),
    recentResults = recentResults.map(MatchRepository::canonical)
)

private fun WorldCupBootstrapInfo.toJson() = JSONObject().apply {
    put("tournament", JSONObject().apply {
        put("id", tournament.id)
        put("name", tournament.name)
        put("stage", tournament.stage)
        put("hostCities", tournament.hostCities)
        putNullable("daysToFinal", tournament.daysToFinal)
    })
    put("liveMatches", JSONArray(liveMatches.map { it.toWorldCupJson() }))
    put("todayMatches", JSONArray(todayMatches.map { it.toWorldCupJson() }))
    put("upcomingMatches", JSONArray(upcomingMatches.map { it.toWorldCupJson() }))
    put("recentResults", JSONArray(recentResults.map { it.toWorldCupJson() }))
    put("groups", JSONArray(groups.map { group -> JSONObject().apply {
        put("code", group.code)
        put("teams", JSONArray(group.teams.map { it.toJson() }))
    } }))
    put("bracket", bracket.toJson())
    put("library", JSONArray(library.map { item -> JSONObject().apply {
        put("id", item.id)
        put("title", item.title)
        put("category", item.category)
        put("body", item.body)
        put("readMinutes", item.readMinutes)
        putNullable("url", item.url)
        putNullable("imageUrl", item.imageUrl)
    } }))
    put("randomFact", JSONObject().apply {
        put("title", randomFact.title)
        put("body", randomFact.body)
    })
}

private fun ScheduleMatch.toWorldCupJson() = JSONObject().apply {
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
    putNullable("venue", venue?.let { JSONObject().apply {
        putNullable("name", it.name)
        putNullable("city", it.city)
    } })
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

private fun StandingTeamInfo.toJson() = JSONObject().apply {
    putNullable("rank", rank)
    put("teamId", teamId)
    put("name", name)
    putNullable("abbreviation", abbreviation)
    putNullable("logo", logo)
    putNullable("group", group)
    putNullable("stage", stage)
    putNullable("played", played)
    putNullable("wins", wins)
    putNullable("draws", draws)
    putNullable("losses", losses)
    putNullable("goalsFor", goalsFor)
    putNullable("goalsAgainst", goalsAgainst)
    putNullable("goalDifference", goalDifference)
    putNullable("points", points)
}

private fun WorldCupBracketMatchInfo.toJson() = JSONObject().apply {
    put("eventId", eventId)
    put("round", round)
    put("slotIndex", slotIndex)
    putNullable("status", status)
    putNullable("homeTeam", homeTeam)
    putNullable("awayTeam", awayTeam)
    putNullable("homeLogo", homeTeamLogo)
    putNullable("awayLogo", awayTeamLogo)
    putNullable("homeScore", homeScore)
    putNullable("awayScore", awayScore)
    putNullable("winnerTeamId", winnerTeamId)
    putNullable("kickoff", kickoff)
    putNullable("nextMatchSlot", nextMatchSlot?.let { next -> JSONObject().apply {
        put("round", next.round)
        put("slotIndex", next.slotIndex)
        put("teamPosition", next.teamPosition)
    } })
}

private fun WorldCupBracketInfo.toJson() = JSONObject().apply {
    put("tournament", tournament)
    put("bracketType", bracketType)
    put("rounds", JSONObject().apply {
        this@toJson.rounds.forEach { (round, matches) -> put(round, JSONArray(matches.map { it.toJson() })) }
    })
}

private fun JSONObject.toWorldCupBootstrapInfo() = WorldCupBootstrapInfo(
    tournament = getJSONObject("tournament").run {
        WorldCupTournamentInfo(
            id = getString("id"),
            name = getString("name"),
            stage = getString("stage"),
            hostCities = optInt("hostCities", 16),
            daysToFinal = nullableInt("daysToFinal")
        )
    },
    liveMatches = optJSONArray("liveMatches").toMatches(),
    todayMatches = optJSONArray("todayMatches").toMatches(),
    upcomingMatches = optJSONArray("upcomingMatches").toMatches(),
    recentResults = optJSONArray("recentResults").toMatches(),
    groups = buildList {
        val source = optJSONArray("groups")
        if (source != null) for (index in 0 until source.length()) source.getJSONObject(index).run {
            add(WorldCupGroupInfo(getString("code"), optJSONArray("teams").toStandingTeams()))
        }
    },
    bracket = toCachedBracket(),
    library = buildList {
        val source = optJSONArray("library")
        if (source != null) for (index in 0 until source.length()) source.getJSONObject(index).run {
            add(WorldCupLibraryItemInfo(
                id = getString("id"),
                title = getString("title"),
                category = getString("category"),
                body = getString("body"),
                readMinutes = optInt("readMinutes", 4),
                url = nullableString("url"),
                imageUrl = nullableString("imageUrl")
            ))
        }
    },
    randomFact = getJSONObject("randomFact").run { WorldCupFactInfo(getString("title"), getString("body")) }
)

private fun JSONArray?.toMatches(): List<ScheduleMatch> = buildList {
    if (this@toMatches != null) for (index in 0 until length()) getJSONObject(index).run {
        add(ScheduleMatch(
            matchId = getString("matchId"), league = getString("league"),
            name = nullableString("name"), shortName = nullableString("shortName"),
            status = nullableString("status"), statusDescription = nullableString("statusDescription"),
            state = nullableString("state"), kickoff = nullableString("kickoff"),
            homeTeam = optJSONObject("homeTeam")?.toTeam(), awayTeam = optJSONObject("awayTeam")?.toTeam(),
            venue = optJSONObject("venue")?.let { MatchVenueInfo(it.nullableString("name"), it.nullableString("city")) },
            detailApi = getString("detailApi")
        ))
    }
}

private fun JSONObject.toTeam() = MatchTeamInfo(
    id = getString("id"), name = getString("name"), shortName = nullableString("shortName"),
    abbreviation = nullableString("abbreviation"), logo = nullableString("logo"), score = nullableInt("score")
)

private fun JSONArray?.toStandingTeams(): List<StandingTeamInfo> = buildList {
    if (this@toStandingTeams != null) for (index in 0 until length()) getJSONObject(index).run {
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

private fun JSONObject.toCachedBracket(): WorldCupBracketInfo {
    optJSONObject("bracket")?.let { bracket ->
        val source = bracket.getJSONObject("rounds")
        return WorldCupBracketInfo(
            tournament = bracket.optString("tournament", "FIFA World Cup"),
            bracketType = bracket.optString("bracketType", "32_TEAM_KNOCKOUT"),
            rounds = listOf("R32", "R16", "QF", "SF", "FINAL").associateWith { code ->
                source.optJSONArray(code).toBracketMatches(code)
            }
        )
    }

    val migrated = mutableMapOf<String, List<WorldCupBracketMatchInfo>>()
    val legacy = optJSONArray("bracket")
    if (legacy != null) for (index in 0 until legacy.length()) legacy.getJSONObject(index).run {
        val code = legacyRoundCode(getString("round")) ?: return@run
        migrated[code] = optJSONArray("matches").toBracketMatches(code)
    }
    return WorldCupBracketInfo(
        tournament = "FIFA World Cup",
        bracketType = "32_TEAM_KNOCKOUT",
        rounds = listOf("R32", "R16", "QF", "SF", "FINAL").associateWith { migrated[it].orEmpty() }
    )
}

private fun JSONArray?.toBracketMatches(roundCode: String): List<WorldCupBracketMatchInfo> = buildList {
    if (this@toBracketMatches != null) for (index in 0 until length()) getJSONObject(index).run {
        add(WorldCupBracketMatchInfo(
            eventId = getString("eventId"), round = optString("round", roundCode), slotIndex = optInt("slotIndex", index),
            status = nullableString("status"), homeTeam = nullableString("homeTeam"), awayTeam = nullableString("awayTeam"),
            homeTeamLogo = nullableString("homeLogo") ?: nullableString("homeTeamLogo"),
            awayTeamLogo = nullableString("awayLogo") ?: nullableString("awayTeamLogo"),
            homeScore = nullableInt("homeScore"), awayScore = nullableInt("awayScore"),
            winnerTeamId = nullableString("winnerTeamId"), kickoff = nullableString("kickoff"),
            nextMatchSlot = optJSONObject("nextMatchSlot")?.run {
                WorldCupNextMatchSlotInfo(getString("round"), getInt("slotIndex"), getString("teamPosition"))
            }
        ))
    }
}

private fun legacyRoundCode(value: String): String? = when (value.lowercase()) {
    "round of 32", "r32" -> "R32"
    "round of 16", "r16" -> "R16"
    "quarterfinals", "quarter finals", "qf" -> "QF"
    "semifinals", "semi finals", "sf" -> "SF"
    "final", "finals" -> "FINAL"
    else -> null
}

private fun JSONObject.putNullable(key: String, value: Any?) {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
}

private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else optString(key).ifBlank { null }

private fun JSONObject.nullableInt(key: String): Int? = if (isNull(key)) null else optInt(key)
