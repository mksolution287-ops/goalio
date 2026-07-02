package com.goalio.scores

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.widget.TextView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
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

private data class LeagueFilter(val code: String?, val label: String, val mark: String, val markColor: Color)
private data class StatPair(val home: String, val away: String) {
    fun display(): String = "$home / $away"
}
private data class MomentStyle(val label: String, val icon: String, val color: Color)

private val LeagueFilters = listOf(
    LeagueFilter("fifa.world", "World Cup", "WC", Color(0xFFFF8500)),
    LeagueFilter("eng.1", "Premier League", "PL", Color(0xFF7A3CFF)),
    LeagueFilter("uefa.champions", "Champions League", "CL", Color(0xFF2F7DFF)),
    LeagueFilter("esp.1", "LaLiga", "LL", Color(0xFFFF4D4D)),
    LeagueFilter("ita.1", "Serie A", "SA", Color(0xFF25A6FF)),
    LeagueFilter("ger.1", "Bundesliga", "BL", Color(0xFFE03012)),
    LeagueFilter("fra.1", "Ligue 1", "L1", Color(0xFF20D97A)),
    LeagueFilter("uefa.europa", "Europa League", "EL", Color(0xFFFFA31A))
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
    var selectedDate by remember { mutableStateOf(today.toString()) }
    var selectedLeague by rememberSaveable { mutableStateOf("fifa.world") }
    var matches by remember { mutableStateOf(emptyList<ScheduleMatch>()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedDate) {
        MatchRepository.matchUpdates.collect { canonical ->
            val shared = canonical.values.filter { it.matchesCalendarDate(selectedDate) }
            if (shared.isNotEmpty()) {
                matches = shared.sortedWith(compareBy<ScheduleMatch> { stateRank(it.state) }.thenBy { it.kickoff.orEmpty() })
            }
        }
    }

    LaunchedEffect(selectedDate) {
        val localDate = LocalDate.parse(selectedDate)
        val fetchFrom = localDate.minusDays(1).toString()
        val fetchTo = localDate.plusDays(1).toString()
        matches = MatchRepository.cachedFeed(context, fetchFrom, fetchTo).filter { it.matchesCalendarDate(selectedDate) }
        loading = matches.isEmpty()
        while (true) {
            errorMessage = null
            runCatching { MatchRepository.refreshFeed(context, fetchFrom, fetchTo) }
                .onSuccess { result ->
                    matches = result.matches.filter { it.matchesCalendarDate(selectedDate) }
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
                DateFilterHeader(
                    selectedDate = selectedLocalDate,
                    onDateSelected = { selectedDate = it.toString() }
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(metrics.dp(13))) {
                    items(matchCalendarStrip(today, selectedLocalDate)) { day ->
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
                        state.first -> MatchListSkeleton()
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
private fun DateFilterHeader(selectedDate: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    val context = LocalContext.current
    val metrics = rememberGoalioMetrics()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "MATCH CALENDAR",
                color = GoalioColors.Tertiary,
                fontSize = metrics.sp(10),
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                selectedDate.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                color = GoalioColors.TextPrimary,
                fontSize = metrics.sp(22),
                fontWeight = FontWeight.Black
            )
        }
        Surface(
            color = GoalioColors.Neutral,
            contentColor = Color.White,
            border = BorderStroke(1.dp, GoalioColors.Tertiary.copy(alpha = .7f)),
            shape = RoundedCornerShape(50),
            modifier = Modifier.clickable {
                DatePickerDialog(
                    context,
                    { _, year, month, day -> onDateSelected(LocalDate.of(year, month + 1, day)) },
                    selectedDate.year,
                    selectedDate.monthValue - 1,
                    selectedDate.dayOfMonth
                ).show()
            }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = metrics.dp(16), vertical = metrics.dp(10)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CalendarGlyph(Modifier.size(metrics.dp(17)), GoalioColors.Tertiary)
                Spacer(Modifier.width(metrics.dp(8)))
                Text("Choose date", fontSize = metrics.sp(12), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CalendarGlyph(modifier: Modifier, color: Color) = Canvas(modifier) {
    val stroke = size.minDimension * .09f
    drawRoundRect(color, style = Stroke(stroke), cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke * 1.5f))
    drawLine(color, Offset(size.width * .2f, size.height * .34f), Offset(size.width * .8f, size.height * .34f), stroke, StrokeCap.Round)
    drawLine(color, Offset(size.width * .3f, size.height * .05f), Offset(size.width * .3f, size.height * .2f), stroke, StrokeCap.Round)
    drawLine(color, Offset(size.width * .7f, size.height * .05f), Offset(size.width * .7f, size.height * .2f), stroke, StrokeCap.Round)
}

@Composable
private fun MatchListSkeleton() {
    val transition = rememberInfiniteTransition(label = "matchSkeleton")
    val alpha by transition.animateFloat(
        initialValue = .34f,
        targetValue = .72f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "matchSkeletonAlpha"
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(4) {
            Surface(
                color = GoalioColors.Neutral,
                border = BorderStroke(1.dp, GoalioColors.CardBorder),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth().height(116.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.width(92.dp).height(9.dp).clip(CircleShape).background(Color(0xFF666666).copy(alpha = alpha)))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF666666).copy(alpha = alpha)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Box(Modifier.fillMaxWidth(.62f).height(12.dp).clip(CircleShape).background(Color(0xFF777777).copy(alpha = alpha)))
                            Box(Modifier.fillMaxWidth(.43f).height(9.dp).clip(CircleShape).background(Color(0xFF5A5A5A).copy(alpha = alpha)))
                        }
                        Box(Modifier.width(48.dp).height(24.dp).clip(CircleShape).background(GoalioColors.Tertiary.copy(alpha = alpha * .55f)))
                    }
                }
            }
        }
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
                    loading -> MatchDetailHeroSkeleton()
                    else -> MatchStateCard(errorMessage ?: "No detail found for this match.")
                }
            }
            if (shown != null) {
                item { DetailTabs(selectedTab) { selectedTab = it } }
                when (selectedTab) {
                    "Timeline" -> item { TimelineSection(shown.events, showTitle = true, keyMomentsOnly = false) }
                    "Stats" -> item { PerformanceMatrix(shown) }
                    "AI Insight" -> item { AiSummaryCard(shown.summary, shown) }
                    "Lineups" -> item { PlayerLineupsSection(lineup, lineupLoading, lineupError) }
                    "Watch" -> item {
                        if (mediaLoading && media == null && watch == null) {
                            MatchDetailTabSkeleton("Watch")
                        } else {
                            StreamHighlights(media, watch, mediaLoading, mediaError)
                        }
                    }
                    else -> item { OverviewContent(shown) }
                }
            } else if (loading || initialMatch != null) {
                item { DetailTabs(selectedTab) { selectedTab = it } }
                item { MatchDetailTabSkeleton(selectedTab) }
            }
        }
        GoalioBottomBar(Modifier.align(Alignment.BottomCenter), "Matches", onOpenHome, onOpenMatches, onOpenWorldCup, onOpenGames)
    }
}

