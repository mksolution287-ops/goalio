package com.goalio.scores

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.widget.TextView
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.text.Html
import android.text.method.LinkMovementMethod
import android.graphics.Color as AndroidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.goalio.scores.ui.theme.GoalioColors
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private data class LeagueFilter(val code: String?, val label: String)
private data class StatPair(val home: String, val away: String) {
    fun display(): String = "$home / $away"
}
private data class MomentStyle(val label: String, val icon: String, val color: Color)

private val LeagueFilters = listOf(
    LeagueFilter("fifa.world", "World Cup"),
    LeagueFilter("eng.1", "Premier League"),
    LeagueFilter("uefa.champions", "Champions League"),
    LeagueFilter("esp.1", "LaLiga"),
    LeagueFilter("ita.1", "Serie A"),
    LeagueFilter("ger.1", "Bundesliga"),
    LeagueFilter("fra.1", "Ligue 1"),
    LeagueFilter("uefa.europa", "Europa League")
)

@Composable
fun MatchScreen(
    onBack: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenWorldCup: () -> Unit,
    onOpenGames: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMatch: (ScheduleMatch) -> Unit
) {
    val context = LocalContext.current
    val metrics = rememberGoalioMetrics()
    val today = remember { LocalDate.now() }
    var selectedDate by rememberSaveable { mutableStateOf(today.toString()) }
    var selectedLeague by rememberSaveable { mutableStateOf("fifa.world") }
    var matches by remember { mutableStateOf(emptyList<ScheduleMatch>()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedDate) {
        MatchRepository.matchUpdates.collect { canonical ->
            val shared = canonical.values.filter { it.localKickoffDate()?.toString() == selectedDate }
            if (shared.isNotEmpty()) {
                matches = shared.sortedWith(compareBy<ScheduleMatch> { stateRank(it.state) }.thenBy { it.kickoff.orEmpty() })
            }
        }
    }

    LaunchedEffect(selectedDate) {
        val localDate = LocalDate.parse(selectedDate)
        val fetchFrom = localDate.minusDays(1).toString()
        val fetchTo = localDate.plusDays(1).toString()
        matches = MatchRepository.cachedFeed(context, fetchFrom, fetchTo).filter { it.localKickoffDate() == localDate }
        loading = matches.isEmpty()
        while (true) {
            errorMessage = null
            runCatching { MatchRepository.refreshFeed(context, fetchFrom, fetchTo) }
                .onSuccess { result ->
                    matches = result.matches.filter { it.localKickoffDate() == localDate }
                    if (result.scoreChanged && GoalioAppVisibility.isForeground) {
                        Toast.makeText(context, "Goal update received", Toast.LENGTH_SHORT).show()
                    }
                    GoalioMatchNotifier.notifyBackgroundEvents(context, result.notificationEvents)
                }
                .onFailure {
                    errorMessage = if (matches.isEmpty()) {
                        it.message ?: "Could not load matches. Check the backend connection and try again."
                    } else {
                        null
                    }
                }
            loading = false
            delay(MatchRepository.nextRefreshDelayMillis(matches))
        }
    }

    val selectedLocalDate = remember(selectedDate) { LocalDate.parse(selectedDate) }
    val filtered = matches.filter { it.league == selectedLeague }
    GoalioBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = metrics.horizontalPadding,
                end = metrics.horizontalPadding,
                top = metrics.dp(18),
                bottom = metrics.bottomBarPadding
            ),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(20))
        ) {
            item { GoalioTopBar(onBack = onBack, onSettings = onOpenSettings) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(metrics.dp(13))) {
                    items((-2..8).map { today.plusDays(it.toLong()) }) { day ->
                        DateCard(day, selected = day.toString() == selectedDate) {
                            selectedDate = day.toString()
                        }
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(metrics.dp(13))) {
                    items(LeagueFilters) { league ->
                        LeagueChip(league, selected = league.code == selectedLeague) {
                            selectedLeague = league.code.orEmpty()
                        }
                    }
                }
            }
            item {
                Crossfade(targetState = Triple(loading, errorMessage, filtered.isEmpty()), label = "match list") { state ->
                    when {
                        state.first -> MatchStateCard("Loading real match data...")
                        state.second != null -> MatchStateCard(state.second.orEmpty())
                        state.third -> MatchStateCard("No ${leagueLabel(selectedLeague)} matches on ${selectedLocalDate.format(DateTimeFormatter.ofPattern("dd MMM"))}.")
                        else -> Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(18))) {
                            filtered.forEach { match ->
                                FixtureCard(match, onOpenMatch)
                            }
                        }
                    }
                }
            }
        }
        GoalioBottomBar(Modifier.align(Alignment.BottomCenter), "Matches", onOpenHome, {}, onOpenWorldCup, onOpenGames)
    }
}

@Composable
fun MatchDetailScreen(
    league: String,
    matchId: String,
    initialMatch: ScheduleMatch?,
    onBack: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenMatches: () -> Unit,
    onOpenWorldCup: () -> Unit,
    onOpenGames: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val metrics = rememberGoalioMetrics()
    var detail by remember { mutableStateOf(MatchRepository.cachedDetail(context, league, matchId)) }
    var lineup by remember { mutableStateOf(LineupRepository.cached(context, league, matchId)) }
    var loading by remember { mutableStateOf(detail == null) }
    var lineupLoading by remember { mutableStateOf(lineup == null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lineupError by remember { mutableStateOf<String?>(null) }
    var media by remember { mutableStateOf<MatchMediaInfo?>(null) }
    var watch by remember { mutableStateOf<MatchWatchInfo?>(null) }
    var mediaLoading by remember { mutableStateOf(true) }
    var mediaError by remember { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf("Overview") }
    var previousScore by remember { mutableStateOf(detail?.scoreSignature() ?: initialMatch?.scoreSignature().orEmpty()) }

    LaunchedEffect(league, matchId) {
        MatchRepository.detailUpdates.collect { canonical ->
            canonical["$league:$matchId"]?.let { shared ->
                detail = shared
                previousScore = shared.scoreSignature()
            }
        }
    }

    LaunchedEffect(matchId) {
        val country = Locale.getDefault().country.takeIf { it.length == 2 } ?: "IN"
        while (true) {
            mediaLoading = media == null && watch == null
            val mediaResult = runCatching { GoalioBackendApi.getMatchMedia(matchId) }
            val watchResult = runCatching { GoalioBackendApi.getMatchWatch(matchId, country) }
            mediaResult.onSuccess { media = it }
            watchResult.onSuccess { watch = it }
            mediaError = if (mediaResult.isFailure && watchResult.isFailure) {
                mediaResult.exceptionOrNull()?.message ?: "Media information is unavailable."
            } else null
            mediaLoading = false
            delay(15 * 60 * 1000L)
        }
    }

    LaunchedEffect(league, matchId) {
        detail = MatchRepository.cachedDetail(context, league, matchId)
        loading = detail == null
        while (true) {
            errorMessage = null
            runCatching { MatchRepository.refreshDetail(context, league, matchId) }
                .onSuccess { fresh ->
                    val newScore = fresh.scoreSignature()
                    if (previousScore.isNotBlank() && previousScore != newScore) {
                        Toast.makeText(context, "Goal update received", Toast.LENGTH_SHORT).show()
                    }
                    previousScore = newScore
                    detail = fresh
                }
                .onFailure {
                    errorMessage = if (detail == null) {
                        it.message ?: "Could not load match detail. Check the backend connection and try again."
                    } else {
                        null
                    }
                }
            loading = false
            val isLive = detail?.statusState() == "in" || initialMatch?.state == "in"
            delay(
                if (isLive) 2 * 60 * 1000L else 15 * 60 * 1000L
            )
        }
    }

    LaunchedEffect(league, matchId) {
        lineup = LineupRepository.cached(context, league, matchId)
        lineupLoading = lineup == null
        while (true) {
            runCatching { LineupRepository.refresh(context, league, matchId) }
                .onSuccess {
                    lineup = it
                    lineupError = null
                }
                .onFailure {
                    if (lineup == null) lineupError = it.message ?: "Could not load lineups."
                }
            lineupLoading = false
            delay(LineupRepository.refreshDelayMillis(lineup))
        }
    }

    val shown = detail
    GoalioBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = metrics.horizontalPadding,
                end = metrics.horizontalPadding,
                top = metrics.dp(14),
                bottom = metrics.bottomBarPadding
            ),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(16))
        ) {
            item { GoalioTopBar(onBack = onBack, onSettings = onOpenSettings) }
            item {
                when {
                    shown != null -> DetailHeroCard(shown)
                    initialMatch != null -> ScheduleHeroCard(initialMatch)
                    loading -> MatchStateCard("Loading match detail...")
                    else -> MatchStateCard(errorMessage ?: "No detail found for this match.")
                }
            }
            if (shown != null) {
                item { DetailTabs(selectedTab) { selectedTab = it } }
                when (selectedTab) {
                    "Timeline" -> item { TimelineSection(shown.events, showTitle = true, keyMomentsOnly = false) }
                    "Stats" -> item { PerformanceMatrix(shown) }
                    "AI Insight" -> item { AiSummaryCard(shown.summary, shown) }
                    "Player Lineups" -> item { PlayerLineupsSection(lineup, lineupLoading, lineupError) }
                    else -> item { OverviewContent(shown) }
                }
                item { StreamHighlights(media, watch, mediaLoading, mediaError) }
            }
        }
        GoalioBottomBar(Modifier.align(Alignment.BottomCenter), "Matches", onOpenHome, onOpenMatches, onOpenWorldCup, onOpenGames)
    }
}

