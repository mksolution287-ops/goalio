package zero.ramjikvarosai.hirebazzar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import zero.ramjikvarosai.hirebazzar.ui.theme.GoalioColors
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import zero.ramjikvarosai.hirebazzar.components.AppInstallNativeAdCard

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

private data class HomeMatchBuckets(
    val liveMatches: List<ScheduleMatch>,
    val upcomingToday: List<ScheduleMatch>,
    val upcomingTitle: String,
    val featured: ScheduleMatch?
)

@Composable
fun PersonalizedHomeScreen(
    fallbackName: String?,
    fallbackTeams: Set<String>,
    fallbackPlayers: Set<String>,
    onOpenMatches: () -> Unit,
    onOpenWorldCup: () -> Unit,
    onOpenGames: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMatch: (ScheduleMatch, String) -> Unit
) {
    val context = LocalContext.current
    val metrics = rememberGoalioMetrics()
    var matches by remember { mutableStateOf(emptyList<ScheduleMatch>()) }
    var standings by remember { mutableStateOf(MatchRepository.cachedStandings(context, "fifa.world")) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }
    var pinnedLiveMatch by remember { mutableStateOf<ScheduleMatch?>(null) }
    var livePinnedAt by remember { mutableStateOf<Instant?>(null) }
    val today = remember(now) { LocalDate.now() }
    val fromDate = remember(today) { today.minusDays(30).toString() }
    val toDate = remember(today) { today.plusDays(120).toString() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = Instant.now()
        }
    }
    LaunchedEffect(fromDate, toDate) {
        MatchRepository.matchUpdates.collect { canonical ->
            val shared = canonical.values.filter { match ->
                match.localKickoffDate()?.toString()?.let { it >= fromDate && it <= toDate } ?: true
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
                    if (result.matches.isNotEmpty()) {
                        matches = result.matches
                    } else {
                        val cached = MatchRepository.cachedFeed(context, fromDate, toDate)
                        if (cached.isNotEmpty()) matches = cached
                    }
                    if (result.scoreChanged) {
                        if (GoalioAppVisibility.isForeground) {
                            android.widget.Toast.makeText(context, "Live score updated", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                    GoalioMatchNotifier.notifyBackgroundEvents(context, result.notificationEvents)
                }
                .onFailure {
                    errorMessage = if (matches.isEmpty()) {
                        "Could not load matches. Check your internet connection and retry later."
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

    val detectedLiveMatches = remember(matches, now) {
        matches.filter { it.isHomeLiveAt(now) }
            .sortedBy { it.kickoff.orEmpty() }
    }
    LaunchedEffect(matches, detectedLiveMatches, now) {
        val pinned = pinnedLiveMatch
        if (pinned == null) {
            detectedLiveMatches.firstOrNull()?.let {
                pinnedLiveMatch = it
                livePinnedAt = now
            }
            return@LaunchedEffect
        }

        val refreshed = matches.firstOrNull { it.homeMatchKey() == pinned.homeMatchKey() }
        val withinPinLimit = livePinnedAt?.let { Duration.between(it, now) < Duration.ofHours(6) } ?: false
        val keepPinned = when {
            refreshed?.isHomeTerminal() == true -> false
            refreshed != null -> refreshed.canRemainHomeLiveAt(now)
            else -> withinPinLimit && pinned.canRemainHomeLiveAt(now)
        }

        if (keepPinned) {
            if (refreshed != null) pinnedLiveMatch = refreshed
        } else {
            val replacement = detectedLiveMatches.firstOrNull { it.homeMatchKey() != pinned.homeMatchKey() }
            pinnedLiveMatch = replacement
            livePinnedAt = replacement?.let { now }
        }
    }

    val buckets = remember(matches, today, now, pinnedLiveMatch) {
        val todayMatches = matches.filter { it.isTodayKickoff(today) }
        val refreshedPinned = pinnedLiveMatch?.let { pinned ->
            matches.firstOrNull { it.homeMatchKey() == pinned.homeMatchKey() } ?: pinned
        }?.takeIf { it.canRemainHomeLiveAt(now) }
        val liveMatches = buildList {
            refreshedPinned?.let(::add)
            matches.asSequence()
                .filter { it.isHomeLiveAt(now) }
                .filter { candidate -> candidate.homeMatchKey() != refreshedPinned?.homeMatchKey() }
                .sortedBy { it.kickoff.orEmpty() }
                .forEach(::add)
        }
        val nearLimit = today.plusDays(7)
        val upcoming = matches
            .filter { match ->
                match.state == "pre" && !match.isHomeLiveAt(now) &&
                    match.localKickoffDate()?.let { date -> !date.isBefore(today) && !date.isAfter(nearLimit) } == true
            }
            .sortedBy { it.kickoff.orEmpty() }
            .take(6)
        val todayUpcoming = todayMatches.filter { it.state == "pre" && !it.isHomeLiveAt(now) }.sortedBy { it.kickoff.orEmpty() }
        val upcomingToday = todayUpcoming.take(3).ifEmpty { upcoming.take(3) }
        val upcomingTitle = if (todayUpcoming.isEmpty() && upcomingToday.isNotEmpty()) "NEXT 7 DAYS" else "UPCOMING TODAY"
        val finished = todayMatches.filter { it.state == "post" }.take(3)
        val recent = matches.filter { it.state == "post" }.sortedByDescending { it.kickoff.orEmpty() }.take(3)
        val featured = liveMatches.firstOrNull()
            ?: todayUpcoming.firstOrNull()
            ?: upcoming.firstOrNull()
            ?: finished.firstOrNull()
            ?: recent.firstOrNull()
        HomeMatchBuckets(liveMatches, upcomingToday, upcomingTitle, featured)
    }

    GoalioBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            contentPadding = PaddingValues(start = metrics.horizontalPadding, end = metrics.horizontalPadding, top = metrics.dp(20), bottom = metrics.bottomBarPadding),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(20))
        ) {
            item { GoalioTopBar(onSettings = onOpenSettings) }
            item {
                when {
                    loading -> HomeSkeleton()
                    errorMessage != null -> HomeStateCard(errorMessage.orEmpty())
                    buckets.featured != null -> FeaturedMatchCard(buckets.featured) { match -> onOpenMatch(match, "Overview") }
                    else -> HomeStateCard("Looking for the latest fixtures...")
                }
            }
            if (!loading && errorMessage == null) {
                item {
                    SectionHeader("Live Action", "View All", onOpenMatches)
                    Spacer(Modifier.height(12.dp))
                    if (buckets.liveMatches.isEmpty()) {
                        MutedPill("No live matches right now")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            buckets.liveMatches.take(3).forEach {
                                MatchMiniCard(it, Modifier.fillMaxWidth()) { match -> onOpenMatch(match, "Overview") }
                            }
                        }
                    }
                }
                item {
                    SectionHeader(buckets.upcomingTitle, "View All", onOpenMatches, compactTitle = true)
                    Spacer(Modifier.height(12.dp))
                    if (buckets.upcomingToday.isEmpty()) {
                        MutedPill("No upcoming fixtures in this window")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            buckets.upcomingToday.forEach { ScheduleRow(it) { match -> onOpenMatch(match, "Overview") } }
                        }
                    }
                }
                item { AppInstallNativeAdCard() }
                if (buckets.featured != null) {
                    item { WinProbabilityCard(buckets.featured) { onOpenMatch(buckets.featured, "Stats") } }
                }
                item { WorldCupHubCard(standings, onOpenWorldCup) }
                item { FunZoneSection(onOpenGames) }
            }
        }
        GoalioBottomBar(Modifier.align(Alignment.BottomCenter), "Home", {}, onOpenMatches, onOpenWorldCup, onOpenGames)
    }
}

@Composable
private fun HomeTopBar(fallbackName: String?) {
    val metrics = rememberGoalioMetrics()
    Row(Modifier.fillMaxWidth().padding(top = metrics.dp(4)), verticalAlignment = Alignment.CenterVertically) {
        Text(
            APP_DISPLAY_NAME,
            color = GoalioColors.TextPrimary,
            fontSize = metrics.sp(if (metrics.compact) 12 else 14),
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp,
            lineHeight = metrics.sp(if (metrics.compact) 14 else 16),
            maxLines = 2,
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
        shape = RoundedCornerShape(metrics.dp(16)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(R.drawable.foot_optimized),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
            Column(
                Modifier.padding(metrics.dp(22)),
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
                    colors = ButtonDefaults.buttonColors(containerColor = GoalioColors.Accent, contentColor = Color.White),
                    shape = RoundedCornerShape(metrics.dp(16)),
                    modifier = Modifier.fillMaxWidth().height(metrics.dp(54))
                ) {
                    Text(trans("Match Details"), fontSize = metrics.sp(16), fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun HomeSkeleton() {
    val metrics = rememberGoalioMetrics()
    val transition = rememberInfiniteTransition(label = "homeSkeleton")
    val alpha by transition.animateFloat(
        initialValue = .28f,
        targetValue = .72f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "homeSkeletonAlpha"
    )
    val shimmer = Color(0xFF6A6A6A).copy(alpha = alpha)
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
        SkeletonHero(shimmer)
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
            SkeletonBlock(Modifier.weight(1f).height(metrics.dp(92)), RoundedCornerShape(metrics.dp(12)), shimmer)
            SkeletonBlock(Modifier.weight(1f).height(metrics.dp(92)), RoundedCornerShape(metrics.dp(12)), shimmer.copy(alpha = alpha * .78f))
        }
        SkeletonBlock(Modifier.fillMaxWidth().height(metrics.dp(148)), RoundedCornerShape(metrics.dp(12)), shimmer)
        SkeletonBlock(Modifier.fillMaxWidth().height(metrics.dp(210)), RoundedCornerShape(metrics.dp(12)), shimmer.copy(alpha = alpha * .82f))
    }
}

@Composable
private fun SkeletonHero(shimmer: Color) {
    val metrics = rememberGoalioMetrics()
    Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(metrics.dp(16)), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = Modifier.fillMaxWidth().height(metrics.dp(260))) {
        Column(Modifier.padding(metrics.dp(22)), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(metrics.dp(72)).height(metrics.dp(22)).clip(RoundedCornerShape(50)).background(shimmer))
                Spacer(Modifier.weight(1f))
                Box(Modifier.width(metrics.dp(86)).height(metrics.dp(14)).clip(RoundedCornerShape(50)).background(shimmer.copy(alpha = .75f)))
            }
            Spacer(Modifier.height(metrics.dp(34)))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SkeletonTeam(shimmer, Modifier.weight(1f))
                Box(Modifier.width(metrics.dp(64)).height(metrics.dp(44)).clip(RoundedCornerShape(metrics.dp(8))).background(shimmer))
                SkeletonTeam(shimmer, Modifier.weight(1f))
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth().height(metrics.dp(54)).clip(RoundedCornerShape(metrics.dp(16))).background(shimmer.copy(alpha = .85f)))
        }
    }
}

@Composable
private fun SkeletonTeam(shimmer: Color, modifier: Modifier) {
    val metrics = rememberGoalioMetrics()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(metrics.dp(62)).clip(CircleShape).background(shimmer))
        Spacer(Modifier.height(metrics.dp(10)))
        Box(Modifier.width(metrics.dp(64)).height(metrics.dp(14)).clip(RoundedCornerShape(50)).background(shimmer.copy(alpha = .7f)))
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier, shape: RoundedCornerShape, shimmer: Color) {
    Surface(color = GoalioColors.Surface1, shape = shape, border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth(.55f).height(14.dp).clip(RoundedCornerShape(50)).background(shimmer))
            Box(Modifier.fillMaxWidth(.82f).height(11.dp).clip(RoundedCornerShape(50)).background(shimmer.copy(alpha = .72f)))
            Box(Modifier.fillMaxWidth(.68f).height(11.dp).clip(RoundedCornerShape(50)).background(shimmer.copy(alpha = .58f)))
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
            trans(title),
            color = GoalioColors.TextPrimary,
            fontSize = metrics.sp(if (compactTitle) 13 else 23),
            fontWeight = FontWeight.Black,
            letterSpacing = if (compactTitle) 2.sp else 0.sp,
            modifier = Modifier.weight(1f)
        )
        Text(trans(action), color = GoalioColors.TextTertiary, fontSize = metrics.sp(13), fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onAction))
    }
}

