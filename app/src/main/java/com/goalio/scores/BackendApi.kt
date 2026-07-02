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
import com.goalio.scores.ui.theme.GoalioColors

data class BackendProfile(
    val userId: String,
    val name: String,
    val username: String,
    val favoriteTeamIds: List<String>,
    val favoritePlayerIds: List<String>,
    val favoriteTeams: List<String>,
    val favoritePlayers: List<String>,
    val onboardingCompleted: Boolean,
    val profileCompleted: Boolean,
    val createdAt: String? = null
)

data class BackendHome(val greeting: String, val profile: BackendProfile)
data class QuizQuestionInfo(val id: String, val category: String, val prompt: String, val options: List<String>, val timeLimitSeconds: Int)
data class QuizSessionInfo(val sessionId: String, val questions: List<QuizQuestionInfo>, val currentQuestion: Int, val questionStartedAt: String)
data class QuizAnswerInfo(val correct: Boolean, val timedOut: Boolean, val correctAnswerIndex: Int, val explanation: String, val xpDelta: Int, val totalXp: Int, val currentQuestion: Int, val completed: Boolean)
data class QuizLeaderInfo(val rank: Int, val username: String, val xp: Int, val isMe: Boolean)
data class QuizLeaderboardInfo(val entries: List<QuizLeaderInfo>, val me: QuizLeaderInfo?)

data class BackendPage<T>(val items: List<T>, val nextCursor: String?)

data class MatchTeamInfo(
    val id: String,
    val name: String,
    val shortName: String?,
    val abbreviation: String?,
    val logo: String?,
    val score: Int?
)

data class MatchVenueInfo(val name: String?, val city: String?)

data class MatchOfficialInfo(val name: String?, val role: String?)

data class MatchWeatherInfo(val displayValue: String?, val temperature: String?, val condition: String?)

data class ScheduleMatch(
    val matchId: String,
    val league: String,
    val name: String?,
    val shortName: String?,
    val status: String?,
    val statusDescription: String?,
    val state: String?,
    val kickoff: String?,
    val homeTeam: MatchTeamInfo?,
    val awayTeam: MatchTeamInfo?,
    val venue: MatchVenueInfo?,
    val detailApi: String
)

data class MatchSchedule(val league: String, val date: String?, val matches: List<ScheduleMatch>)

data class StandingTeamInfo(
    val rank: Int?,
    val teamId: String,
    val name: String,
    val abbreviation: String?,
    val logo: String?,
    val group: String?,
    val stage: String?,
    val played: Int?,
    val wins: Int?,
    val draws: Int?,
    val losses: Int?,
    val goalsFor: Int?,
    val goalsAgainst: Int?,
    val goalDifference: Int?,
    val points: Int?
)

data class LeagueStandings(
    val league: String,
    val season: Int?,
    val groups: List<String>,
    val teams: List<StandingTeamInfo>
)

data class WorldCupTournamentInfo(
    val id: String,
    val name: String,
    val stage: String,
    val hostCities: Int,
    val daysToFinal: Int?
)

data class WorldCupGroupInfo(val code: String, val teams: List<StandingTeamInfo>)

data class WorldCupBracketMatchInfo(
    val eventId: String,
    val round: String,
    val slotIndex: Int,
    val status: String?,
    val homeTeam: String?,
    val awayTeam: String?,
    val homeTeamLogo: String?,
    val awayTeamLogo: String?,
    val homeScore: Int?,
    val awayScore: Int?,
    val winnerTeamId: String?,
    val kickoff: String?,
    val nextMatchSlot: WorldCupNextMatchSlotInfo?
)

data class WorldCupNextMatchSlotInfo(val round: String, val slotIndex: Int, val teamPosition: String)

data class WorldCupBracketInfo(
    val tournament: String,
    val bracketType: String,
    val rounds: Map<String, List<WorldCupBracketMatchInfo>>
)

data class WorldCupLibraryItemInfo(
    val id: String,
    val title: String,
    val category: String,
    val body: String,
    val readMinutes: Int,
    val url: String? = null,
    val imageUrl: String? = null
)

data class WorldCupFactInfo(val title: String, val body: String)