@Composable
private fun MatchTopBar(title: String, onBack: () -> Unit, large: Boolean) {
    val metrics = rememberGoalioMetrics()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HeaderIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", Color.White, Modifier.clickable(onClick = onBack))
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
            HeaderIcon(Icons.Default.Search, "Search")
            HeaderIcon(Icons.Default.Notifications, "Notifications")
            HeaderIcon(Icons.Default.Settings, "Settings")
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
        Row(Modifier.padding(horizontal = metrics.dp(13), vertical = metrics.dp(10)), verticalAlignment = Alignment.CenterVertically) {
            LeagueBadge(league, selected)
            Spacer(Modifier.width(metrics.dp(9)))
            Text(league.label, fontSize = metrics.sp(16), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LeagueBadge(league: LeagueFilter, selected: Boolean) {
    val metrics = rememberGoalioMetrics()
    Surface(
        color = if (selected) league.markColor else league.markColor.copy(alpha = .18f),
        contentColor = if (selected) Color.Black else league.markColor,
        shape = RoundedCornerShape(metrics.dp(8)),
        border = BorderStroke(1.dp, league.markColor.copy(alpha = if (selected) 1f else .55f)),
        modifier = Modifier.size(metrics.dp(25))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                league.mark,
                fontSize = metrics.sp(9),
                fontWeight = FontWeight.Black,
                letterSpacing = .2.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FixtureCard(match: ScheduleMatch, onOpenMatch: (ScheduleMatch) -> Unit) {
    val metrics = rememberGoalioMetrics()
    val homeOdds = match.homeWinProbability()
    Surface(
        color = GoalioColors.Neutral,
        shape = RoundedCornerShape(metrics.dp(24)),
        border = BorderStroke(1.dp, GoalioColors.Tertiary.copy(alpha = .28f)),
        modifier = Modifier.fillMaxWidth().clickable { onOpenMatch(match) }
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(3.dp).background(GoalioColors.Tertiary))
            Row(Modifier.fillMaxWidth().padding(horizontal = metrics.dp(18), vertical = metrics.dp(15)), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(match.leagueLabel().uppercase(), color = GoalioColors.Tertiary, fontSize = metrics.sp(10), fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(match.stageLabel(), color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DynamicMatchStatus(match, metrics)
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = metrics.dp(18)).height(1.dp).background(Color(0xFF303034)))
            Row(Modifier.fillMaxWidth().padding(horizontal = metrics.dp(18), vertical = metrics.dp(24)), verticalAlignment = Alignment.CenterVertically) {
                MatchTeamBlock(match.homeTeam, Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(metrics.dp(116))) {
                    Text(scoreLine(match), color = GoalioColors.TextPrimary, fontSize = metrics.sp(38), fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(metrics.dp(8)))
                    Surface(color = Color.Black, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, Color(0xFF39393D))) {
                        Text("MATCH CENTER", color = GoalioColors.TextSecondary, fontSize = metrics.sp(9), fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
                MatchTeamBlock(match.awayTeam, Modifier.weight(1f))
            }
            Column(Modifier.padding(horizontal = metrics.dp(20), vertical = metrics.dp(2))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("HOME ${homeOdds.toInt()}%", color = GoalioColors.TextSecondary, fontSize = metrics.sp(11), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${(100f - homeOdds).toInt()}% AWAY", color = GoalioColors.TextSecondary, fontSize = metrics.sp(11), fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(metrics.dp(9)))
                Row(Modifier.fillMaxWidth().height(metrics.dp(7)).clip(RoundedCornerShape(50))) {
                    Box(Modifier.weight(homeOdds).fillMaxSize().background(GoalioColors.Tertiary))
                    Box(Modifier.weight(100f - homeOdds).fillMaxSize().background(Color(0xFF3C3C3E)))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = metrics.dp(20), vertical = metrics.dp(17)), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(GoalioColors.Tertiary))
                Spacer(Modifier.width(8.dp))
                Text("Tap to open full match details", color = GoalioColors.TextTertiary, fontSize = metrics.sp(11), fontWeight = FontWeight.SemiBold)
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
    val tabs = listOf("Overview", "Timeline", "Stats", "AI Insight", "Lineups", "Watch")
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(10)),
        contentPadding = PaddingValues(vertical = metrics.dp(4))
    ) {
        items(tabs) { tab ->
            val isSelected = selected == tab
            val tabBrush = if (isSelected) {
                Brush.horizontalGradient(
                    colors = listOf(Color(0xFF8F3900), Color(0xFF100600))
                )
            } else {
                Brush.linearGradient(colors = listOf(Color.Transparent, Color.Transparent))
            }
            Surface(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onSelected(tab) },
                color = Color.Transparent,
                shape = CircleShape,
                border = BorderStroke(1.5.dp, Color(0xFFFF8500))
            ) {
                Box(
                    modifier = Modifier
                        .background(tabBrush)
                        .padding(horizontal = metrics.dp(20), vertical = metrics.dp(12)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab,
                        color = Color.White,
                        fontSize = metrics.sp(13),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        letterSpacing = 0.3.sp
                    )
                }
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
        SectionTitle("Player Lineups", "Starting XI, formation and bench")
        when {
            lineup == null && loading -> LineupSkeleton()
            lineup == null -> MatchStateCard(error ?: "Lineups not announced yet")
            lineup.home.startingXI.isEmpty() && lineup.away.startingXI.isEmpty() -> {
                MatchStateCard(buildString {
                    append("Lineups not announced yet")
                    lineup.nextRefreshAt?.let { append("\nNext check ${formatLineupTime(it)}") }
                })
            }
            else -> {
                LineupTeamHeader(lineup.away, alignment = Alignment.Start)
                LineupPitch(lineup)
                LineupTeamHeader(lineup.home, alignment = Alignment.End)
                StartingXiSection(lineup)
                BenchSection(lineup)
                UnavailableSection(lineup)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    val metrics = rememberGoalioMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(4))) {
        Text(title, color = Color.White, fontSize = metrics.sp(24), fontWeight = FontWeight.Black)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, color = GoalioColors.TextSecondary, fontSize = metrics.sp(13), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LineupSkeleton() {
    val transition = rememberInfiniteTransition(label = "lineupSkeleton")
    val alpha by transition.animateFloat(
        initialValue = .28f,
        targetValue = .7f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "lineupSkeletonAlpha"
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF666666).copy(alpha = alpha)))
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(132.dp).height(18.dp).clip(CircleShape).background(Color(0xFF777777).copy(alpha = alpha)))
                Box(Modifier.width(90.dp).height(11.dp).clip(CircleShape).background(Color(0xFF555555).copy(alpha = alpha)))
            }
        }
        Surface(color = Color(0xFF176B3A), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFF2D8B52)), modifier = Modifier.fillMaxWidth().height(420.dp)) {
            Box(Modifier.fillMaxSize()) {
                repeat(11) { index ->
                    Box(
                        Modifier
                            .offset(x = ((index % 4) * 70).dp, y = ((index / 4) * 92 + 40).dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = alpha))
                    )
                }
            }
        }
        MatchStateCard("Loading lineup data...")
    }
}

@Composable
private fun MatchDetailSkeleton() {
    val transition = rememberInfiniteTransition(label = "detailSkeleton")
    val alpha by transition.animateFloat(
        initialValue = .28f,
        targetValue = .68f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "detailSkeletonAlpha"
    )
    val shimmer = Color(0xFF6A6A6A).copy(alpha = alpha)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SkeletonTeamBlock(shimmer, Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.width(72.dp).height(12.dp).clip(CircleShape).background(shimmer))
                        Box(Modifier.width(82.dp).height(34.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
                    }
                    SkeletonTeamBlock(shimmer, Modifier.weight(1f))
                }
                Box(Modifier.fillMaxWidth(.72f).height(12.dp).clip(CircleShape).background(shimmer))
                Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(shimmer.copy(alpha = alpha * .8f)))
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(5) {
                Box(Modifier.width(92.dp).height(52.dp).clip(RoundedCornerShape(50)).background(shimmer.copy(alpha = alpha * .7f)))
            }
        }
        repeat(3) {
            Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.width(140.dp).height(16.dp).clip(CircleShape).background(shimmer))
                    Box(Modifier.fillMaxWidth().height(11.dp).clip(CircleShape).background(shimmer.copy(alpha = alpha * .8f)))
                    Box(Modifier.fillMaxWidth(.72f).height(11.dp).clip(CircleShape).background(shimmer.copy(alpha = alpha * .65f)))
                }
            }
        }
    }
}