@Composable
private fun MatchMiniCard(match: ScheduleMatch, modifier: Modifier = Modifier, onOpenMatch: (ScheduleMatch) -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(
        color = GoalioColors.Surface1,
        shape = RoundedCornerShape(metrics.dp(10)),
        border = BorderStroke(1.dp, GoalioColors.CardBorder),
        modifier = modifier.height(metrics.dp(136)).clickable { onOpenMatch(match) }
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .width(metrics.dp(4))
                    .fillMaxHeight()
                    .background(GoalioColors.Accent)
            )
            Column(Modifier.padding(metrics.dp(15)).weight(1f), verticalArrangement = Arrangement.SpaceBetween) {
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
}

@Composable
private fun ScheduleRow(match: ScheduleMatch, onOpenMatch: (ScheduleMatch) -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(
        color = GoalioColors.Surface1,
        shape = RoundedCornerShape(metrics.dp(12)),
        border = BorderStroke(1.dp, GoalioColors.CardBorder),
        modifier = Modifier.fillMaxWidth().clickable { onOpenMatch(match) }
    ) {
        Row(Modifier.padding(horizontal = metrics.dp(16), vertical = metrics.dp(16)), verticalAlignment = Alignment.CenterVertically) {
            Text(formatKickoff(match.kickoff), color = GoalioColors.TextSecondary, fontSize = metrics.sp(17), fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(metrics.dp(18)))
            Text(match.compactName(), color = GoalioColors.TextSecondary, fontSize = metrics.sp(17), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("›", color = GoalioColors.TextSecondary, fontSize = metrics.sp(26), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun WinProbabilityHeader() {
    val metrics = rememberGoalioMetrics()
    Row(verticalAlignment = Alignment.CenterVertically) {
        TrendIcon(Modifier.size(metrics.dp(25)), Color.White)
        Spacer(Modifier.width(metrics.dp(10)))
        Text(trans("Win Probability"), color = GoalioColors.TextPrimary, fontSize = metrics.sp(23), fontWeight = FontWeight.Black)
    }
}

@Composable
private fun WinProbabilityBar(homeProbability: Float, homeName: String, awayName: String) {
    val metrics = rememberGoalioMetrics()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(homeName.uppercase().take(12), color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        Text(awayName.uppercase().take(12), color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
    }
    Spacer(Modifier.height(metrics.dp(10)))
    Row(Modifier.fillMaxWidth().height(metrics.dp(12)).clip(RoundedCornerShape(50))) {
        Box(Modifier.weight(homeProbability).fillMaxSize().background(Color.White))
        Box(Modifier.weight(100f - homeProbability).fillMaxSize().background(GoalioColors.Accent))
    }
}

@Composable
private fun WinProbabilityCard(match: ScheduleMatch?, onViewAnalysis: () -> Unit) {
    val context = LocalContext.current
    val metrics = rememberGoalioMetrics()
    var detail by remember(match?.league, match?.matchId) { mutableStateOf(match?.let { MatchRepository.cachedDetail(context, it.league, it.matchId) }) }
    LaunchedEffect(match?.league, match?.matchId, match?.state) {
        val current = match ?: return@LaunchedEffect
        detail = MatchRepository.cachedDetail(context, current.league, current.matchId)
        while (true) {
            runCatching { MatchRepository.refreshDetail(context, current.league, current.matchId) }
                .onSuccess { detail = it }
            if (current.state == "post") break
            delay(MatchRepository.nextRefreshDelayMillis(listOf(current)))
        }
    }
    val homeName = match?.homeTeam?.abbreviation ?: match?.homeTeam?.shortName ?: match?.homeTeam?.name ?: "HOME"
    val awayName = match?.awayTeam?.abbreviation ?: match?.awayTeam?.shortName ?: match?.awayTeam?.name ?: "AWAY"
    val homeProbability = detail?.sharedHomeWinProbability() ?: match?.sharedHomeWinProbability() ?: 50f
    Surface(
        color = GoalioColors.Surface1,
        shape = RoundedCornerShape(metrics.dp(10)),
        border = BorderStroke(1.dp, GoalioColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(metrics.dp(20))) {
            WinProbabilityHeader()
            Spacer(Modifier.height(metrics.dp(20)))
            WinProbabilityBar(homeProbability, homeName, awayName)
            Spacer(Modifier.height(metrics.dp(20)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${homeProbability.toInt()}%", color = GoalioColors.TextSecondary, fontSize = metrics.sp(18))
                Spacer(Modifier.width(metrics.dp(18)))
                Text("${(100f - homeProbability).toInt()}%", color = GoalioColors.Accent, fontSize = metrics.sp(18))
                Spacer(Modifier.weight(1f))
                Surface(color = Color.Transparent, border = BorderStroke(1.dp, GoalioColors.Accent), shape = RoundedCornerShape(50), modifier = Modifier.clickable(onClick = onViewAnalysis)) {
                    Text(trans("View Analysis"), color = GoalioColors.Accent, fontSize = metrics.sp(16), modifier = Modifier.padding(horizontal = metrics.dp(26), vertical = metrics.dp(12)))
                }
            }
        }
    }
}

@Composable
private fun GlobeIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawCircle(Color.White, radius = size.minDimension * 0.42f, style = Stroke(1.5.dp.toPx()))
        drawLine(Color.White, Offset(size.width * 0.12f, size.height * 0.5f), Offset(size.width * 0.88f, size.height * 0.5f), 1.5.dp.toPx())
        drawArc(Color.White, startAngle = -45f, sweepAngle = 90f, useCenter = false, style = Stroke(1.5.dp.toPx()))
        drawArc(Color.White, startAngle = 135f, sweepAngle = 90f, useCenter = false, style = Stroke(1.5.dp.toPx()))
    }
}

@Composable
private fun WorldCupHubHeader(onViewHub: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(GoalioColors.Accent))
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF241000))
                .padding(horizontal = metrics.dp(18), vertical = metrics.dp(16)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlobeIcon(Modifier.size(20.dp))
            Spacer(Modifier.width(metrics.dp(10)))
            Text(trans("Group A"), color = Color.White, fontSize = metrics.sp(22), fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            Text(trans("View All"), color = GoalioColors.Accent, fontSize = metrics.sp(13), fontWeight = FontWeight.Black, modifier = Modifier.clickable(onClick = onViewHub))
        }
    }
}

@Composable
private fun StandingTeamRow(team: StandingTeamInfo, index: Int, rows: List<StandingTeamInfo>) {
    val metrics = rememberGoalioMetrics()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text((team.rank ?: index + 1).toString(), color = GoalioColors.TextSecondary, fontSize = metrics.sp(16), fontWeight = FontWeight.Bold, modifier = Modifier.width(metrics.dp(50)))
        TeamStandingName(team, Modifier.weight(1f))
        Text(team.stageLabel(), color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(metrics.dp(62)))
        Text((team.points ?: 0).toString(), color = GoalioColors.TextSecondary, fontSize = metrics.sp(16), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WorldCupHubCard(standings: LeagueStandings?, onViewHub: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    val rows = remember(standings) {
        val teams = standings?.teams.orEmpty()
        val groupA = teams.filter { it.isGroupA() }
        val fallbackGroup = teams.groupBy { it.group ?: it.stage ?: "" }.entries.firstOrNull()?.value.orEmpty()
        (groupA.ifEmpty { fallbackGroup })
            .sortedBy { it.rank ?: 999 }
            .take(4)
    }
    Surface(
        color = GoalioColors.Surface1,
        shape = RoundedCornerShape(metrics.dp(10)),
        border = BorderStroke(1.dp, GoalioColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            WorldCupHubHeader(onViewHub)
            Column(Modifier.padding(metrics.dp(18)), verticalArrangement = Arrangement.spacedBy(metrics.dp(13))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(trans("Rank"), color = Color(0xFFE4D7BC), fontSize = metrics.sp(15), fontWeight = FontWeight.Black, modifier = Modifier.width(metrics.dp(50)))
                    Text(trans("Team"), color = Color(0xFFE4D7BC), fontSize = metrics.sp(15), fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                    Text(trans("Stage"), color = Color(0xFFE4D7BC), fontSize = metrics.sp(15), fontWeight = FontWeight.Black, modifier = Modifier.width(metrics.dp(62)))
                    Text(trans("PTS"), color = Color(0xFFE4D7BC), fontSize = metrics.sp(15), fontWeight = FontWeight.Black)
                }
                if (rows.isEmpty()) {
                    Text(trans("Group A table will appear when FIFA publishes the current standings."), color = GoalioColors.TextSecondary, fontSize = metrics.sp(14), lineHeight = metrics.sp(20))
                } else {
                    rows.forEachIndexed { idx, team -> StandingTeamRow(team, idx, rows) }
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
private fun FunTileIcon(label: String, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val color = Color.White
        if (label == "The Football Five") {
            drawCircle(Color.White.copy(alpha = .16f), radius = size.minDimension * .48f)
            drawRoundRect(color, Offset(size.width * 0.25f, size.height * 0.22f), Size(size.width * 0.5f, size.height * 0.56f), androidx.compose.ui.geometry.CornerRadius(6f, 6f), style = Stroke(2.5.dp.toPx()))
            drawLine(color, Offset(size.width * 0.35f, size.height * 0.42f), Offset(size.width * 0.65f, size.height * 0.42f), 2.5.dp.toPx())
            drawLine(color, Offset(size.width * 0.35f, size.height * 0.55f), Offset(size.width * 0.58f, size.height * 0.55f), 2.5.dp.toPx())
            drawLine(color, Offset(size.width * 0.35f, size.height * 0.68f), Offset(size.width * 0.52f, size.height * 0.68f), 2.5.dp.toPx())
        } else {
            drawCircle(color, radius = size.minDimension * 0.15f, center = Offset(size.width * 0.5f, size.height * 0.4f), style = Stroke(2.dp.toPx()))
            drawArc(color, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = Offset(size.width * 0.35f, size.height * 0.55f), size = Size(size.width * 0.3f, size.height * 0.3f), style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun FunTile(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(metrics.dp(18)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = .18f)),
        modifier = modifier.height(metrics.dp(154)).clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFF8A00), Color(0xFFB83B00), Color(0xFF1B0A00))
                    )
                )
        ) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(Color.White.copy(alpha = .11f), radius = size.minDimension * .52f, center = Offset(size.width * .88f, size.height * .18f))
                drawCircle(Color.Black.copy(alpha = .18f), radius = size.minDimension * .42f, center = Offset(size.width * .05f, size.height * .92f))
                repeat(5) { index ->
                    val x = size.width * (.18f + index * .14f)
                    drawLine(Color.White.copy(alpha = .14f), Offset(x, size.height * .12f), Offset(x + size.width * .2f, size.height * .88f), 1.2.dp.toPx())
                }
            }
            Row(
                Modifier.fillMaxSize().padding(metrics.dp(18)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FunTileIcon(label, Modifier.size(metrics.dp(64)))
                Spacer(Modifier.width(metrics.dp(16)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
                    Text(trans("The Football Five"), color = Color.White, fontSize = metrics.sp(24), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(trans("5 questions. 1 streak. Climb the table."), color = Color.White.copy(alpha = .82f), fontSize = metrics.sp(13), fontWeight = FontWeight.SemiBold, maxLines = 2)
                }
                Canvas(Modifier.size(metrics.dp(32))) {
                    val stroke = 2.5.dp.toPx()
                    drawLine(Color.White, Offset(size.width * .38f, size.height * .28f), Offset(size.width * .65f, size.height * .5f), stroke, StrokeCap.Round)
                    drawLine(Color.White, Offset(size.width * .65f, size.height * .5f), Offset(size.width * .38f, size.height * .72f), stroke, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun FunZoneSection(onOpenGames: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
        Text(trans("The Football Five"), color = GoalioColors.TextPrimary, fontSize = metrics.sp(23), fontWeight = FontWeight.Black)
        FunTile("The Football Five", Modifier.fillMaxWidth(), onOpenGames)
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
    LaunchedEffect(match.matchId, match.kickoff, match.state) {
        if (match.state !in setOf("pre", "in")) return@LaunchedEffect
        if (match.state == "pre") {
            val kickoffInstant = runCatching { OffsetDateTime.parse(match.kickoff).toInstant() }.getOrNull()
            if (kickoffInstant != null && Duration.between(Instant.now(), kickoffInstant).toHours() > 48) {
                return@LaunchedEffect
            }
        }
        while (true) {
            now = Instant.now()
            delay(1_000)
        }
    }
    val countdown = remember(match.kickoff, now) { match.countdownLabel(now) }
    val liveClock = remember(match.league, match.matchId, match.state, match.status, match.statusDescription, now) {
        LiveMatchClockStore.label(
            "${match.league}:${match.matchId}",
            match.state,
            match.status,
            match.statusDescription,
            now
        )
    }
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

internal fun ScheduleMatch.isHomeTerminal(): Boolean {
    if (state.equals("post", ignoreCase = true)) return true
    val value = "${status.orEmpty()} ${statusDescription.orEmpty()}".trim().lowercase()
    val terminalCodes = setOf("ft", "final", "aet", "pens", "canceled", "cancelled", "postponed", "abandoned")
    return value in terminalCodes || listOf(
        "full time",
        "match finished",
        "match ended",
        "game finished",
        "game ended",
        "cancelled",
        "canceled",
        "postponed",
        "abandoned"
    ).any(value::contains)
}

internal fun ScheduleMatch.isHomeLiveAt(now: Instant): Boolean {
    if (isHomeTerminal()) return false
    if (state.equals("in", ignoreCase = true) || hasExplicitHomeLiveStatus()) return true
    val kickoffInstant = runCatching { OffsetDateTime.parse(kickoff).toInstant() }.getOrNull() ?: return false
    return !now.isBefore(kickoffInstant) && now <= kickoffInstant.plus(Duration.ofHours(4))
}

internal fun ScheduleMatch.canRemainHomeLiveAt(now: Instant): Boolean {
    if (isHomeTerminal()) return false
    val kickoffInstant = runCatching { OffsetDateTime.parse(kickoff).toInstant() }.getOrNull()
    return kickoffInstant?.let { now <= it.plus(Duration.ofHours(6)) }
        ?: (state.equals("in", ignoreCase = true) || hasExplicitHomeLiveStatus())
}

private fun ScheduleMatch.hasExplicitHomeLiveStatus(): Boolean {
    val statusCode = status?.trim()?.uppercase().orEmpty()
    if (statusCode in setOf("HT", "ET", "1H", "2H", "P", "LIVE")) return true
    val value = "${status.orEmpty()} ${statusDescription.orEmpty()}"
    return value.contains("live", ignoreCase = true) ||
        value.contains("half time", ignoreCase = true) ||
        value.contains("halftime", ignoreCase = true) ||
        value.contains("in progress", ignoreCase = true) ||
        value.contains("extra time", ignoreCase = true) ||
        Regex("\\b\\d{1,3}(?:\\+\\d+)?['’]").containsMatchIn(value)
}

private fun ScheduleMatch.homeMatchKey(): String = "$league:$matchId"

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

private fun ScheduleMatch.localKickoffDate(): LocalDate? = runCatching {
    OffsetDateTime.parse(kickoff).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
}.getOrNull()

private fun StandingTeamInfo.isGroupA(): Boolean {
    fun matches(value: String?): Boolean {
        val normalized = value?.trim()?.replace("_", " ") ?: return false
        return normalized.equals("A", ignoreCase = true) ||
            normalized.equals("Group A", ignoreCase = true) ||
            normalized.endsWith(" Group A", ignoreCase = true)
    }
    return matches(group) || matches(stage)
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
    if (remaining.toHours() > 48) {
        return runCatching {
            OffsetDateTime.parse(kickoff)
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("EEE d MMM"))
                .uppercase()
        }.getOrNull()
    }
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