@Composable
private fun MatchTopBar(title: String, onBack: () -> Unit, large: Boolean) {
    val metrics = rememberGoalioMetrics()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BackGlyph(Modifier.size(metrics.dp(30)).clickable(onClick = onBack), Color.White)
        Spacer(Modifier.width(metrics.dp(18)))
        Text(
            title,
            color = Color.White,
            fontSize = metrics.sp(if (large) 31 else 26),
            fontWeight = FontWeight.Black,
            letterSpacing = if (large) 6.sp else 3.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(18)), verticalAlignment = Alignment.CenterVertically) {
            HeaderIcon("search")
            HeaderIcon("bell")
            HeaderIcon("gear")
        }
    }
}

@Composable
private fun DateCard(date: LocalDate, selected: Boolean, onClick: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(
        color = if (selected) Color(0xFF241000) else GoalioColors.Neutral,
        contentColor = GoalioColors.TextPrimary,
        border = BorderStroke(1.dp, if (selected) GoalioColors.Accent else GoalioColors.CardBorder),
        shape = RoundedCornerShape(metrics.dp(18)),
        modifier = Modifier.width(metrics.dp(100)).height(metrics.dp(88)).clickable(onClick = onClick)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).uppercase(), fontSize = metrics.sp(17), letterSpacing = 2.sp, color = if (selected) GoalioColors.Tertiary else GoalioColors.TextSecondary)
            Text(date.format(DateTimeFormatter.ofPattern("dd MMM")), fontSize = metrics.sp(19), fontWeight = FontWeight.Black)
            Text(date.year.toString(), fontSize = metrics.sp(12), color = GoalioColors.TextTertiary)
        }
    }
}