data class WorldCupBootstrapInfo(
    val tournament: WorldCupTournamentInfo,
    val liveMatches: List<ScheduleMatch>,
    val todayMatches: List<ScheduleMatch>,
    val upcomingMatches: List<ScheduleMatch>,
    val recentResults: List<ScheduleMatch>,
    val groups: List<WorldCupGroupInfo>,
    val bracket: WorldCupBracketInfo,
    val library: List<WorldCupLibraryItemInfo>,
    val randomFact: WorldCupFactInfo
)

data class MatchStat(val name: String?, val label: String?, val value: String?)

data class TeamStatsBlock(val teamId: String?, val stats: List<MatchStat>)

data class MatchLeaderPlayer(
    val id: String?,
    val name: String?,
    val position: String?,
    val jersey: String?,
    val espnUrl: String?,
    val mainStat: String?,
    val stats: List<MatchStat>
)

data class MatchLeaderGroup(val category: String?, val players: List<MatchLeaderPlayer>)

data class LineupPlayerInfo(
    val id: String?,
    val name: String,
    val position: String?,
    val jersey: String?,
    val starter: Boolean,
    val captain: Boolean,
    val substitute: Boolean,
    val formationPlace: String?
)

data class TeamLineupInfo(
    val teamId: String?,
    val teamName: String?,
    val formation: String?,
    val coach: String?,
    val starters: List<LineupPlayerInfo>,
    val substitutes: List<LineupPlayerInfo>
)

data class LineupManagerInfo(val name: String, val photo: String?)

data class PitchLineupPlayerInfo(
    val id: String?, val name: String, val number: Int?, val position: String?, val role: String?,
    val photo: String?, val captain: Boolean, val x: Float?, val y: Float?
)

data class UnavailablePlayerInfo(val name: String, val reason: String)

data class NormalizedTeamLineupInfo(
    val teamId: String?, val teamName: String?, val teamLogo: String?, val formation: String?,
    val manager: LineupManagerInfo?, val startingXI: List<PitchLineupPlayerInfo>,
    val bench: List<PitchLineupPlayerInfo>, val unavailable: List<UnavailablePlayerInfo>
)

data class MatchLineupInfo(
    val eventId: String, val status: String, val source: String, val formationStatus: String,
    val lastUpdated: String, val nextRefreshAt: String?, val kickoff: String?, val isStale: Boolean,
    val home: NormalizedTeamLineupInfo, val away: NormalizedTeamLineupInfo
)

data class MatchHighlightInfo(
    val status: String, val provider: String?, val url: String?, val embedUrl: String?,
    val thumbnailUrl: String?, val publishedAt: String?
)

data class MatchOfficialMediaInfo(val highlightsPageUrl: String?, val matchUrl: String?)
data class MatchMediaInfo(
    val matchId: String, val highlight: MatchHighlightInfo, val official: MatchOfficialMediaInfo,
    val source: String, val lastCheckedAt: String
)

data class WatchProviderInfo(
    val name: String, val type: String, val url: String, val appPackage: String?,
    val isFree: Boolean?, val note: String?
)

data class MatchWatchInfo(
    val matchId: String, val country: String, val status: String,
    val providers: List<WatchProviderInfo>, val fallback: WatchProviderInfo,
    val message: String?, val disclaimer: String
)

data class MatchTimelineEvent(
    val minute: String?,
    val type: String?,
    val text: String?,
    val team: String?
)

data class WinProbabilityInfo(
    val homeWinPercentage: Int,
    val awayWinPercentage: Int,
    val drawPercentage: Int?
)