@Composable
private fun MatchDetailHeroSkeleton() {
    val transition = rememberInfiniteTransition(label = "detailHeroSkeleton")
    val alpha by transition.animateFloat(
        initialValue = .28f,
        targetValue = .68f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "detailHeroSkeletonAlpha"
    )
    val shimmer = Color(0xFF6A6A6A).copy(alpha = alpha)
    Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SkeletonTeamBlock(shimmer, Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.width(72.dp).height(12.dp).clip(CircleShape).background(shimmer))
                    Box(Modifier.width(82.dp).height(34.dp).clip(RoundedCornerShape(8.dp)).background(shimmer))
                }
                SkeletonTeamBlock(shimmer, Modifier.weight(1f))
            }
            Box(Modifier.fillMaxWidth(.72f).height(12.dp).clip(CircleShape).background(shimmer))
            Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(shimmer.copy(alpha = alpha * .8f)))
        }
    }
}

@Composable
private fun MatchDetailTabSkeleton(tab: String) {
    val transition = rememberInfiniteTransition(label = "detailTabSkeleton")
    val alpha by transition.animateFloat(
        initialValue = .28f,
        targetValue = .68f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "detailTabSkeletonAlpha"
    )
    val shimmer = Color(0xFF6A6A6A).copy(alpha = alpha)
    when (tab) {
        "Timeline" -> TimelineSkeleton(shimmer)
        "Stats" -> StatsSkeleton(shimmer)
        "AI Insight" -> SummarySkeleton(shimmer)
        "Lineups" -> LineupSkeleton()
        "Watch" -> WatchSkeleton(shimmer)
        else -> OverviewSkeleton(shimmer)
    }
}

