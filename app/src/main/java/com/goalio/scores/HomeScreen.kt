package com.goalio.scores

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
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

private val HomeLeagues = listOf(
    "fifa.world",
    "eng.1",
    "esp.1",
    "ita.1",
    "ger.1",
    "fra.1",
    "uefa.champions",
    "uefa.europa"
)

@Composable
fun PersonalizedHomeScreen(
    fallbackName: String?,
    fallbackTeams: Set<String>,
    fallbackPlayers: Set<String>,
    onOpenMatches: () -> Unit,
    onOpenWorldCup: () -> Unit,
    onOpenGames: () -> Unit,
    onOpenMatch: (ScheduleMatch) -> Unit
) {
    val context = LocalContext.current
    val metrics = rememberGoalioMetrics()
    var matches by remember { mutableStateOf(emptyList<ScheduleMatch>()) }
    var standings by remember { mutableStateOf(MatchRepository.cachedStandings(context, "fifa.world")) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val today = remember { LocalDate.now() }
    val fromDate = remember(today) { today.minusDays(30).toString() }
    val toDate = remember(today) { today.plusDays(120).toString() }

    LaunchedEffect(fromDate, toDate) {
        MatchRepository.matchUpdates.collect { canonical ->
            val shared = canonical.values.filter { match ->
                match.kickoff?.take(10)?.let { it >= fromDate && it <= toDate } ?: true
            }
            if (shared.isNotEmpty()) {
                matches = shared.sortedWith(compareBy<ScheduleMatch> { stateRank(it.state) }.thenBy { it.kickoff.orEmpty() })
            }
        }
    }

    LaunchedEffect(fromDate, toDate) {
        matches = MatchRepository.cachedFeed(context, fromDate, toDate)
        loading = matches.isEmpty()
        while (true) {
            errorMessage = null
            runCatching { MatchRepository.refreshFeed(context, fromDate, toDate) }
                .onSuccess { result ->
                    matches = result.matches
                    if (result.scoreChanged) {
                        if (GoalioAppVisibility.isForeground) {
                            android.widget.Toast.makeText(context, "Live score updated", android.widget.Toast.LENGTH_SHORT).show()
                        }
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
    LaunchedEffect(Unit) {
        runCatching { MatchRepository.refreshStandings(context, "fifa.world") }
            .onSuccess { standings = it }
    }

    val liveMatches = matches.filter { it.state == "in" }
    val upcoming = matches.filter { it.state == "pre" }.take(12)
    val todayUpcoming = matches.filter { it.state == "pre" && it.isTodayKickoff(today) }
    val upcomingToday = todayUpcoming.take(3).ifEmpty { upcoming.take(3) }
    val upcomingTitle = if (todayUpcoming.isEmpty() && upcomingToday.isNotEmpty()) "UPCOMING NEXT" else "UPCOMING TODAY"
    val finished = matches.filter { it.state == "post" }.take(8)
    val featured = liveMatches.firstOrNull() ?: upcoming.firstOrNull() ?: finished.firstOrNull()

    GoalioBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            contentPadding = PaddingValues(start = metrics.horizontalPadding, end = metrics.horizontalPadding, top = metrics.dp(20), bottom = metrics.bottomBarPadding),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(20))
        ) {
            item { HomeTopBar(fallbackName) }
            item {
                when {
                    loading -> HomeStateCard("Loading real match data...")
                    errorMessage != null -> HomeStateCard(errorMessage.orEmpty())
                    featured != null -> FeaturedMatchCard(featured, onOpenMatch)
                    else -> HomeStateCard("Looking for the latest fixtures...")
                }
            }
            if (!loading && errorMessage == null && matches.isNotEmpty()) {
                item {
                    SectionHeader("Live Action", "View All", onOpenMatches)
                    Spacer(Modifier.height(12.dp))
                    if (liveMatches.isEmpty()) {
                        MutedPill("No live matches right now")
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(liveMatches.take(3), key = { "${it.league}:${it.matchId}" }) {
                                MatchMiniCard(it, onOpenMatch)
                            }
                        }
                    }
                }
                item {
                    SectionHeader(upcomingTitle, "View All", onOpenMatches, compactTitle = true)
                    Spacer(Modifier.height(12.dp))
                    if (upcomingToday.isEmpty()) {
                        MutedPill("No upcoming fixtures in this window")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            upcomingToday.forEach { ScheduleRow(it, onOpenMatch) }
                        }
                    }
                }
                item { WinProbabilityCard(featured) }
                item { WorldCupHubCard(standings, onOpenWorldCup) }
                item { FunZoneSection() }
            }
        }
            HomeBottomNav(Modifier.align(Alignment.BottomCenter), onOpenMatches, onOpenWorldCup, onOpenGames)
    }
}