data class MatchDetail(
    val matchId: String,
    val league: String,
    val status: String?,
    val statusDescription: String?,
    val kickoff: String?,
    val homeTeam: MatchTeamInfo?,
    val awayTeam: MatchTeamInfo?,
    val venue: MatchVenueInfo?,
    val officials: List<MatchOfficialInfo>,
    val weather: MatchWeatherInfo?,
    val teamStats: List<TeamStatsBlock>,
    val playerLeaders: List<MatchLeaderGroup>,
    val lineups: List<TeamLineupInfo>,
    val events: List<MatchTimelineEvent>,
    val summary: String?,
    val winProbability: WinProbabilityInfo?
)

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

    suspend fun signInExistingProfile(name: String, username: String): BackendProfile = withContext(Dispatchers.IO) {
        val token = request("POST", "/api/v1/auth/profile-login", JSONObject().put("name", name).put("username", username)) { it.getString("customToken") }
        Tasks.await(FirebaseAuth.getInstance().signInWithCustomToken(token))
        getProfile()
    }

    suspend fun getHome(): BackendHome = request("GET", "/api/v1/home") { json ->
        BackendHome(json.getString("greeting"), parseProfile(json.getJSONObject("profile")))
    }

    suspend fun isUsernameAvailable(username: String): Boolean = request(
        "GET", "/api/v1/users/username/availability?username=${encode(username)}"
    ) { it.getBoolean("available") }

    suspend fun profileIdentityMatches(name: String, username: String): Boolean = request(
        "POST", "/api/v1/users/profile/identity-match",
        JSONObject().put("name", name).put("username", username)
    ) { it.getBoolean("matched") }

    suspend fun getTeams(limit: Int = 6, cursor: String? = null, competitionId: Int? = null): BackendPage<FavoriteTeam> = request(
        "GET", pagedPath("/api/v1/football/teams", limit, cursor) + (competitionId?.let { "&competitionId=$it" } ?: "")
    ) { json ->
        BackendPage(
            items = json.getJSONArray("items").toTeamList(),
            nextCursor = json.nullableString("nextCursor")
        )
    }

    suspend fun getPlayers(limit: Int = 6, cursor: String? = null, competitionId: Int? = null): BackendPage<FavoritePlayer> = request(
        "GET", pagedPath("/api/v1/football/players", limit, cursor) + (competitionId?.let { "&competitionId=$it" } ?: "")
    ) { json ->
        BackendPage(
            items = json.getJSONArray("items").toPlayerList(),
            nextCursor = json.nullableString("nextCursor")
        )
    }

    suspend fun searchTeams(query: String, limit: Int = 6, cursor: String? = null): BackendPage<FavoriteTeam> = request(
        "GET", searchPath("/api/v1/football/teams/search", query, limit, cursor)
    ) { json ->
        BackendPage(
            items = json.getJSONArray("items").toTeamList(),
            nextCursor = json.nullableString("nextCursor")
        )
    }

    suspend fun searchPlayers(query: String, limit: Int = 6, cursor: String? = null): BackendPage<FavoritePlayer> = request(
        "GET", searchPath("/api/v1/football/players/search", query, limit, cursor)
    ) { json ->
        BackendPage(
            items = json.getJSONArray("items").toPlayerList(),
            nextCursor = json.nullableString("nextCursor")
        )
    }

    suspend fun getSchedule(league: String, date: String): MatchSchedule = request(
        "GET", "/api/v1/matches/${encodePath(league)}/schedule?date=${encode(date)}"
    ) { json -> json.toMatchSchedule() }

    suspend fun getScheduleRange(league: String, from: String, to: String): MatchSchedule = request(
        "GET", "/api/v1/matches/${encodePath(league)}/schedule?from=${encode(from)}&to=${encode(to)}"
    ) { json -> json.toMatchSchedule() }

    suspend fun getMatchDetail(league: String, eventId: String): MatchDetail = request(
        "GET", "/api/v1/matches/${encodePath(league)}/${encodePath(eventId)}/detail"
    ) { json -> json.toMatchDetail() }

    suspend fun getMatchLineup(league: String, eventId: String): MatchLineupInfo = request(
        "GET", "/api/v1/matches/${encodePath(eventId)}/lineup?league=${encode(league)}"
    ) { json -> json.toMatchLineup() }

    suspend fun getMatchMedia(eventId: String): MatchMediaInfo = request(
        "GET", "/api/v1/matches/${encodePath(eventId)}/media"
    ) { json -> json.toMatchMedia() }

    suspend fun getMatchWatch(eventId: String, country: String): MatchWatchInfo = request(
        "GET", "/api/v1/matches/${encodePath(eventId)}/watch?country=${encode(country)}"
    ) { json -> json.toMatchWatch() }

    suspend fun getStandings(league: String, season: Int? = null): LeagueStandings = request(
        "GET",
        buildString {
            append("/api/v1/matches/")
            append(encodePath(league))
            append("/standings")
            if (season != null) {
                append("?season=")
                append(season)
            }
        }
    ) { json -> json.toLeagueStandings() }

    suspend fun getWorldCupBootstrap(): WorldCupBootstrapInfo = request(
        "GET", "/api/v1/worldcup/bootstrap"
    ) { json -> json.toWorldCupBootstrap() }

    suspend fun startQuiz(): QuizSessionInfo = request("POST", "/api/v1/quiz/sessions", JSONObject()) { json ->
        QuizSessionInfo(json.getString("sessionId"), buildList {
            val items = json.getJSONArray("questions")
            for (i in 0 until items.length()) items.getJSONObject(i).run {
                add(QuizQuestionInfo(getString("id"), getString("category"), getString("prompt"), optJSONArray("options").toStrings(), optInt("timeLimitSeconds", 15)))
            }
        }, json.optInt("currentQuestion"), json.getString("questionStartedAt"))
    }

    suspend fun answerQuiz(sessionId: String, questionId: String, answerIndex: Int): QuizAnswerInfo = request(
        "POST", "/api/v1/quiz/sessions/${encodePath(sessionId)}/answer", JSONObject().put("questionId", questionId).put("answerIndex", answerIndex)
    ) { json -> QuizAnswerInfo(json.getBoolean("correct"), json.optBoolean("timedOut"), json.getInt("correctAnswerIndex"), json.getString("explanation"), json.getInt("xpDelta"), json.getInt("totalXp"), json.getInt("currentQuestion"), json.getBoolean("completed")) }

    suspend fun getQuizLeaderboard(): QuizLeaderboardInfo = request("GET", "/api/v1/quiz/leaderboard") { json ->
        fun parse(item: JSONObject) = QuizLeaderInfo(item.getInt("rank"), item.getString("username"), item.getInt("xp"), !item.isNull("userId"))
        val entries = buildList { val items = json.getJSONArray("entries"); for (i in 0 until items.length()) add(parse(items.getJSONObject(i))) }
        QuizLeaderboardInfo(entries, json.optJSONObject("me")?.let(::parse))
    }

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
        profileCompleted = json.optBoolean("profileCompleted", true),
        createdAt = json.nullableString("createdAt")
    )

    private fun JSONArray?.toStrings(): List<String> = buildList {
        if (this@toStrings != null) for (index in 0 until length()) add(getString(index))
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else optString(key).ifBlank { null }

    private fun JSONArray.toTeamList() = buildList {
        for (index in 0 until length()) getJSONObject(index).run {
            val id = getString("id")
            add(FavoriteTeam(
                id = id,
                name = getString("name"),
                shortName = getString("shortName"),
                primaryColor = resultColor(id),
                imageUrl = optString("imageUrl").ifBlank { null },
                competitionIds = optJSONArray("competitionIds").toInts()
                    .ifEmpty { setOfNotNull(competitionIdFromTeamId(id)) }
            ))
        }
    }

    private fun JSONArray.toPlayerList() = buildList {
        for (index in 0 until length()) getJSONObject(index).run {
            val name = getString("name")
            val id = getString("id")
            val initials = name.split(' ').mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
            add(FavoritePlayer(
                id = id,
                name = name,
                team = getString("team"),
                initials = initials,
                accent = resultColor(id),
                imageUrl = optString("imageUrl").ifBlank { null },
                competitionIds = optJSONArray("competitionIds").toInts()
            ))
        }
    }

    private fun JSONArray?.toInts(): Set<Int> = buildSet {
        if (this@toInts != null) for (index in 0 until length()) add(getInt(index))
    }

    private fun JSONObject.toMatchSchedule() = MatchSchedule(
        league = getString("league"),
        date = nullableString("date"),
        matches = getJSONArray("matches").toScheduleMatches()
    )

    private fun JSONObject.toLeagueStandings() = LeagueStandings(
        league = getString("league"),
        season = if (isNull("season")) null else optInt("season"),
        groups = optJSONArray("groups").toStrings(),
        teams = optJSONArray("teams").toStandingTeams()
    )

    private fun JSONObject.toWorldCupBootstrap() = WorldCupBootstrapInfo(
        tournament = getJSONObject("tournament").run {
            WorldCupTournamentInfo(
                id = getString("id"),
                name = getString("name"),
                stage = getString("stage"),
                hostCities = optInt("hostCities", 16),
                daysToFinal = if (isNull("daysToFinal")) null else optInt("daysToFinal")
            )
        },
        liveMatches = optJSONArray("liveMatches").toScheduleMatches(),
        todayMatches = optJSONArray("todayMatches").toScheduleMatches(),
        upcomingMatches = optJSONArray("upcomingMatches").toScheduleMatches(),
        recentResults = optJSONArray("recentResults").toScheduleMatches(),
        groups = optJSONArray("groups").toWorldCupGroups(),
        bracket = getJSONObject("bracket").toWorldCupBracket(),
        library = optJSONArray("library").toWorldCupLibrary(),
        randomFact = getJSONObject("randomFact").run { WorldCupFactInfo(getString("title"), getString("body")) }
    )

    private fun JSONArray?.toStandingTeams(): List<StandingTeamInfo> = buildList {
        if (this@toStandingTeams != null) for (index in 0 until length()) getJSONObject(index).run {
            add(StandingTeamInfo(
                rank = if (isNull("rank")) null else optInt("rank"),
                teamId = getString("teamId"),
                name = getString("name"),
                abbreviation = nullableString("abbreviation"),
                logo = nullableString("logo"),
                group = nullableString("group"),
                stage = nullableString("stage"),
                played = if (isNull("played")) null else optInt("played"),
                wins = if (isNull("wins")) null else optInt("wins"),
                draws = if (isNull("draws")) null else optInt("draws"),
                losses = if (isNull("losses")) null else optInt("losses"),
                goalsFor = if (isNull("goalsFor")) null else optInt("goalsFor"),
                goalsAgainst = if (isNull("goalsAgainst")) null else optInt("goalsAgainst"),
                goalDifference = if (isNull("goalDifference")) null else optInt("goalDifference"),
                points = if (isNull("points")) null else optInt("points")
            ))
        }
    }

    private fun JSONArray?.toWorldCupGroups(): List<WorldCupGroupInfo> = buildList {
        if (this@toWorldCupGroups != null) for (index in 0 until length()) getJSONObject(index).run {
            add(WorldCupGroupInfo(getString("code"), optJSONArray("teams").toStandingTeams()))
        }
    }

    private fun JSONObject.toWorldCupBracket() = WorldCupBracketInfo(
        tournament = getString("tournament"),
        bracketType = getString("bracketType"),
        rounds = getJSONObject("rounds").let { source ->
            listOf("R32", "R16", "QF", "SF", "FINAL").associateWith { code ->
                source.optJSONArray(code).toWorldCupBracketMatches()
            }
        }
    )

    private fun JSONArray?.toWorldCupBracketMatches(): List<WorldCupBracketMatchInfo> = buildList {
        if (this@toWorldCupBracketMatches != null) for (index in 0 until length()) getJSONObject(index).run {
            add(WorldCupBracketMatchInfo(
                eventId = getString("eventId"),
                round = getString("round"),
                slotIndex = getInt("slotIndex"),
                status = nullableString("status"),
                homeTeam = nullableString("homeTeam"),
                awayTeam = nullableString("awayTeam"),
                homeTeamLogo = nullableString("homeLogo"),
                awayTeamLogo = nullableString("awayLogo"),
                homeScore = if (isNull("homeScore")) null else optInt("homeScore"),
                awayScore = if (isNull("awayScore")) null else optInt("awayScore"),
                winnerTeamId = nullableString("winnerTeamId"),
                kickoff = nullableString("kickoff"),
                nextMatchSlot = optJSONObject("nextMatchSlot")?.run {
                    WorldCupNextMatchSlotInfo(getString("round"), getInt("slotIndex"), getString("teamPosition"))
                }
            ))
        }
    }

    private fun JSONArray?.toWorldCupLibrary(): List<WorldCupLibraryItemInfo> = buildList {
        if (this@toWorldCupLibrary != null) for (index in 0 until length()) getJSONObject(index).run {
            add(WorldCupLibraryItemInfo(
                id = getString("id"),
                title = getString("title"),
                category = getString("category"),
                body = getString("body"),
                readMinutes = optInt("readMinutes", 4)
            ))
        }
    }

    private fun JSONArray?.toScheduleMatches() = buildList {
        if (this@toScheduleMatches != null) for (index in 0 until length()) getJSONObject(index).run {
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

    private fun JSONObject.toMatchLineup() = MatchLineupInfo(
        eventId = getString("eventId"), status = getString("status"), source = getString("source"),
        formationStatus = getString("formationStatus"), lastUpdated = getString("lastUpdated"),
        nextRefreshAt = nullableString("nextRefreshAt"), kickoff = nullableString("kickoff"),
        isStale = optBoolean("isStale", false),
        home = getJSONObject("home").toNormalizedTeamLineup(),
        away = getJSONObject("away").toNormalizedTeamLineup()
    )

    private fun JSONObject.toMatchMedia() = MatchMediaInfo(
        matchId = getString("matchId"),
        highlight = getJSONObject("highlight").run {
            MatchHighlightInfo(getString("status"), nullableString("provider"), nullableString("url"), nullableString("embedUrl"), nullableString("thumbnailUrl"), nullableString("publishedAt"))
        },
        official = getJSONObject("official").run {
            MatchOfficialMediaInfo(nullableString("highlightsPageUrl"), nullableString("matchUrl"))
        },
        source = getString("source"), lastCheckedAt = getString("lastCheckedAt")
    )

    private fun JSONObject.toMatchWatch() = MatchWatchInfo(
        matchId = getString("matchId"), country = getString("country"), status = getString("status"),
        providers = optJSONArray("providers").toWatchProviders(),
        fallback = getJSONObject("fallback").toWatchProvider(),
        message = nullableString("message"), disclaimer = getString("disclaimer")
    )

    private fun JSONArray?.toWatchProviders(): List<WatchProviderInfo> = buildList {
        if (this@toWatchProviders != null) for (index in 0 until length()) add(getJSONObject(index).toWatchProvider())
    }

    private fun JSONObject.toWatchProvider() = WatchProviderInfo(
        name = getString("name"), type = getString("type"), url = getString("url"),
        appPackage = nullableString("appPackage"),
        isFree = if (isNull("isFree")) null else optBoolean("isFree"), note = nullableString("note")
    )

    private fun JSONObject.toNormalizedTeamLineup() = NormalizedTeamLineupInfo(
        teamId = nullableString("teamId"), teamName = nullableString("teamName"),
        teamLogo = nullableString("teamLogo"), formation = nullableString("formation"),
        manager = optJSONObject("manager")?.run { LineupManagerInfo(getString("name"), nullableString("photo")) },
        startingXI = optJSONArray("startingXI").toPitchPlayers(),
        bench = optJSONArray("bench").toPitchPlayers(),
        unavailable = optJSONArray("unavailable").toUnavailablePlayers()
    )

    private fun JSONArray?.toPitchPlayers(): List<PitchLineupPlayerInfo> = buildList {
        if (this@toPitchPlayers != null) for (index in 0 until length()) getJSONObject(index).run {
            add(PitchLineupPlayerInfo(
                id = nullableString("id"), name = getString("name"),
                number = if (isNull("number")) null else optInt("number"),
                position = nullableString("position"), role = nullableString("role"), photo = nullableString("photo"),
                captain = optBoolean("captain", false),
                x = if (isNull("x")) null else optDouble("x").toFloat(),
                y = if (isNull("y")) null else optDouble("y").toFloat()
            ))
        }
    }

    private fun JSONArray?.toUnavailablePlayers(): List<UnavailablePlayerInfo> = buildList {
        if (this@toUnavailablePlayers != null) for (index in 0 until length()) getJSONObject(index).run {
            add(UnavailablePlayerInfo(getString("name"), getString("reason")))
        }
    }

    private fun JSONObject.toWinProbabilityInfo() = WinProbabilityInfo(
        homeWinPercentage = optInt("homeWinPercentage", 50),
        awayWinPercentage = optInt("awayWinPercentage", 50),
        drawPercentage = if (isNull("drawPercentage")) null else optInt("drawPercentage")
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

    private fun competitionIdFromTeamId(id: String): Int? = when {
        "fifa.world" in id -> 1
        "eng.1" in id -> 39
        "esp.1" in id -> 140
        "ita.1" in id -> 135
        "ger.1" in id -> 78
        "fra.1" in id -> 61
        else -> null
    }

    private fun resultColor(id: String) = when {
        id.contains("live", ignoreCase = true) -> GoalioColors.Live
        id.contains("win", ignoreCase = true) -> GoalioColors.Accent
        else -> GoalioColors.Accent
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

    private fun searchPath(path: String, query: String, limit: Int, cursor: String?): String = buildString {
        append(path)
        append("?q=")
        append(encode(query))
        append("&limit=")
        append(limit.coerceIn(1, 20))
        if (!cursor.isNullOrBlank()) {
            append("&cursor=")
            append(encode(cursor))
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun encodePath(value: String) = value.split('.').joinToString(".") { encode(it) }
}

class BackendException(val statusCode: Int, message: String) : Exception(message)