@Composable
private fun OverviewSkeleton(shimmer: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SkeletonCard(shimmer, Modifier.weight(1f).height(86.dp))
            SkeletonCard(shimmer, Modifier.weight(1f).height(86.dp))
        }
        TimelineSkeleton(shimmer, count = 3)
        StatsSkeleton(shimmer)
        SummarySkeleton(shimmer)
    }
}

@Composable
private fun TimelineSkeleton(shimmer: Color, count: Int = 5) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(132.dp).height(20.dp).clip(CircleShape).background(shimmer))
        repeat(count) {
            Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(shimmer))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.fillMaxWidth(.72f).height(13.dp).clip(CircleShape).background(shimmer))
                        Box(Modifier.fillMaxWidth(.48f).height(10.dp).clip(CircleShape).background(shimmer.copy(alpha = .75f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsSkeleton(shimmer: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(124.dp).height(20.dp).clip(CircleShape).background(shimmer))
        repeat(5) {
            Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(42.dp).height(16.dp).clip(CircleShape).background(shimmer))
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.width(42.dp).height(16.dp).clip(CircleShape).background(shimmer))
                    }
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(shimmer.copy(alpha = .75f)))
                }
            }
        }
    }
}

@Composable
private fun SummarySkeleton(shimmer: Color) {
    Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, GoalioColors.Tertiary.copy(alpha = .35f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.width(180.dp).height(21.dp).clip(CircleShape).background(shimmer))
            repeat(5) { index ->
                Box(Modifier.fillMaxWidth(if (index == 4) .62f else 1f).height(12.dp).clip(CircleShape).background(shimmer.copy(alpha = .78f)))
            }
        }
    }
}

