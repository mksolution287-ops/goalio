package com.goalio.scores

import android.widget.Toast
import android.widget.TextView
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
        matches = MatchRepository.cachedFeed(context, selectedDate, selectedDate)
        loading = matches.isEmpty()
        while (true) {
            errorMessage = null
            runCatching { MatchRepository.refreshFeed(context, selectedDate, selectedDate) }
                .onSuccess { result ->
                    matches = result.matches
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
            delay(MatchRepository.nextRefreshDelayMillis(context, matches))
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
            item { MatchTopBar(title = "GOALIO", onBack = onBack, large = true) }
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
        MatchBottomNav(Modifier.align(Alignment.BottomCenter), selected = "Matches", onHome = onOpenHome, onMatches = {}, onWorldCup = onOpenWorldCup)
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
    onOpenWorldCup: () -> Unit
) {
    val context = LocalContext.current
    val metrics = rememberGoalioMetrics()
    var detail by remember { mutableStateOf(MatchRepository.cachedDetail(context, league, matchId)) }
    var loading by remember { mutableStateOf(detail == null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by rememberSaveable { mutableStateOf("Overview") }
    var boostUntil by remember { mutableStateOf(0L) }
    var previousScore by remember { mutableStateOf(detail?.scoreSignature() ?: initialMatch?.scoreSignature().orEmpty()) }

    LaunchedEffect(league, matchId) {
        detail = MatchRepository.cachedDetail(context, league, matchId)
        loading = detail == null
        while (true) {
            errorMessage = null
            runCatching { MatchRepository.refreshDetail(context, league, matchId) }
                .onSuccess { fresh ->
                    val newScore = fresh.scoreSignature()
                    if (previousScore.isNotBlank() && previousScore != newScore) {
                        boostUntil = System.currentTimeMillis() + 8 * 60 * 1000L
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
                when {
                    !isLive -> 15 * 60 * 1000L
                    System.currentTimeMillis() < boostUntil -> 2 * 60 * 1000L
                    else -> 5 * 60 * 1000L
                }
            )
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
            item { MatchTopBar(title = "Goalio", onBack = onBack, large = false) }
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
                    "Player Lineups" -> item { PlayerLineupsSection(shown) }
                    else -> item { OverviewContent(shown) }
                }
                item { StreamHighlights() }
            }
        }
        MatchBottomNav(Modifier.align(Alignment.BottomCenter), selected = "Matches", onHome = onOpenHome, onMatches = onOpenMatches, onWorldCup = onOpenWorldCup)
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
        color = if (selected) GoalioColors.Accent else Color(0xFF171A19),
        contentColor = if (selected) Color.Black else GoalioColors.TextPrimary,
        border = BorderStroke(1.dp, if (selected) GoalioColors.Accent else GoalioColors.CardBorder),
        shape = RoundedCornerShape(metrics.dp(18)),
        modifier = Modifier.width(metrics.dp(100)).height(metrics.dp(88)).clickable(onClick = onClick)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).uppercase(), fontSize = metrics.sp(17), letterSpacing = 2.sp, color = if (selected) Color(0xFF2B1A02) else GoalioColors.TextSecondary)
            Text(date.format(DateTimeFormatter.ofPattern("dd MMM")), fontSize = metrics.sp(19), fontWeight = FontWeight.Black)
            Text(date.year.toString(), fontSize = metrics.sp(12), color = if (selected) Color(0xFF3F2704) else GoalioColors.TextTertiary)
        }
    }
}

@Composable
private fun LeagueChip(league: LeagueFilter, selected: Boolean, onClick: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(
        color = if (selected) Color(0xFF4A2708) else GoalioColors.Surface2,
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
                Text(match.statusPillText(), color = statusColor(match.state), fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
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
                    Text(detail.statusPillText(), color = statusColor(detail.statusState()), fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(metrics.dp(6)))
                    Text(scoreLine(detail.homeTeam, detail.awayTeam), color = GoalioColors.TextPrimary, fontSize = metrics.sp(42), fontWeight = FontWeight.Black)
                }
                DetailTopTeam(detail.awayTeam, Modifier.weight(1f))
            }
            Spacer(Modifier.height(metrics.dp(22)))
            Text(detail.venueText().ifBlank { detail.leagueLabel() }, color = GoalioColors.TextSecondary, fontSize = metrics.sp(13), fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(metrics.dp(28)))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("MOMENTUM", color = GoalioColors.TextSecondary, fontSize = metrics.sp(11), fontWeight = FontWeight.Black)
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
        summary = null
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
private fun PlayerLineupsSection(detail: MatchDetail) {
    val metrics = rememberGoalioMetrics()
    val lineups = detail.lineups
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
        Text("Player Lineups", color = Color.White, fontSize = metrics.sp(24), fontWeight = FontWeight.Black)
        if (lineups.isEmpty()) {
            MatchStateCard("Lineups are not available for this match yet.")
        } else {
            lineups.forEach { lineup ->
                TeamLineupCard(lineup)
            }
        }
    }
}