@Composable
private fun LeagueChip(league: LeagueFilter, selected: Boolean, onClick: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(
        color = if (selected) Color(0xFF241000) else GoalioColors.Neutral,
        contentColor = GoalioColors.TextPrimary,
        border = BorderStroke(1.dp, if (selected) GoalioColors.Accent else GoalioColors.Border),
        shape = RoundedCornerShape(metrics.dp(12)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(horizontal = metrics.dp(16), vertical = metrics.dp(12)), verticalAlignment = Alignment.CenterVertically) {
            if (league.code == "fifa.world") {
                Text("T", color = Color.White, fontSize = metrics.sp(13), fontWeight = FontWeight.Black)
                Spacer(Modifier.width(metrics.dp(9)))
            }
            Text(league.label, fontSize = metrics.sp(16), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FixtureCard(match: ScheduleMatch, onOpenMatch: (ScheduleMatch) -> Unit) {
    val metrics = rememberGoalioMetrics()
    val homeOdds = match.homeWinProbability()
    Surface(
        color = Color(0xFF1D1D1F),
        shape = RoundedCornerShape(metrics.dp(18)),
        border = BorderStroke(1.dp, Color(0xFF303036)),
        modifier = Modifier.fillMaxWidth().clickable { onOpenMatch(match) }
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(metrics.dp(16)), verticalAlignment = Alignment.CenterVertically) {
                Text("${match.leagueLabel()} • ${match.stageLabel()}", color = GoalioColors.TextPrimary, fontSize = metrics.sp(14), fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                DynamicMatchStatus(match, metrics)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF242426)))
            Row(Modifier.fillMaxWidth().padding(horizontal = metrics.dp(20), vertical = metrics.dp(28)), verticalAlignment = Alignment.CenterVertically) {
                MatchTeamBlock(match.homeTeam, Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(metrics.dp(116))) {
                    Text(scoreLine(match), color = GoalioColors.TextPrimary, fontSize = metrics.sp(42), fontWeight = FontWeight.Light, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(metrics.dp(8)))
                    Text("MATCH ODDS", color = Color(0xFF9A9288), fontSize = metrics.sp(11), fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                }
                MatchTeamBlock(match.awayTeam, Modifier.weight(1f))
            }
            Column(Modifier.padding(horizontal = metrics.dp(20))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("WIN ${homeOdds.toInt()}%", color = Color(0xFFC1B6A5), fontSize = metrics.sp(13), fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    Text("${(100f - homeOdds).toInt()}% WIN", color = Color(0xFFC1B6A5), fontSize = metrics.sp(13), fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(metrics.dp(9)))
                Row(Modifier.fillMaxWidth().height(metrics.dp(9)).clip(RoundedCornerShape(50))) {
                    Box(Modifier.weight(homeOdds).fillMaxSize().background(Color.White))
                    Box(Modifier.weight(100f - homeOdds).fillMaxSize().background(Color(0xFFE53015)))
                }
            }
            Box(Modifier.fillMaxWidth().padding(metrics.dp(20))) {
                Box(Modifier.fillMaxWidth().height(1.dp).align(Alignment.TopCenter).background(Color(0xFF262629)))
                Text("♡", color = GoalioColors.Accent, fontSize = metrics.sp(29), modifier = Modifier.align(Alignment.BottomStart).padding(top = metrics.dp(13)))
                Text(">", color = GoalioColors.Accent, fontSize = metrics.sp(30), modifier = Modifier.align(Alignment.BottomEnd).padding(top = metrics.dp(13)))
            }
        }
    }
}

@Composable
private fun MatchTeamBlock(team: MatchTeamInfo?, modifier: Modifier = Modifier) {
    val metrics = rememberGoalioMetrics()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamLogo(team, size = metrics.dp(76), logoSize = metrics.dp(58))
        Spacer(Modifier.height(metrics.dp(12)))
        Text(team?.shortName ?: team?.name ?: "TBD", color = GoalioColors.TextPrimary, fontSize = metrics.sp(18), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DetailHeroCard(detail: MatchDetail) {
    val metrics = rememberGoalioMetrics()
    Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(metrics.dp(12)), border = BorderStroke(1.dp, Color(0xFF24272A))) {
        Column(Modifier.fillMaxWidth().padding(metrics.dp(20)), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DetailTopTeam(detail.homeTeam, Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(metrics.dp(130))) {
                    DynamicDetailStatus(detail, metrics)
                    Spacer(Modifier.height(metrics.dp(6)))
                    Text(scoreLine(detail.homeTeam, detail.awayTeam), color = GoalioColors.TextPrimary, fontSize = metrics.sp(42), fontWeight = FontWeight.Black)
                }
                DetailTopTeam(detail.awayTeam, Modifier.weight(1f))
            }
            Spacer(Modifier.height(metrics.dp(22)))
            Text(detail.venueText().ifBlank { detail.leagueLabel() }, color = GoalioColors.TextSecondary, fontSize = metrics.sp(13), fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(metrics.dp(28)))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("WIN PROBABILITY", color = GoalioColors.TextSecondary, fontSize = metrics.sp(11), fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text("${detail.homeWinProbability().toInt()}% ${detail.homeTeam?.abbreviation ?: "HOME"}", color = GoalioColors.TextSecondary, fontSize = metrics.sp(11), fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(metrics.dp(9)))
            Row(Modifier.fillMaxWidth().height(metrics.dp(7)).clip(RoundedCornerShape(50))) {
                Box(Modifier.weight(detail.homeWinProbability()).fillMaxSize().background(GoalioColors.Accent))
                Box(Modifier.weight(100f - detail.homeWinProbability()).fillMaxSize().background(Color(0xFF333536)))
            }
        }
    }
}

@Composable
private fun ScheduleHeroCard(match: ScheduleMatch) {
    val detail = MatchDetail(
        matchId = match.matchId,
        league = match.league,
        status = match.status,
        statusDescription = match.statusDescription,
        kickoff = match.kickoff,
        homeTeam = match.homeTeam,
        awayTeam = match.awayTeam,
        venue = match.venue,
        officials = emptyList(),
        weather = null,
        teamStats = emptyList(),
        playerLeaders = emptyList(),
        lineups = emptyList(),
        events = emptyList(),
        summary = null,
        winProbability = null
    )
    DetailHeroCard(detail)
}

@Composable
private fun DetailTopTeam(team: MatchTeamInfo?, modifier: Modifier = Modifier) {
    val metrics = rememberGoalioMetrics()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamLogo(team, size = metrics.dp(68), logoSize = metrics.dp(50))
        Spacer(Modifier.height(metrics.dp(10)))
        Text(team?.abbreviation ?: team?.shortName ?: team?.name ?: "TBD", color = GoalioColors.TextPrimary, fontSize = metrics.sp(18), fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun TeamLogo(team: MatchTeamInfo?, size: androidx.compose.ui.unit.Dp, logoSize: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF303232)), contentAlignment = Alignment.Center) {
        if (!team?.logo.isNullOrBlank()) {
            AsyncImage(team?.logo, contentDescription = team?.name, contentScale = ContentScale.Fit, modifier = Modifier.size(logoSize))
        } else {
            Text(team?.abbreviation ?: "TBD", color = GoalioColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun DetailTabs(selected: String, onSelected: (String) -> Unit) {
    val metrics = rememberGoalioMetrics()
    val tabs = listOf("Overview", "Timeline", "Stats", "AI Insight", "Player Lineups")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
        items(tabs) { tab ->
            Surface(
                color = if (selected == tab) GoalioColors.Accent else Color.Transparent,
                contentColor = if (selected == tab) Color.White else GoalioColors.TextSecondary,
                border = BorderStroke(1.dp, GoalioColors.Accent),
                shape = RoundedCornerShape(50),
                modifier = Modifier.clickable { onSelected(tab) }
            ) {
                Text(tab, fontSize = metrics.sp(13), fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = metrics.dp(16), vertical = metrics.dp(12)))
            }
        }
    }
}

@Composable
private fun OverviewContent(detail: MatchDetail) {
    val metrics = rememberGoalioMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(24))) {
        DetailInfoGrid(detail)
        TimelineSection(detail.keyMomentEvents().take(6), showTitle = true, keyMomentsOnly = true)
        PerformanceMatrix(detail)
        AiSummaryCard(detail.summary, detail)
    }
}

@Composable
private fun DetailInfoGrid(detail: MatchDetail) {
    val metrics = rememberGoalioMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
            InfoTile("Referee", detail.refereeName() ?: "Not listed", Modifier.weight(1f))
            InfoTile("Weather", detail.weatherText() ?: "Not listed", Modifier.weight(1f))
        }
        InfoTile("Stadium", detail.venueText().ifBlank { "Not listed" }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun InfoTile(label: String, value: String, modifier: Modifier = Modifier) {
    val metrics = rememberGoalioMetrics()
    Surface(color = GoalioColors.Surface2, shape = RoundedCornerShape(metrics.dp(7)), modifier = modifier.height(metrics.dp(86))) {
        Column(Modifier.padding(metrics.dp(12)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, color = GoalioColors.TextSecondary, fontSize = metrics.sp(13), fontWeight = FontWeight.Black)
            Spacer(Modifier.height(metrics.dp(5)))
            Text(value, color = GoalioColors.TextPrimary, fontSize = metrics.sp(15), fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PlayerLineupsSection(lineup: MatchLineupInfo?, loading: Boolean, error: String?) {
    val metrics = rememberGoalioMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
        Text("Player Lineups", color = Color.White, fontSize = metrics.sp(24), fontWeight = FontWeight.Black)
        when {
            lineup == null && loading -> MatchStateCard("Loading lineup data...")
            lineup == null -> MatchStateCard(error ?: "Lineups not announced yet")
            lineup.home.startingXI.isEmpty() && lineup.away.startingXI.isEmpty() -> {
                LineupMetaHeader(lineup)
                MatchStateCard(buildString {
                    append("Lineups not announced yet")
                    lineup.nextRefreshAt?.let { append("\nNext check ${formatLineupTime(it)}") }
                })
            }
            else -> {
                LineupMetaHeader(lineup)
                LineupTeamHeader(lineup.away, away = true)
                LineupPitch(lineup)
                LineupTeamHeader(lineup.home, away = false)
                BenchSection(lineup)
                UnavailableSection(lineup)
            }
        }
    }
}

@Composable
private fun LineupMetaHeader(lineup: MatchLineupInfo) {
    val metrics = rememberGoalioMetrics()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(metrics.dp(8)), verticalAlignment = Alignment.CenterVertically) {
        listOf(lineup.status, lineup.source.uppercase(), lineup.formationStatus).forEach { label ->
            Surface(color = if (label == "LIVE") GoalioColors.Live else Color(0xFF242424), shape = RoundedCornerShape(50), border = BorderStroke(1.dp, GoalioColors.CardBorder)) {
                Text(label, color = Color.White, fontSize = metrics.sp(10), fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = metrics.dp(10), vertical = metrics.dp(6)))
            }
        }
        Spacer(Modifier.weight(1f))
        Text(formatLineupTime(lineup.lastUpdated), color = GoalioColors.TextSecondary, fontSize = metrics.sp(10), textAlign = TextAlign.End)
    }
}

@Composable
private fun LineupTeamHeader(team: NormalizedTeamLineupInfo, away: Boolean) {
    val metrics = rememberGoalioMetrics()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (!team.teamLogo.isNullOrBlank()) {
            AsyncImage(model = team.teamLogo, contentDescription = team.teamName, contentScale = ContentScale.Fit, modifier = Modifier.size(metrics.dp(38)))
            Spacer(Modifier.width(metrics.dp(10)))
        }
        Column(Modifier.weight(1f), horizontalAlignment = if (away) Alignment.Start else Alignment.End) {
            Text(team.teamName.orEmpty(), color = Color.White, fontSize = metrics.sp(17), fontWeight = FontWeight.Black)
            Text(
                listOfNotNull(team.formation?.let { "Formation $it" }, team.manager?.name?.let { "Manager $it" }).joinToString(" | "),
                color = GoalioColors.TextSecondary, fontSize = metrics.sp(11), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LineupPitch(lineup: MatchLineupInfo) {
    val metrics = rememberGoalioMetrics()
    Surface(shape = RoundedCornerShape(metrics.dp(8)), color = Color(0xFF176B3A), modifier = Modifier.fillMaxWidth().height(metrics.dp(620))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize().padding(metrics.dp(8))) {
                val line = Color.White.copy(alpha = .72f)
                val stroke = 2.dp.toPx()
                drawRect(line, style = Stroke(stroke))
                drawLine(line, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), stroke)
                drawCircle(line, radius = size.width * .14f, center = center, style = Stroke(stroke))
                drawRect(line, topLeft = Offset(size.width * .2f, 0f), size = androidx.compose.ui.geometry.Size(size.width * .6f, size.height * .15f), style = Stroke(stroke))
                drawRect(line, topLeft = Offset(size.width * .2f, size.height * .85f), size = androidx.compose.ui.geometry.Size(size.width * .6f, size.height * .15f), style = Stroke(stroke))
                drawRect(line, topLeft = Offset(size.width * .36f, 0f), size = androidx.compose.ui.geometry.Size(size.width * .28f, size.height * .06f), style = Stroke(stroke))
                drawRect(line, topLeft = Offset(size.width * .36f, size.height * .94f), size = androidx.compose.ui.geometry.Size(size.width * .28f, size.height * .06f), style = Stroke(stroke))
            }
            (lineup.home.startingXI.map { it to GoalioColors.Accent } + lineup.away.startingXI.map { it to Color.White }).forEach { (player, border) ->
                PitchPlayerMarker(player, border, maxWidth, maxHeight)
            }
        }
    }
}

@Composable
private fun PitchPlayerMarker(player: PitchLineupPlayerInfo, border: Color, pitchWidth: androidx.compose.ui.unit.Dp, pitchHeight: androidx.compose.ui.unit.Dp) {
    val metrics = rememberGoalioMetrics()
    val marker = metrics.dp(42)
    val labelWidth = metrics.dp(76)
    val x = pitchWidth * ((player.x ?: 50f).coerceIn(4f, 96f) / 100f) - labelWidth / 2
    val y = pitchHeight * ((player.y ?: 50f).coerceIn(3f, 97f) / 100f) - marker / 2
    Column(Modifier.offset(x, y).width(labelWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = Color(0xFF111111), shape = CircleShape, border = BorderStroke(2.dp, border), modifier = Modifier.size(marker)) {
            Box(contentAlignment = Alignment.Center) {
                if (!player.photo.isNullOrBlank()) {
                    AsyncImage(model = player.photo, contentDescription = player.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                } else {
                    Text(player.number?.toString() ?: player.name.take(2).uppercase(), color = Color.White, fontSize = metrics.sp(11), fontWeight = FontWeight.Black)
                }
            }
        }
        Surface(color = Color.Black.copy(alpha = .72f), shape = RoundedCornerShape(3.dp)) {
            Text(player.name + if (player.captain) " (C)" else "", color = Color.White, fontSize = metrics.sp(8), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp))
        }
    }
}

@Composable
private fun BenchSection(lineup: MatchLineupInfo) {
    val metrics = rememberGoalioMetrics()
    if (lineup.home.bench.isEmpty() && lineup.away.bench.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
        Text("BENCH", color = GoalioColors.Accent, fontSize = metrics.sp(11), fontWeight = FontWeight.Black)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
            BenchColumn(lineup.home.bench, Modifier.weight(1f))
            BenchColumn(lineup.away.bench, Modifier.weight(1f))
        }
    }
}

@Composable
private fun BenchColumn(players: List<PitchLineupPlayerInfo>, modifier: Modifier) {
    val metrics = rememberGoalioMetrics()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(metrics.dp(7))) {
        players.forEach { player ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(player.number?.toString() ?: "-", color = GoalioColors.Accent, fontSize = metrics.sp(11), fontWeight = FontWeight.Black, modifier = Modifier.width(metrics.dp(24)))
                Text(player.name, color = GoalioColors.TextPrimary, fontSize = metrics.sp(11), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun UnavailableSection(lineup: MatchLineupInfo) {
    val metrics = rememberGoalioMetrics()
    val players = lineup.home.unavailable + lineup.away.unavailable
    if (players.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(8))) {
        Text("UNAVAILABLE", color = Color(0xFFFFC857), fontSize = metrics.sp(11), fontWeight = FontWeight.Black)
        players.forEach { Text("${it.name} | ${it.reason}", color = GoalioColors.TextSecondary, fontSize = metrics.sp(12)) }
    }
}

private fun formatLineupTime(value: String): String = runCatching {
    OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM, HH:mm"))
}.getOrDefault(value)

@Composable
private fun TimelineSection(events: List<MatchTimelineEvent>, showTitle: Boolean, keyMomentsOnly: Boolean = true) {
    val metrics = rememberGoalioMetrics()
    val moments = remember(events, keyMomentsOnly) {
        if (keyMomentsOnly) events.filter { it.isKeyMoment() } else events
    }
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(18))) {
        if (showTitle) Text(if (keyMomentsOnly) "Key Moments" else "Timeline", color = Color.White, fontSize = metrics.sp(24), fontWeight = FontWeight.Black)
        if (moments.isEmpty()) {
            MatchStateCard(if (keyMomentsOnly) "No goals, cards, penalties, or injury moments available yet." else "No timeline moments available yet.")
        } else {
            moments.forEachIndexed { index, event ->
                val kind = event.momentKind()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Box(Modifier.width(metrics.dp(34)).height(metrics.dp(86)), contentAlignment = Alignment.TopCenter) {
                        Canvas(Modifier.fillMaxSize()) {
                            val x = size.width / 2f
                            if (index > 0) drawLine(Color(0xFF3A3A3A), Offset(x, 0f), Offset(x, size.height * .24f), 4f, StrokeCap.Round)
                            if (index < moments.lastIndex) drawLine(Color(0xFF3A3A3A), Offset(x, size.height * .42f), Offset(x, size.height), 4f, StrokeCap.Round)
                        }
                        Box(
                            Modifier
                                .size(metrics.dp(27))
                                .clip(CircleShape)
                                .background(kind.color),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(kind.icon, color = Color.Black, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(Modifier.width(metrics.dp(12)))
                    Surface(
                        color = Color(0xFF171719),
                        shape = RoundedCornerShape(metrics.dp(10)),
                        border = BorderStroke(1.dp, Color(0xFF292929)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(Modifier.padding(metrics.dp(14))) {
                            Text("${event.minuteLabel()}  ${kind.label}".uppercase(), color = kind.color, fontSize = metrics.sp(12), fontWeight = FontWeight.Black, letterSpacing = .8.sp)
                            Spacer(Modifier.height(metrics.dp(5)))
                            Text(event.momentTitle(), color = GoalioColors.TextPrimary, fontSize = metrics.sp(17), fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            event.momentBody().takeIf { it.isNotBlank() }?.let {
                                Spacer(Modifier.height(metrics.dp(4)))
                                Text(it, color = GoalioColors.TextSecondary, fontSize = metrics.sp(13), lineHeight = metrics.sp(18), maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceMatrix(detail: MatchDetail) {
    val metrics = rememberGoalioMetrics()
    val possession = detail.possessionPercent()
    val homeName = detail.homeTeam?.abbreviation ?: detail.homeTeam?.shortName ?: "HOME"
    val awayName = detail.awayTeam?.abbreviation ?: detail.awayTeam?.shortName ?: "AWAY"
    val statRows = listOf(
        "Shots" to detail.statPair("totalShots", "SHOTS"),
        "On Goal" to detail.statPair("shotsOnTarget", "ON GOAL", "Shots On Target"),
        "Corners" to detail.statPair("wonCorners", "Corner Kicks"),
        "Fouls" to detail.statPair("foulsCommitted", "Fouls"),
        "Yellow Cards" to detail.statPair("yellowCards", "Yellow Cards"),
        "Saves" to detail.statPair("saves", "Saves"),
        "Passes" to detail.statPair("totalPasses", "Passes"),
        "Accurate Passes" to detail.statPair("accuratePasses", "Accurate Passes")
    ).filter { it.second != null }
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
        Text("Performance Matrix", color = Color.White, fontSize = metrics.sp(24), fontWeight = FontWeight.Black)
        Surface(
            color = GoalioColors.Surface2,
            shape = RoundedCornerShape(metrics.dp(10)),
            border = BorderStroke(1.dp, Color(0xFF3D3D3D)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(metrics.dp(22))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(homeName.uppercase(), color = GoalioColors.TextPrimary, fontSize = metrics.sp(13), fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    Text("MATCH STATS", color = GoalioColors.Accent, fontSize = metrics.sp(12), fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text(awayName.uppercase(), color = GoalioColors.TextPrimary, fontSize = metrics.sp(13), fontWeight = FontWeight.Black, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(metrics.dp(22)))
                Text("Possession", color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
                Spacer(Modifier.height(metrics.dp(13)))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${possession.toInt()}%", color = GoalioColors.TextPrimary, fontSize = metrics.sp(21), fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(metrics.dp(14)))
                    Row(Modifier.weight(1f).height(metrics.dp(13)).clip(RoundedCornerShape(50))) {
                        Box(Modifier.weight(possession.coerceAtLeast(1f)).fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFFFF7A00), Color(0xFFFFB02E)))))
                        Box(Modifier.weight((100f - possession).coerceAtLeast(1f)).fillMaxSize().background(Color(0xFF5B5C5D)))
                    }
                    Spacer(Modifier.width(metrics.dp(14)))
                    Text("${(100f - possession).toInt()}%", color = GoalioColors.TextPrimary, fontSize = metrics.sp(21), fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.height(metrics.dp(26)))
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
                    StatSummary("TOTAL SHOTS", detail.statPair("totalShots", "SHOTS")?.display() ?: "-", Modifier.weight(1f))
                    StatSummary("SHOTS ON TARGET", detail.shotsOnTargetDisplay(), Modifier.weight(1f))
                }
                if (statRows.isNotEmpty()) {
                    Spacer(Modifier.height(metrics.dp(22)))
                    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
                        statRows.forEach { (label, pair) ->
                            pair?.let { StatCompareRow(label, it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCompareRow(label: String, pair: StatPair) {
    val metrics = rememberGoalioMetrics()
    val homeValue = pair.home.statNumber()
    val awayValue = pair.away.statNumber()
    val total = ((homeValue ?: 0f) + (awayValue ?: 0f)).takeIf { it > 0f }
    val homeWeight = total?.let { ((homeValue ?: 0f) / it * 100f).coerceIn(4f, 96f) } ?: 50f
    val awayWeight = (100f - homeWeight).coerceIn(4f, 96f)
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(8))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pair.home, color = GoalioColors.TextPrimary, fontSize = metrics.sp(16), fontWeight = FontWeight.Black, modifier = Modifier.width(metrics.dp(52)))
            Text(label.uppercase(), color = GoalioColors.TextSecondary, fontSize = metrics.sp(11), fontWeight = FontWeight.Black, textAlign = TextAlign.Center, letterSpacing = .7.sp, modifier = Modifier.weight(1f))
            Text(pair.away, color = GoalioColors.TextPrimary, fontSize = metrics.sp(16), fontWeight = FontWeight.Black, textAlign = TextAlign.End, modifier = Modifier.width(metrics.dp(52)))
        }
        Row(Modifier.fillMaxWidth().height(metrics.dp(8)).clip(RoundedCornerShape(50))) {
            Box(Modifier.weight(homeWeight).fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFFFF7A00), Color(0xFFFFC247)))))
            Box(Modifier.weight(awayWeight).fillMaxSize().background(Color(0xFFD92D12)))
        }
    }
}

@Composable
private fun StatSummary(label: String, value: String, modifier: Modifier = Modifier) {
    val metrics = rememberGoalioMetrics()
    Surface(color = Color(0xFF171719), shape = RoundedCornerShape(metrics.dp(9)), border = BorderStroke(1.dp, Color(0xFF303030)), modifier = modifier) {
        Column(Modifier.padding(metrics.dp(14))) {
            Text(label, color = GoalioColors.TextSecondary, fontSize = metrics.sp(10), fontWeight = FontWeight.Black, letterSpacing = .7.sp)
            Spacer(Modifier.height(metrics.dp(8)))
            Text(value, color = GoalioColors.TextPrimary, fontSize = metrics.sp(22), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AiSummaryCard(summary: String?, detail: MatchDetail) {
    val metrics = rememberGoalioMetrics()
    Surface(color = Color(0xFF271B00), shape = RoundedCornerShape(metrics.dp(10)), border = BorderStroke(1.dp, Color(0xFF66531D)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(metrics.dp(24))) {
            Text("Goalio AI Summary", color = Color.White, fontSize = metrics.sp(23), fontWeight = FontWeight.Black)
            Spacer(Modifier.height(metrics.dp(20)))
            HtmlSummaryText(
                html = summary ?: "${detail.homeTeam?.shortName ?: "Home"} and ${detail.awayTeam?.shortName ?: "away"} are being tracked from the live ESPN feed. Detailed AI insight will expand as more match events and stats become available."
            )
            Spacer(Modifier.height(metrics.dp(24)))
            Surface(color = Color(0xFF3C4142), shape = RoundedCornerShape(50), modifier = Modifier.fillMaxWidth().height(metrics.dp(50))) {
                Row(Modifier.padding(horizontal = metrics.dp(18)), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ask AI about this match...", color = Color(0xFF858B94), fontSize = metrics.sp(14))
                }
            }
        }
    }
}

@Composable
private fun HtmlSummaryText(html: String) {
    val metrics = rememberGoalioMetrics()
    val cleanedHtml = remember(html) {
        html
            .replace(Regex("<photo\\b[^>]*>\\s*</photo>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<photo\\b[^>]*/?>", RegexOption.IGNORE_CASE), "")
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            TextView(context).apply {
                setTextColor(AndroidColor.rgb(216, 208, 191))
                setLinkTextColor(AndroidColor.rgb(255, 138, 0))
                textSize = 16f * metrics.scale
                setLineSpacing(4f, 1.08f)
                movementMethod = LinkMovementMethod.getInstance()
                setBackgroundColor(AndroidColor.TRANSPARENT)
            }
        },
        update = { view ->
            view.text = Html.fromHtml(cleanedHtml, Html.FROM_HTML_MODE_LEGACY)
        }
    )
}

@Composable
private fun StreamHighlights(media: MatchMediaInfo?, watch: MatchWatchInfo?, loading: Boolean, error: String?) {
    val metrics = rememberGoalioMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
        Text("WATCH & HIGHLIGHTS", color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        when {
            loading -> MatchStateCard("Finding official ways to watch…")
            error != null && media == null && watch == null -> MatchStateCard(error)
            else -> {
                WatchProvidersCard(watch)
                HighlightCard(media)
            }
        }
    }
}

@Composable
private fun WatchProvidersCard(watch: MatchWatchInfo?) {
    val metrics = rememberGoalioMetrics()
    val providers = watch?.providers.orEmpty()
    Surface(color = GoalioColors.Neutral, shape = RoundedCornerShape(metrics.dp(18)), border = BorderStroke(1.dp, GoalioColors.Border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(metrics.dp(18)), verticalArrangement = Arrangement.spacedBy(metrics.dp(13))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MediaGlyph("play")
                Spacer(Modifier.width(metrics.dp(12)))
                Column(Modifier.weight(1f)) {
                    Text("STREAM", color = GoalioColors.Tertiary, fontSize = metrics.sp(11), fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                    Text(if (providers.isNotEmpty()) "Official ways to watch" else "Official match centre", color = GoalioColors.Secondary, fontSize = metrics.sp(19), fontWeight = FontWeight.Black)
                }
                watch?.country?.let { MediaBadge(it) }
            }
            if (providers.isNotEmpty()) {
                providers.forEach { ProviderRow(it) }
            } else {
                Text(watch?.message ?: "Broadcaster information is not available for your region yet.", color = GoalioColors.TextSecondary, fontSize = metrics.sp(14))
                watch?.fallback?.let { MediaActionButton("OPEN ${it.name.uppercase()}", it.url) }
            }
            Text(watch?.disclaimer ?: "Streaming availability depends on your region and broadcaster rights.", color = GoalioColors.TextTertiary, fontSize = metrics.sp(10))
        }
    }
}

@Composable
private fun ProviderRow(provider: WatchProviderInfo) {
    val metrics = rememberGoalioMetrics()
    Surface(color = Color(0xFF241000), shape = RoundedCornerShape(metrics.dp(14)), border = BorderStroke(1.dp, GoalioColors.Tertiary), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(metrics.dp(14)), verticalArrangement = Arrangement.spacedBy(metrics.dp(8))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(provider.name, color = GoalioColors.Secondary, fontSize = metrics.sp(16), fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                MediaBadge(when (provider.isFree) { true -> "FREE"; false -> "PAID"; null -> "OFFICIAL" })
            }
            provider.note?.let { Text(it, color = GoalioColors.TextSecondary, fontSize = metrics.sp(12)) }
            MediaActionButton("WATCH ON ${provider.name.uppercase()}", provider.url)
        }
    }
}

@Composable
private fun HighlightCard(media: MatchMediaInfo?) {
    val metrics = rememberGoalioMetrics()
    val highlight = media?.highlight
    val context = LocalContext.current
    val primaryUrl = highlight?.url ?: media?.official?.matchUrl ?: media?.official?.highlightsPageUrl
    Surface(color = GoalioColors.Neutral, shape = RoundedCornerShape(metrics.dp(18)), border = BorderStroke(1.dp, GoalioColors.Border), modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
            if (!highlight?.thumbnailUrl.isNullOrBlank()) {
                Box(Modifier.fillMaxWidth().height(metrics.dp(176)).clip(RoundedCornerShape(topStart = metrics.dp(18), topEnd = metrics.dp(18)))) {
                    AsyncImage(highlight?.thumbnailUrl, "Official highlights", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .78f)))))
                    Box(Modifier.size(metrics.dp(56)).background(GoalioColors.Tertiary, CircleShape).align(Alignment.Center).clickable { primaryUrl?.let { context.openOfficialUrl(it) } }, contentAlignment = Alignment.Center) { Text("▶", color = GoalioColors.Primary, fontSize = metrics.sp(23)) }
                }
            }
            Column(Modifier.padding(start = metrics.dp(18), end = metrics.dp(18), bottom = metrics.dp(18)), verticalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MediaGlyph("video")
                    Spacer(Modifier.width(metrics.dp(12)))
                    Column(Modifier.weight(1f)) {
                        Text("HIGHLIGHTS", color = GoalioColors.Tertiary, fontSize = metrics.sp(11), fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                        Text(if (highlight?.status == "available") "Official match highlights" else if (media?.official?.matchUrl != null) "Official full match available" else "Highlights coming soon", color = GoalioColors.Secondary, fontSize = metrics.sp(19), fontWeight = FontWeight.Black)
                    }
                    MediaBadge((highlight?.status ?: "PENDING").uppercase())
                }
                Text(when { highlight?.status == "available" -> "Published by ${highlight.provider ?: "an official channel"}."; media?.official?.matchUrl != null -> "The verified full match is ready while official highlights are being prepared."; else -> "We’ll show the verified official video as soon as it is published." }, color = GoalioColors.TextSecondary, fontSize = metrics.sp(13))
                primaryUrl?.let { MediaActionButton(if (highlight?.status == "available") "WATCH HIGHLIGHTS" else if (media?.official?.matchUrl != null) "WATCH OFFICIAL MATCH" else "OPEN FIFA HIGHLIGHTS", it) }
            }
        }
    }
}

@Composable private fun MediaBadge(label: String) = Surface(color = GoalioColors.Surface3, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, GoalioColors.Border)) { Text(label, color = GoalioColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) }

@Composable private fun MediaActionButton(label: String, url: String) {
    val context = LocalContext.current
    Surface(color = Color(0xFF241000), shape = RoundedCornerShape(50), border = BorderStroke(2.dp, GoalioColors.Tertiary), modifier = Modifier.fillMaxWidth().clickable { context.openOfficialUrl(url) }) { Text(label, color = GoalioColors.Secondary, fontWeight = FontWeight.Black, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 14.dp, horizontal = 16.dp)) }
}

@Composable private fun MediaGlyph(kind: String) = Box(Modifier.size(42.dp).background(GoalioColors.Surface3, CircleShape), contentAlignment = Alignment.Center) { Text(if (kind == "play") "▶" else "▣", color = GoalioColors.Tertiary, fontSize = 18.sp, fontWeight = FontWeight.Black) }

private fun android.content.Context.openOfficialUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        .onFailure { Toast.makeText(this, "Could not open this official link", Toast.LENGTH_SHORT).show() }
}

@Composable
private fun MatchStateCard(text: String) {
    val metrics = rememberGoalioMetrics()
    Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(metrics.dp(16)), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = GoalioColors.TextSecondary, fontSize = metrics.sp(15), fontWeight = FontWeight.Bold, modifier = Modifier.padding(metrics.dp(20)))
    }
}

@Composable
private fun MatchBottomNav(modifier: Modifier = Modifier, selected: String, onHome: () -> Unit, onMatches: () -> Unit, onWorldCup: () -> Unit, onGames: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(color = Color(0xFF3B3B3B), shape = RoundedCornerShape(metrics.dp(28)), modifier = modifier.fillMaxWidth().padding(horizontal = metrics.dp(8), vertical = metrics.dp(10))) {
        Row(Modifier.padding(metrics.dp(8)), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            NavTab("Home", selected == "Home", onHome)
            NavTab("Matches", selected == "Matches", onMatches)
            NavTab("World Cup", selected == "World Cup", onWorldCup)
            NavTab("Games", selected == "Games", onGames)
        }
    }
}

@Composable
private fun RowScope.NavTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(
        color = if (selected) GoalioColors.Accent else Color.Transparent,
        shape = RoundedCornerShape(50),
        modifier = Modifier.weight(1f).height(metrics.dp(56)).clickable(onClick = onClick)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, color = if (selected) Color.White else Color(0xFFBEB8AA), fontSize = metrics.sp(12), fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
private fun BackGlyph(modifier: Modifier, color: Color) = Canvas(modifier) {
    drawLine(color, Offset(size.width * .85f, size.height * .5f), Offset(size.width * .15f, size.height * .5f), size.minDimension * .1f, StrokeCap.Round)
    drawLine(color, Offset(size.width * .15f, size.height * .5f), Offset(size.width * .44f, size.height * .2f), size.minDimension * .1f, StrokeCap.Round)
    drawLine(color, Offset(size.width * .15f, size.height * .5f), Offset(size.width * .44f, size.height * .8f), size.minDimension * .1f, StrokeCap.Round)
}

@Composable
private fun HeaderIcon(kind: String) = Canvas(Modifier.size(27.dp)) {
    val color = GoalioColors.Accent
    when (kind) {
        "search" -> {
            drawCircle(color, radius = size.minDimension * .26f, center = Offset(size.width * .42f, size.height * .42f), style = Stroke(size.minDimension * .11f))
            drawLine(color, Offset(size.width * .62f, size.height * .62f), Offset(size.width * .9f, size.height * .9f), size.minDimension * .11f, StrokeCap.Round)
        }
        "bell" -> {
            drawArc(color, 205f, 130f, false, topLeft = Offset(size.width * .24f, size.height * .18f), size = androidx.compose.ui.geometry.Size(size.width * .52f, size.height * .58f), style = Stroke(size.minDimension * .1f, cap = StrokeCap.Round))
            drawLine(color, Offset(size.width * .25f, size.height * .68f), Offset(size.width * .75f, size.height * .68f), size.minDimension * .1f, StrokeCap.Round)
            drawCircle(color, radius = size.minDimension * .05f, center = Offset(size.width * .5f, size.height * .84f))
        }
        else -> {
            drawCircle(color, radius = size.minDimension * .27f, center = center, style = Stroke(size.minDimension * .1f))
            repeat(8) { index ->
                val angle = Math.toRadians((index * 45).toDouble())
                drawLine(
                    color,
                    Offset(center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * .37f, center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * .37f),
                    Offset(center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * .48f, center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * .48f),
                    size.minDimension * .08f,
                    StrokeCap.Round
                )
            }
        }
    }
}

private fun ScheduleMatch.statusLabel(): String = when (state) {
    "pre" -> statusDescription ?: status ?: "Upcoming"
    "in" -> statusDescription ?: status ?: "Live"
    "post" -> statusDescription ?: status ?: "Finished"
    else -> statusDescription ?: status ?: "Match"
}

@Composable
private fun DynamicMatchStatus(match: ScheduleMatch, metrics: GoalioMetrics) {
    var now by remember(match.matchId, match.kickoff) { mutableStateOf(Instant.now()) }
    val anchor = remember(match.status, match.statusDescription) { Instant.now() }
    LaunchedEffect(match.matchId, match.state, match.status, match.statusDescription) {
        if (match.state !in setOf("pre", "in")) return@LaunchedEffect
        while (true) { now = Instant.now(); delay(1_000) }
    }
    Text(match.dynamicStatusText(now, anchor), color = statusColor(match.state), fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
}

@Composable
private fun DynamicDetailStatus(detail: MatchDetail, metrics: GoalioMetrics) {
    var now by remember(detail.matchId, detail.kickoff) { mutableStateOf(Instant.now()) }
    val anchor = remember(detail.status, detail.statusDescription) { Instant.now() }
    LaunchedEffect(detail.matchId, detail.status, detail.statusDescription) {
        if (detail.statusState() !in setOf("pre", "in")) return@LaunchedEffect
        while (true) { now = Instant.now(); delay(1_000) }
    }
    Text(detail.dynamicStatusText(now, anchor), color = statusColor(detail.statusState()), fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
}

private fun ScheduleMatch.dynamicStatusText(now: Instant, anchor: Instant): String = when (state) {
    "pre" -> countdownLabel(now) ?: statusLabel().uppercase()
    "in" -> liveClock(status, statusDescription, anchor, now)
    else -> statusLabel().uppercase()
}

private fun MatchDetail.dynamicStatusText(now: Instant, anchor: Instant): String = when (statusState()) {
    "pre" -> countdownLabel(kickoff, now) ?: (statusDescription ?: status ?: "UPCOMING").uppercase()
    "in" -> liveClock(status, statusDescription, anchor, now)
    else -> (statusDescription ?: status ?: "MATCH").uppercase()
}

private fun liveClock(status: String?, description: String?, anchor: Instant, now: Instant): String {
    val minute = Regex("(\\d{1,3})(?:\\+\\d+)?['’]?").find("${status.orEmpty()} ${description.orEmpty()}")
        ?.groupValues?.get(1)?.toLongOrNull() ?: return "LIVE ${status ?: description.orEmpty()}".trim()
    val elapsed = Duration.between(anchor, now).seconds.coerceAtLeast(0)
    return "LIVE ${minute + elapsed / 60}'%02d\"".format(elapsed % 60)
}

private fun ScheduleMatch.countdownLabel(now: Instant): String? =
    if (state != "pre") null else countdownLabel(kickoff, now)

private fun countdownLabel(kickoff: String?, now: Instant): String? {
    if (kickoff.isNullOrBlank()) return null
    val start = runCatching { OffsetDateTime.parse(kickoff).toInstant() }.getOrNull() ?: return null
    val remaining = Duration.between(now, start)
    if (remaining.isNegative) return "STARTING"
    val hours = remaining.toHours()
    val minutes = remaining.toMinutes() % 60
    val seconds = remaining.seconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun ScheduleMatch.leagueLabel(): String = leagueLabel(league)

private fun MatchDetail.leagueLabel(): String = leagueLabel(league)

private fun leagueLabel(league: String): String = when (league) {
    "fifa.world" -> "World Cup"
    "eng.1" -> "Premier League"
    "esp.1" -> "LaLiga"
    "ita.1" -> "Serie A"
    "ger.1" -> "Bundesliga"
    "fra.1" -> "Ligue 1"
    "uefa.champions" -> "Champions League"
    "uefa.europa" -> "Europa League"
    else -> league.uppercase()
}

private fun ScheduleMatch.stageLabel(): String =
    statusDescription?.takeIf { state == "pre" } ?: "Group Stage"

private fun scoreLine(match: ScheduleMatch): String = scoreLine(match.homeTeam, match.awayTeam)

private fun scoreLine(home: MatchTeamInfo?, away: MatchTeamInfo?): String {
    val homeScore = home?.score
    val awayScore = away?.score
    return if (homeScore == null || awayScore == null) "v" else "$homeScore - $awayScore"
}

private fun statusColor(state: String?): Color = when (state) {
    "in" -> GoalioColors.Live
    "pre" -> GoalioColors.Accent
    "post" -> GoalioColors.Finished
    else -> GoalioColors.TextTertiary
}

private fun ScheduleMatch.homeWinProbability(): Float {
    val home = homeTeam?.score ?: return 50f
    val away = awayTeam?.score ?: return 50f
    return (50f + (home - away) * 12f).coerceIn(35f, 65f)
}

private fun MatchDetail.homeWinProbability(): Float {
    winProbability?.homeWinPercentage?.let { return it.toFloat().coerceIn(1f, 99f) }
    val home = homeTeam?.score ?: return statPercent("possession") ?: 50f
    val away = awayTeam?.score ?: return statPercent("possession") ?: 50f
    return (50f + (home - away) * 12f).coerceIn(30f, 78f)
}

private fun MatchDetail.possessionPercent(): Float {
    return statPercent("possession") ?: 50f
}

private fun MatchDetail.statPercent(name: String): Float? {
    val possession = teamStats.firstOrNull()?.stats?.firstOrNull {
        it.name?.contains(name, true) == true || it.label?.contains(name, true) == true
    }?.value
    return possession?.filter { it.isDigit() || it == '.' }?.toFloatOrNull()?.coerceIn(1f, 99f)
}

private fun MatchDetail.statValue(name: String): String? =
    teamStats.firstOrNull()?.stats?.firstOrNull {
        it.name?.contains(name, true) == true || it.label?.contains(name, true) == true
    }?.value

private fun MatchDetail.shotsOnTargetDisplay(): String {
    return statPair("shotsOnTarget", "ON GOAL", "Shots On Target")?.display() ?: "-"
}

private fun MatchDetail.statPair(vararg keys: String): StatPair? {
    val home = teamStats.getOrNull(0)?.findStatValue(*keys)
    val away = teamStats.getOrNull(1)?.findStatValue(*keys)
    if (home.isNullOrBlank() && away.isNullOrBlank()) return null
    return StatPair(home.orEmpty().ifBlank { "-" }, away.orEmpty().ifBlank { "-" })
}

private fun TeamStatsBlock.findStatValue(vararg keys: String): String? {
    return stats.firstOrNull { stat ->
        keys.any { key ->
            stat.name.matchesStatKey(key) || stat.label.matchesStatKey(key)
        }
    }?.value
}

private fun String?.matchesStatKey(key: String): Boolean {
    if (this.isNullOrBlank()) return false
    return equals(key, ignoreCase = true) || contains(key, ignoreCase = true) || key.contains(this, ignoreCase = true)
}

private fun String.statNumber(): Float? =
    filter { it.isDigit() || it == '.' || it == '-' }.toFloatOrNull()

private fun MatchDetail.keyMomentEvents(): List<MatchTimelineEvent> =
    events.filter { it.isKeyMoment() }

private fun MatchTimelineEvent.isKeyMoment(): Boolean {
    val raw = "${type.orEmpty()} ${text.orEmpty()}".lowercase(Locale.US)
    return raw.contains("goal!") ||
        raw.contains(" penalty") ||
        raw.contains("penalty ") ||
        raw.contains("injury") ||
        raw.contains("injured") ||
        raw.contains("yellow card") ||
        raw.contains("red card") ||
        raw.contains("sent off")
}

private fun MatchTimelineEvent.momentKind(): MomentStyle {
    val raw = "${type.orEmpty()} ${text.orEmpty()}".lowercase(Locale.US)
    return when {
        raw.contains("red card") || raw.contains("sent off") -> MomentStyle("Red Card", "R", Color(0xFFE03012))
        raw.contains("yellow card") -> MomentStyle("Yellow Card", "Y", Color(0xFFFFD54F))
        raw.contains("penalty") -> MomentStyle("Penalty", "P", Color(0xFFFF8500))
        raw.contains("injury") || raw.contains("injured") -> MomentStyle("Injury", "!", Color(0xFFFFC857))
        raw.contains("goal!") || raw.contains(" goal") -> MomentStyle("Goal", "G", Color(0xFFFFA31A))
        else -> MomentStyle("Moment", "-", GoalioColors.TextSecondary)
    }
}

private fun MatchTimelineEvent.minuteLabel(): String =
    minute?.takeIf { it.isNotBlank() }?.let { if (it.contains("'")) it else "$it'" } ?: "--'"

private fun MatchTimelineEvent.momentTitle(): String {
    val source = text.orEmpty().trim()
    if (source.isBlank()) return momentKind().label
    val lower = source.lowercase(Locale.US)
    val cardMarker = when {
        lower.contains(" is shown the yellow card") -> " is shown the yellow card"
        lower.contains(" is shown the red card") -> " is shown the red card"
        else -> null
    }
    if (cardMarker != null) {
        return source.substringBefore(cardMarker, source).substringAfterLast(". ").trim().ifBlank { momentKind().label }
    }
    if (lower.startsWith("goal!")) {
        return source.substringAfter("Goal!", source).substringBefore(".").trim().ifBlank { "Goal" }
    }
    if (lower.contains("injury")) {
        return source.substringBefore(".").trim().ifBlank { "Injury stoppage" }
    }
    if (lower.contains("penalty")) {
        return source.substringBefore(".").trim().ifBlank { "Penalty" }
    }
    return source.substringBefore(".").trim().ifBlank { momentKind().label }
}

private fun MatchTimelineEvent.momentBody(): String {
    val source = text.orEmpty().trim()
    val title = momentTitle()
    return source.removePrefix("Goal!").trim()
        .removePrefix(title).trimStart('.', ' ', '-')
        .ifBlank { source.takeUnless { it == title }.orEmpty() }
}

private fun ScheduleMatch.venueText(): String =
    listOfNotNull(venue?.name, venue?.city).joinToString(", ")

private fun MatchDetail.venueText(): String =
    listOfNotNull(venue?.name, venue?.city).joinToString(" | ")

private fun MatchDetail.refereeName(): String? =
    officials.firstOrNull { it.role?.contains("ref", true) == true }?.name
        ?: officials.firstOrNull()?.name

private fun MatchDetail.weatherText(): String? =
    weather?.displayValue
        ?: listOfNotNull(weather?.temperature, weather?.condition).joinToString(" ").ifBlank { null }

private fun MatchDetail.statusPillText(): String =
    if (statusState() == "in") "LIVE ${status ?: ""}".trim() else (statusDescription ?: status ?: "Match").uppercase()

private fun MatchDetail.statusState(): String? = when {
    Regex("\\b\\d{1,3}(?:\\+\\d+)?['’]").containsMatchIn("${status.orEmpty()} ${statusDescription.orEmpty()}") -> "in"
    status?.contains("live", true) == true -> "in"
    statusDescription?.contains("live", true) == true -> "in"
    listOf(status, statusDescription).any { it?.trim()?.lowercase() in setOf("ft", "final", "full time", "aet", "pens") } -> "post"
    statusDescription?.contains("scheduled", true) == true -> "pre"
    else -> null
}

private fun ScheduleMatch.scoreSignature(): String =
    "${homeTeam?.score}:${awayTeam?.score}"

private fun MatchDetail.scoreSignature(): String =
    "${homeTeam?.score}:${awayTeam?.score}"

private fun formatKickoff(value: String?): String {
    if (value.isNullOrBlank()) return "--:--"
    return runCatching {
        OffsetDateTime.parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault(value.take(5))
}

private fun ScheduleMatch.localKickoffDate(): LocalDate? = runCatching {
    OffsetDateTime.parse(kickoff).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
}.getOrNull()