@Composable
private fun WatchSkeleton(shimmer: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(190.dp).height(22.dp).clip(CircleShape).background(shimmer))
        SkeletonCard(shimmer, Modifier.fillMaxWidth().height(150.dp))
        SkeletonCard(shimmer, Modifier.fillMaxWidth().height(220.dp))
    }
}

@Composable
private fun SkeletonCard(shimmer: Color, modifier: Modifier) {
    Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth(.62f).height(13.dp).clip(CircleShape).background(shimmer))
            Box(Modifier.fillMaxWidth(.42f).height(10.dp).clip(CircleShape).background(shimmer.copy(alpha = .72f)))
        }
    }
}

@Composable
private fun SkeletonTeamBlock(color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(62.dp).clip(CircleShape).background(color))
        Box(Modifier.width(78.dp).height(13.dp).clip(CircleShape).background(color.copy(alpha = .85f)))
    }
}

@Composable
private fun LineupTeamHeader(team: NormalizedTeamLineupInfo, alignment: Alignment.Horizontal) {
    val metrics = rememberGoalioMetrics()
    val isEnd = alignment == Alignment.End
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isEnd) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
        if (!isEnd) LineupTeamLogo(team)
        if (!isEnd) Spacer(Modifier.width(metrics.dp(12)))
        Column(horizontalAlignment = alignment) {
            Text(team.teamName.orEmpty().ifBlank { "Team" }, color = Color.White, fontSize = metrics.sp(21), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Formation ${team.formation ?: "-"}", color = GoalioColors.TextSecondary, fontSize = metrics.sp(13), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isEnd) Spacer(Modifier.width(metrics.dp(12)))
        if (isEnd) LineupTeamLogo(team)
    }
}