@Composable
private fun TeamLineupCard(lineup: TeamLineupInfo) {
    val metrics = rememberGoalioMetrics()
    Surface(color = GoalioColors.Surface2, shape = RoundedCornerShape(metrics.dp(10)), border = BorderStroke(1.dp, Color(0xFF323232)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(metrics.dp(18)), verticalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(lineup.teamName ?: "Team", color = GoalioColors.TextPrimary, fontSize = metrics.sp(20), fontWeight = FontWeight.Black)
                    Text(listOfNotNull(lineup.formation?.let { "Formation $it" }, lineup.coach?.let { "Coach $it" }).joinToString("  |  ").ifBlank { "Lineup" }, color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.Bold)
                }
            }
            if (lineup.starters.isNotEmpty()) {
                LineupGroup("Starting XI", lineup.starters)
            }
            if (lineup.substitutes.isNotEmpty()) {
                LineupGroup(if (lineup.starters.isEmpty()) "Squad" else "Bench", lineup.substitutes)
            }
        }
    }
}

@Composable
private fun LineupGroup(title: String, players: List<LineupPlayerInfo>) {
    val metrics = rememberGoalioMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(9))) {
        Text(title.uppercase(), color = GoalioColors.Accent, fontSize = metrics.sp(11), fontWeight = FontWeight.Black, letterSpacing = .8.sp)
        players.forEach { player ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Surface(color = Color(0xFF121212), shape = CircleShape, border = BorderStroke(1.dp, Color(0xFF3A3A3A)), modifier = Modifier.size(metrics.dp(34))) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(player.jersey ?: "-", color = GoalioColors.TextPrimary, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.width(metrics.dp(11)))
                Column(Modifier.weight(1f)) {
                    Text(player.name + if (player.captain) " (C)" else "", color = GoalioColors.TextPrimary, fontSize = metrics.sp(15), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(player.position, player.formationPlace?.let { "Slot $it" }).joinToString(" | "), color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

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
private fun StreamHighlights() {
    val metrics = rememberGoalioMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
        Text("STREAM & HIGHLIGHTS", color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(18))) {
            repeat(3) {
                Box(Modifier.size(metrics.dp(56)).clip(CircleShape).background(GoalioColors.Surface1), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(metrics.dp(12)).clip(CircleShape).background(GoalioColors.TextTertiary.copy(alpha = .5f)))
                }
            }
        }
    }
}

@Composable
private fun MatchStateCard(text: String) {
    val metrics = rememberGoalioMetrics()
    Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(metrics.dp(16)), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = GoalioColors.TextSecondary, fontSize = metrics.sp(15), fontWeight = FontWeight.Bold, modifier = Modifier.padding(metrics.dp(20)))
    }
}

@Composable
private fun MatchBottomNav(modifier: Modifier = Modifier, selected: String, onHome: () -> Unit, onMatches: () -> Unit, onWorldCup: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(color = Color(0xFF3B3B3B), shape = RoundedCornerShape(metrics.dp(28)), modifier = modifier.fillMaxWidth().padding(horizontal = metrics.dp(8), vertical = metrics.dp(10))) {
        Row(Modifier.padding(metrics.dp(8)), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            NavTab("Home", selected == "Home", onHome)
            NavTab("Matches", selected == "Matches", onMatches)
            NavTab("World Cup", selected == "World Cup", onWorldCup)
            NavTab("Games", false) {}
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

private fun ScheduleMatch.statusPillText(): String {
    if (state == "pre") return countdownLabel() ?: statusLabel().uppercase()
    if (state == "in") return "LIVE ${status ?: statusDescription ?: ""}".trim()
    return statusLabel().uppercase()
}

private fun ScheduleMatch.countdownLabel(): String? {
    if (state != "pre" || kickoff.isNullOrBlank()) return null
    val start = runCatching { OffsetDateTime.parse(kickoff).toInstant() }.getOrNull() ?: return null
    val remaining = Duration.between(Instant.now(), start)
    if (remaining.isNegative) return "STARTING"
    val hours = remaining.toHours()
    val minutes = remaining.toMinutes() % 60
    return if (hours > 0) "${hours}H ${minutes}M" else "${minutes}M"
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
    status?.contains("live", true) == true -> "in"
    statusDescription?.contains("live", true) == true -> "in"
    statusDescription?.contains("final", true) == true -> "post"
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