@Composable
private fun HomeTopBar(fallbackName: String?) {
    val metrics = rememberGoalioMetrics()
    Row(Modifier.fillMaxWidth().padding(top = metrics.dp(4)), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "GOALIO",
            color = GoalioColors.TextPrimary,
            fontSize = metrics.sp(26),
            fontWeight = FontWeight.Black,
            letterSpacing = if (metrics.compact) 4.sp else 7.sp,
            modifier = Modifier.weight(1f)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(14)), verticalAlignment = Alignment.CenterVertically) {
            HeaderIcon("search")
            HeaderIcon("bell")
            HeaderIcon("gear")
        }
    }
}

@Composable
private fun HeaderIcon(kind: String) {
    Canvas(Modifier.size(24.dp)) {
        val color = GoalioColors.Accent
        when (kind) {
            "search" -> {
                drawCircle(color, radius = size.minDimension * .24f, center = Offset(size.width * .42f, size.height * .42f), style = Stroke(size.minDimension * .11f))
                drawLine(color, Offset(size.width * .62f, size.height * .62f), Offset(size.width * .9f, size.height * .9f), size.minDimension * .11f, StrokeCap.Round)
            }
            "bell" -> {
                drawArc(color, 205f, 130f, false, topLeft = Offset(size.width * .25f, size.height * .18f), size = androidx.compose.ui.geometry.Size(size.width * .5f, size.height * .58f), style = Stroke(size.minDimension * .1f, cap = StrokeCap.Round))
                drawLine(color, Offset(size.width * .25f, size.height * .68f), Offset(size.width * .75f, size.height * .68f), size.minDimension * .1f, StrokeCap.Round)
                drawCircle(color, radius = size.minDimension * .05f, center = Offset(size.width * .5f, size.height * .84f))
            }
            else -> {
                drawCircle(color, radius = size.minDimension * .26f, center = center, style = Stroke(size.minDimension * .1f))
                repeat(8) { index ->
                    val angle = Math.toRadians((index * 45).toDouble())
                    val start = Offset(center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * .36f, center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * .36f)
                    val end = Offset(center.x + kotlin.math.cos(angle).toFloat() * size.minDimension * .46f, center.y + kotlin.math.sin(angle).toFloat() * size.minDimension * .46f)
                    drawLine(color, start, end, size.minDimension * .08f, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun FeaturedMatchCard(match: ScheduleMatch, onOpenMatch: (ScheduleMatch) -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(metrics.dp(10)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF153020), Color(0xFF0B160D), Color(0xFF071007))
                    )
                )
                .padding(metrics.dp(22)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MatchStatusPill(match)
                Spacer(Modifier.weight(1f))
                Text(match.leagueLabel(), color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(metrics.dp(26)))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(match.homeTeam, Modifier.weight(1f))
                Text(scoreLine(match), color = GoalioColors.TextPrimary, fontSize = metrics.sp(42), fontWeight = FontWeight.Black)
                TeamBadge(match.awayTeam, Modifier.weight(1f))
            }
            Spacer(Modifier.height(metrics.dp(28)))
            Button(
                onClick = { onOpenMatch(match) },
                colors = ButtonDefaults.buttonColors(containerColor = GoalioColors.Accent, contentColor = GoalioColors.TextPrimary),
                shape = RoundedCornerShape(metrics.dp(12)),
                modifier = Modifier.fillMaxWidth().height(metrics.dp(54))
            ) {
                Text("Match Details", fontSize = metrics.sp(16), fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun TeamBadge(team: MatchTeamInfo?, modifier: Modifier = Modifier) {
    val metrics = rememberGoalioMetrics()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(metrics.dp(70)).clip(CircleShape).background(GoalioColors.Surface2), contentAlignment = Alignment.Center) {
            if (!team?.logo.isNullOrBlank()) {
                AsyncImage(team?.logo, contentDescription = team?.name, contentScale = ContentScale.Fit, modifier = Modifier.size(metrics.dp(56)))
            } else {
                Text(team?.abbreviation ?: "TBD", color = GoalioColors.TextPrimary, fontWeight = FontWeight.Black, fontSize = metrics.sp(15))
            }
        }
        Spacer(Modifier.height(metrics.dp(9)))
        Text(
            team?.abbreviation ?: team?.shortName ?: team?.name ?: "TBD",
            color = GoalioColors.TextPrimary,
            fontSize = metrics.sp(19),
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit, compactTitle: Boolean = false) {
    val metrics = rememberGoalioMetrics()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            color = GoalioColors.TextPrimary,
            fontSize = metrics.sp(if (compactTitle) 13 else 23),
            fontWeight = FontWeight.Black,
            letterSpacing = if (compactTitle) 2.sp else 0.sp,
            modifier = Modifier.weight(1f)
        )
        Text(action, color = GoalioColors.TextTertiary, fontSize = metrics.sp(13), fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onAction))
    }
}

@Composable
private fun MatchMiniCard(match: ScheduleMatch, onOpenMatch: (ScheduleMatch) -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(
        color = GoalioColors.Surface1,
        shape = RoundedCornerShape(metrics.dp(10)),
        border = BorderStroke(1.dp, GoalioColors.CardBorder),
        modifier = Modifier.width(metrics.dp(210)).height(metrics.dp(136)).clickable { onOpenMatch(match) }
    ) {
        Column(Modifier.padding(metrics.dp(15)), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MatchStatusPill(match)
                Spacer(Modifier.weight(1f))
                Text(match.leagueLabel(short = true), color = GoalioColors.TextTertiary, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
            }
            TeamScoreLine(match.homeTeam)
            TeamScoreLine(match.awayTeam)
        }
    }
}

@Composable
private fun ScheduleRow(match: ScheduleMatch, onOpenMatch: (ScheduleMatch) -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(
        color = Color(0xFF1F1F21),
        shape = RoundedCornerShape(metrics.dp(11)),
        modifier = Modifier.fillMaxWidth().clickable { onOpenMatch(match) }
    ) {
        Row(Modifier.padding(horizontal = metrics.dp(16), vertical = metrics.dp(16)), verticalAlignment = Alignment.CenterVertically) {
            Text(formatKickoff(match.kickoff), color = GoalioColors.TextSecondary, fontSize = metrics.sp(17), fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(metrics.dp(18)))
            Text(match.compactName(), color = GoalioColors.TextSecondary, fontSize = metrics.sp(17), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(">", color = GoalioColors.TextSecondary, fontSize = metrics.sp(26), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun WinProbabilityCard(match: ScheduleMatch?) {
    val metrics = rememberGoalioMetrics()
    val homeName = match?.homeTeam?.abbreviation ?: match?.homeTeam?.shortName ?: match?.homeTeam?.name ?: "HOME"
    val awayName = match?.awayTeam?.abbreviation ?: match?.awayTeam?.shortName ?: match?.awayTeam?.name ?: "AWAY"
    val homeProbability = match.winProbability()
    Surface(
        color = Color(0xFF1E1E20),
        shape = RoundedCornerShape(metrics.dp(10)),
        border = BorderStroke(1.dp, GoalioColors.TextTertiary.copy(alpha = .55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(metrics.dp(20))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrendIcon(Modifier.size(metrics.dp(25)), Color.White)
                Spacer(Modifier.width(metrics.dp(10)))
                Text("Win Probability", color = GoalioColors.TextPrimary, fontSize = metrics.sp(23), fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(metrics.dp(20)))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(homeName.uppercase().take(12), color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text(awayName.uppercase().take(12), color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(metrics.dp(10)))
            Row(Modifier.fillMaxWidth().height(metrics.dp(12)).clip(RoundedCornerShape(50))) {
                Box(Modifier.weight(homeProbability).fillMaxSize().background(Color.White))
                Box(Modifier.weight(100f - homeProbability).fillMaxSize().background(Color(0xFFE22D13)))
            }
            Spacer(Modifier.height(metrics.dp(20)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${homeProbability.toInt()}%", color = GoalioColors.TextSecondary, fontSize = metrics.sp(18))
                Spacer(Modifier.width(metrics.dp(18)))
                Text("${(100f - homeProbability).toInt()}%", color = Color(0xFFE22D13), fontSize = metrics.sp(18))
                Spacer(Modifier.weight(1f))
                Surface(color = GoalioColors.Accent, shape = RoundedCornerShape(50)) {
                    Text("View Analysis", color = Color.White, fontSize = metrics.sp(16), modifier = Modifier.padding(horizontal = metrics.dp(26), vertical = metrics.dp(12)))
                }
            }
        }
    }
}

@Composable
private fun WorldCupHubCard(standings: LeagueStandings?, onViewHub: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    val rows = remember(standings) { standings?.teams.orEmpty().sortedWith(compareBy<StandingTeamInfo> { it.group ?: "" }.thenBy { it.rank ?: 999 }).take(6) }
    Surface(
        color = Color(0xFF202022),
        shape = RoundedCornerShape(metrics.dp(10)),
        border = BorderStroke(1.dp, GoalioColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(GoalioColors.Accent).padding(horizontal = metrics.dp(18), vertical = metrics.dp(16)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("World Cup Hub", color = Color.White, fontSize = metrics.sp(22), fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text("View Hub", color = Color.White, fontSize = metrics.sp(13), fontWeight = FontWeight.Black, modifier = Modifier.clickable(onClick = onViewHub))
            }
            Column(Modifier.padding(metrics.dp(18)), verticalArrangement = Arrangement.spacedBy(metrics.dp(13))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Rank", color = Color(0xFFE4D7BC), fontSize = metrics.sp(15), fontWeight = FontWeight.Black, modifier = Modifier.width(metrics.dp(50)))
                    Text("Team", color = Color(0xFFE4D7BC), fontSize = metrics.sp(15), fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    Text("Stage", color = Color(0xFFE4D7BC), fontSize = metrics.sp(15), fontWeight = FontWeight.Black, modifier = Modifier.width(metrics.dp(62)))
                    Text("PTS", color = Color(0xFFE4D7BC), fontSize = metrics.sp(15), fontWeight = FontWeight.Black)
                }
                if (rows.isEmpty()) {
                    Text("Standings will appear when ESPN publishes the current table.", color = GoalioColors.TextSecondary, fontSize = metrics.sp(14), lineHeight = metrics.sp(20))
                } else {
                    rows.forEach { team ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text((team.rank ?: rows.indexOf(team) + 1).toString(), color = GoalioColors.TextSecondary, fontSize = metrics.sp(16), fontWeight = FontWeight.Bold, modifier = Modifier.width(metrics.dp(50)))
                            TeamStandingName(team, Modifier.weight(1f))
                            Text(team.stageLabel(), color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(metrics.dp(62)))
                            Text((team.points ?: 0).toString(), color = GoalioColors.TextSecondary, fontSize = metrics.sp(16), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamStandingName(team: StandingTeamInfo, modifier: Modifier = Modifier) {
    val metrics = rememberGoalioMetrics()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (!team.logo.isNullOrBlank()) {
            AsyncImage(team.logo, contentDescription = team.name, contentScale = ContentScale.Fit, modifier = Modifier.size(metrics.dp(18)))
        } else {
            Box(Modifier.size(metrics.dp(14)).clip(CircleShape).background(GoalioColors.Accent))
        }
        Spacer(Modifier.width(metrics.dp(10)))
        Text(team.name, color = GoalioColors.TextSecondary, fontSize = metrics.sp(16), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FunZoneSection() {
    val metrics = rememberGoalioMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
        Text("Fun Zone", color = GoalioColors.TextPrimary, fontSize = metrics.sp(23), fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
            FunTile("Daily Trivia", Modifier.weight(1f))
            FunTile("Guess Player", Modifier.weight(1f))
        }
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(metrics.dp(10)),
            modifier = Modifier.fillMaxWidth().height(metrics.dp(150))
        ) {
            Box(
                Modifier.background(Brush.horizontalGradient(listOf(Color(0xFFD37500), Color(0xFF141108)))).padding(metrics.dp(22))
            ) {
                Text("Roll & Win\nEarn points for every goal predicted", color = Color.White, fontSize = metrics.sp(18), lineHeight = metrics.sp(28), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterStart))
            }
        }
    }
}

@Composable
private fun FunTile(label: String, modifier: Modifier = Modifier) {
    val metrics = rememberGoalioMetrics()
    Surface(color = Color.Black, shape = RoundedCornerShape(metrics.dp(10)), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = modifier.height(metrics.dp(104))) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = GoalioColors.TextSecondary, fontSize = metrics.sp(16), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun TrendIcon(modifier: Modifier, color: Color) = Canvas(modifier) {
    drawLine(color, Offset(size.width * .12f, size.height * .7f), Offset(size.width * .34f, size.height * .45f), size.minDimension * .11f, StrokeCap.Round)
    drawLine(color, Offset(size.width * .34f, size.height * .45f), Offset(size.width * .52f, size.height * .58f), size.minDimension * .11f, StrokeCap.Round)
    drawLine(color, Offset(size.width * .52f, size.height * .58f), Offset(size.width * .82f, size.height * .22f), size.minDimension * .11f, StrokeCap.Round)
    drawLine(color, Offset(size.width * .82f, size.height * .22f), Offset(size.width * .88f, size.height * .42f), size.minDimension * .08f, StrokeCap.Round)
    drawLine(color, Offset(size.width * .82f, size.height * .22f), Offset(size.width * .62f, size.height * .22f), size.minDimension * .08f, StrokeCap.Round)
}

@Composable
private fun TeamScoreLine(team: MatchTeamInfo?) {
    val metrics = rememberGoalioMetrics()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            team?.name ?: "TBD",
            color = GoalioColors.TextSecondary,
            fontSize = metrics.sp(15),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        team?.score?.let {
            Text(it.toString(), color = GoalioColors.TextPrimary, fontSize = metrics.sp(16), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun MatchStatusPill(match: ScheduleMatch) {
    val metrics = rememberGoalioMetrics()
    var now by remember(match.matchId, match.kickoff) { mutableStateOf(Instant.now()) }
    val liveAnchor = remember(match.status, match.statusDescription) { Instant.now() }
    LaunchedEffect(match.matchId, match.kickoff, match.state) {
        if (match.state !in setOf("pre", "in")) return@LaunchedEffect
        while (true) {
            now = Instant.now()
            delay(1_000)
        }
    }
    val countdown = remember(match.kickoff, now) { match.countdownLabel(now) }
    val liveClock = remember(match.status, match.statusDescription, now) { match.liveClockLabel(liveAnchor, now) }
    val color = if (countdown != null) GoalioColors.Accent else statusColor(match.state)
    val label = countdown ?: liveClock ?: match.statusLabel().uppercase()
    Surface(color = GoalioColors.Surface2, shape = RoundedCornerShape(50), border = BorderStroke(1.dp, color)) {
        Text(
            label,
            color = color,
            fontSize = metrics.sp(11),
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = metrics.dp(9), vertical = metrics.dp(5))
        )
    }
}

@Composable
private fun HomeStateCard(text: String, action: String? = null) {
    val metrics = rememberGoalioMetrics()
    Surface(
        color = GoalioColors.Surface1,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, GoalioColors.CardBorder),
        modifier = Modifier.fillMaxWidth().height(metrics.dp(190))
    ) {
        Column(Modifier.padding(metrics.dp(22)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(text, color = GoalioColors.TextSecondary, fontSize = metrics.sp(16), fontWeight = FontWeight.Bold)
            if (action != null) {
                Spacer(Modifier.height(metrics.dp(14)))
                Text(action, color = GoalioColors.Accent, fontSize = metrics.sp(13), fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun MutedPill(text: String) {
    val metrics = rememberGoalioMetrics()
    Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = GoalioColors.TextTertiary, fontSize = metrics.sp(15), modifier = Modifier.padding(metrics.dp(16)))
    }
}

@Composable
private fun HomeBottomNav(modifier: Modifier = Modifier, onOpenMatches: () -> Unit, onOpenWorldCup: () -> Unit, onOpenGames: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(
        color = GoalioColors.Navigation,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        border = BorderStroke(1.dp, GoalioColors.CardBorder),
        modifier = modifier.fillMaxWidth().padding(horizontal = metrics.dp(10), vertical = metrics.dp(8))
    ) {
        Row(Modifier.padding(metrics.dp(8)), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            BottomTab("Home", true) {}
            BottomTab("Matches", false, onOpenMatches)
            BottomTab("World Cup", false, onOpenWorldCup)
            BottomTab("Games", false, onOpenGames)
        }
    }
}

@Composable
private fun RowScope.BottomTab(label: String, selected: Boolean, onClick: () -> Unit = {}) {
    val metrics = rememberGoalioMetrics()
    val fg = if (selected) GoalioColors.TextPrimary else GoalioColors.InactiveIcon
    Surface(
        color = if (selected) GoalioColors.Accent else Color.Transparent,
        shape = RoundedCornerShape(50),
        modifier = Modifier.weight(1f).height(metrics.dp(54)).clickable(onClick = onClick)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, color = fg, fontSize = metrics.sp(11), fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

private fun ScheduleMatch.statusLabel(): String = when (state) {
    "pre" -> statusDescription ?: status ?: "Upcoming"
    "in" -> statusDescription ?: status ?: "Live"
    "post" -> statusDescription ?: status ?: "Finished"
    else -> statusDescription ?: status ?: "Match"
}

private fun ScheduleMatch.compactName(): String {
    val home = homeTeam?.shortName ?: homeTeam?.name ?: "TBD"
    val away = awayTeam?.shortName ?: awayTeam?.name ?: "TBD"
    return "$home vs $away"
}

private fun ScheduleMatch.isTodayKickoff(today: LocalDate): Boolean {
    val localDate = runCatching {
        OffsetDateTime.parse(kickoff)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalDate()
    }.getOrNull()
    return localDate == today
}

private fun ScheduleMatch?.winProbability(): Float {
    if (this == null) return 50f
    val home = homeTeam?.score ?: return 50f
    val away = awayTeam?.score ?: return 50f
    return (50f + (home - away) * 10f).coerceIn(25f, 75f)
}

private fun StandingTeamInfo.stageLabel(): String {
    val source = stage ?: group
    if (source.isNullOrBlank()) return "-"
    val normalized = source
        .replace("Group", "G", ignoreCase = true)
        .replace("Round", "R", ignoreCase = true)
        .replace(" ", "")
    return normalized.take(8).uppercase()
}

private fun ScheduleMatch.leagueLabel(short: Boolean = false): String = when (league) {
    "fifa.world" -> if (short) "WC" else "WORLD CUP"
    "eng.1" -> if (short) "EPL" else "PREMIER LEAGUE"
    "esp.1" -> if (short) "LALIGA" else "LALIGA"
    "ita.1" -> if (short) "SERIE A" else "SERIE A"
    "ger.1" -> if (short) "BUND" else "BUNDESLIGA"
    "fra.1" -> if (short) "L1" else "LIGUE 1"
    "uefa.champions" -> if (short) "UCL" else "CHAMPIONS LEAGUE"
    "uefa.europa" -> if (short) "UEL" else "EUROPA LEAGUE"
    else -> league.uppercase()
}

private fun scoreLine(match: ScheduleMatch): String {
    val home = match.homeTeam?.score
    val away = match.awayTeam?.score
    return if (home == null || away == null) "v" else "$home - $away"
}

private fun formatKickoff(value: String?): String {
    if (value.isNullOrBlank()) return "--:--"
    return runCatching {
        OffsetDateTime.parse(value)
            .atZoneSameInstant(ZoneId.systemDefault())
            .toLocalTime()
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault(value.take(5))
}

private fun ScheduleMatch.countdownLabel(now: Instant): String? {
    if (state != "pre" || kickoff.isNullOrBlank()) return null
    val kickoffInstant = runCatching { OffsetDateTime.parse(kickoff).toInstant() }.getOrNull() ?: return null
    val remaining = Duration.between(now, kickoffInstant)
    if (remaining.isNegative || remaining.isZero) return "STARTING"
    val hours = remaining.toHours()
    val minutes = remaining.toMinutes() % 60
    val seconds = remaining.seconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun ScheduleMatch.liveClockLabel(anchor: Instant, now: Instant): String? {
    if (state != "in") return null
    val source = "${status.orEmpty()} ${statusDescription.orEmpty()}"
    val minute = Regex("(\\d{1,3})(?:\\+\\d+)?['’]?").find(source)?.groupValues?.get(1)?.toLongOrNull()
        ?: return "LIVE ${status ?: statusDescription.orEmpty()}".trim()
    val elapsed = Duration.between(anchor, now).seconds.coerceAtLeast(0)
    return "LIVE ${minute + elapsed / 60}'%02d\"".format(elapsed % 60)
}

private fun statusColor(state: String?): Color = when (state) {
    "in" -> GoalioColors.Live
    "pre" -> GoalioColors.Upcoming
    "post" -> GoalioColors.Finished
    else -> GoalioColors.TextTertiary
}