@Composable
private fun LineupTeamLogo(team: NormalizedTeamLineupInfo) {
    val metrics = rememberGoalioMetrics()
    Box(
        Modifier.size(metrics.dp(46)).clip(RoundedCornerShape(metrics.dp(4))).background(Color(0xFF151515)),
        contentAlignment = Alignment.Center
    ) {
        if (!team.teamLogo.isNullOrBlank()) {
            AsyncImage(model = team.teamLogo, contentDescription = team.teamName, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(2.dp))
        } else {
            Text(team.teamName.orEmpty().take(2).uppercase(), color = GoalioColors.Tertiary, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun LineupPitch(lineup: MatchLineupInfo) {
    val metrics = rememberGoalioMetrics()
    Surface(shape = RoundedCornerShape(metrics.dp(14)), color = Color(0xFF176B3A), border = BorderStroke(1.dp, Color(0xFF2D8B52)), modifier = Modifier.fillMaxWidth().height(metrics.dp(560))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize().padding(metrics.dp(10))) {
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
            (lineup.away.startingXI.map { it to Color.White } + lineup.home.startingXI.map { it to GoalioColors.Tertiary }).forEach { (player, border) ->
                PitchPlayerMarker(player, border, maxWidth, maxHeight)
            }
        }
    }
}

@Composable
private fun PitchPlayerMarker(player: PitchLineupPlayerInfo, border: Color, pitchWidth: androidx.compose.ui.unit.Dp, pitchHeight: androidx.compose.ui.unit.Dp) {
    val metrics = rememberGoalioMetrics()
    val marker = metrics.dp(36)
    val x = pitchWidth * ((player.x ?: 50f).coerceIn(6f, 94f) / 100f) - marker / 2
    val y = pitchHeight * ((player.y ?: 50f).coerceIn(3f, 97f) / 100f) - marker / 2
    Surface(color = Color(0xFF111111), shape = CircleShape, border = BorderStroke(2.dp, border), modifier = Modifier.offset(x, y).size(marker)) {
        Box(contentAlignment = Alignment.Center) {
            Text(player.initials(), color = Color.White, fontSize = metrics.sp(11), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun StartingXiSection(lineup: MatchLineupInfo) {
    val metrics = rememberGoalioMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
        Text("STARTING XI", color = GoalioColors.Accent, fontSize = metrics.sp(11), fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(metrics.dp(16)), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(Modifier.fillMaxWidth().background(Color(0xFF141414)).padding(metrics.dp(12)), horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
                    BenchTeamTitle(lineup.home, Modifier.weight(1f))
                    BenchTeamTitle(lineup.away, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(metrics.dp(12)), horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
                    PlayerListColumn(lineup.home.startingXI, Modifier.weight(1f))
                    PlayerListColumn(lineup.away.startingXI, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PlayerListColumn(players: List<PitchLineupPlayerInfo>, modifier: Modifier) {
    val metrics = rememberGoalioMetrics()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(metrics.dp(8))) {
        players.forEach { player ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color.Black, shape = CircleShape, border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = Modifier.size(metrics.dp(25))) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(player.initials(), color = GoalioColors.Tertiary, fontSize = metrics.sp(9), fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.width(metrics.dp(7)))
                Column(Modifier.weight(1f)) {
                    Text(player.name + if (player.captain) " (C)" else "", color = GoalioColors.TextPrimary, fontSize = metrics.sp(11), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    player.position?.let { Text(it, color = GoalioColors.TextTertiary, fontSize = metrics.sp(9), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun BenchSection(lineup: MatchLineupInfo) {
    val metrics = rememberGoalioMetrics()
    if (lineup.home.bench.isEmpty() && lineup.away.bench.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
        Text("BENCH", color = GoalioColors.Accent, fontSize = metrics.sp(11), fontWeight = FontWeight.Black)
        Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(metrics.dp(16)), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(Modifier.fillMaxWidth().background(Color(0xFF141414)).padding(metrics.dp(12)), horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
                    BenchTeamTitle(lineup.home, Modifier.weight(1f))
                    BenchTeamTitle(lineup.away, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().padding(metrics.dp(12)), horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
                    BenchColumn(lineup.home.bench, Modifier.weight(1f))
                    BenchColumn(lineup.away.bench, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BenchTeamTitle(team: NormalizedTeamLineupInfo, modifier: Modifier) {
    val metrics = rememberGoalioMetrics()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LineupTeamLogo(team)
        Spacer(Modifier.width(metrics.dp(8)))
        Text(team.teamName.orEmpty().ifBlank { "Team" }, color = Color.White, fontSize = metrics.sp(12), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BenchColumn(players: List<PitchLineupPlayerInfo>, modifier: Modifier) {
    val metrics = rememberGoalioMetrics()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(metrics.dp(7))) {
        if (players.isEmpty()) {
            Text("No bench listed", color = GoalioColors.TextTertiary, fontSize = metrics.sp(11), fontWeight = FontWeight.SemiBold)
        } else {
            players.forEach { player ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color.Black, shape = CircleShape, border = BorderStroke(1.dp, GoalioColors.Tertiary.copy(alpha = .8f)), modifier = Modifier.size(metrics.dp(25))) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(player.initials(), color = GoalioColors.Tertiary, fontSize = metrics.sp(9), fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(Modifier.width(metrics.dp(7)))
                    Text(player.name, color = GoalioColors.TextPrimary, fontSize = metrics.sp(11), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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
    Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(metrics.dp(18)), border = BorderStroke(1.dp, GoalioColors.Tertiary.copy(alpha = .35f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(metrics.dp(20)), verticalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(metrics.dp(7)).clip(CircleShape).background(GoalioColors.Tertiary))
                Spacer(Modifier.width(metrics.dp(9)))
                Column {
                    Text("Goalio AI Summary", color = Color.White, fontSize = metrics.sp(21), fontWeight = FontWeight.Black)
                    Text("Auto-generated match context", color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.SemiBold)
                }
            }
            HtmlSummaryText(
                html = summary ?: "${detail.homeTeam?.shortName ?: "Home"} and ${detail.awayTeam?.shortName ?: "away"} are being tracked from the live ESPN feed. Detailed AI insight will expand as more match events and stats become available."
            )
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
        Text("Watch & Highlights", color = Color.White, fontSize = metrics.sp(24), fontWeight = FontWeight.Black)
        when {
            loading -> MatchStateCard("Finding official ways to watch...")
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
                MediaGlyph(Icons.Default.PlayArrow, "Live stream")
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
                    Box(Modifier.size(metrics.dp(58)).background(GoalioColors.Tertiary, CircleShape).align(Alignment.Center).clickable { primaryUrl?.let { context.openOfficialUrl(it) } }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = GoalioColors.Primary, modifier = Modifier.size(metrics.dp(34)))
                    }
                }
            }
            Column(Modifier.padding(start = metrics.dp(18), end = metrics.dp(18), bottom = metrics.dp(18)), verticalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MediaGlyph(Icons.Default.Videocam, "Highlights")
                    Spacer(Modifier.width(metrics.dp(12)))
                    Column(Modifier.weight(1f)) {
                        Text("HIGHLIGHTS", color = GoalioColors.Tertiary, fontSize = metrics.sp(11), fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                        Text(if (highlight?.status == "available") "Official match highlights" else if (media?.official?.matchUrl != null) "Official full match available" else "Highlights coming soon", color = GoalioColors.Secondary, fontSize = metrics.sp(19), fontWeight = FontWeight.Black)
                    }
                    MediaBadge((highlight?.status ?: "PENDING").uppercase())
                }
                Text(when { highlight?.status == "available" -> "Published by ${highlight.provider ?: "an official channel"}."; media?.official?.matchUrl != null -> "The verified full match is ready while official highlights are being prepared."; else -> "We'll show the verified official video as soon as it is published." }, color = GoalioColors.TextSecondary, fontSize = metrics.sp(13))
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

@Composable private fun MediaGlyph(icon: ImageVector, description: String) = Box(Modifier.size(42.dp).background(GoalioColors.Surface3, CircleShape), contentAlignment = Alignment.Center) {
    Icon(icon, contentDescription = description, tint = GoalioColors.Tertiary, modifier = Modifier.size(24.dp))
}

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
private fun HeaderIcon(icon: ImageVector, description: String, tint: Color = GoalioColors.Accent, modifier: Modifier = Modifier) {
    Icon(icon, contentDescription = description, tint = tint, modifier = modifier.size(27.dp))
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

private fun PitchLineupPlayerInfo.initials(): String =
    Regex("\\p{L}+")
        .findAll(name)
        .map { it.value.first().uppercaseChar().toString() }
        .take(2)
        .joinToString("")
        .ifBlank { "P" }

private fun ScheduleMatch.officialKickoffDate(): LocalDate? = runCatching {
    OffsetDateTime.parse(kickoff).toLocalDate()
}.getOrNull()

private fun ScheduleMatch.matchesCalendarDate(date: String): Boolean {
    val selected = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
    return officialKickoffDate() == selected || localKickoffDate() == selected
}

private fun matchCalendarStrip(today: LocalDate, selected: LocalDate): List<LocalDate> {
    val visible = (-2..45).map { today.plusDays(it.toLong()) }
    return if (selected in visible) visible else listOf(selected) + visible
}
