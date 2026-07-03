package zero.ramjikvarosai.hirebazzar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import zero.ramjikvarosai.hirebazzar.ui.theme.GoalioColors
import kotlinx.coroutines.delay

@Composable
fun WorldCupScreen(
    onBack: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenMatches: () -> Unit,
    onOpenGames: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val metrics = rememberGoalioMetrics()
    var data by remember { mutableStateOf(WorldCupRepository.cached(context)) }
    var selected by remember { mutableStateOf("GROUPS") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        MatchRepository.matchUpdates.collect {
            data = data?.let(WorldCupRepository::reconcile)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            runCatching { WorldCupRepository.refresh(context) }
                .onSuccess {
                    data = it
                    error = null
                }
                .onFailure {
                    if (data == null) error = "Could not load World Cup hub. Check your internet connection and retry later."
                }
            val hubMatches = data?.let { it.liveMatches + it.todayMatches + it.upcomingMatches + it.recentResults }.orEmpty()
            delay(MatchRepository.nextRefreshDelayMillis(hubMatches))
        }
    }

    GoalioBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            contentPadding = PaddingValues(metrics.horizontalPadding, metrics.dp(18), metrics.horizontalPadding, metrics.bottomBarPadding),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(22))
        ) {
            item { GoalioTopBar(title = trans("WORLD CUP"), onBack = onBack, onSettings = onOpenSettings) }
            when {
                data == null && error == null -> item { WorldCupLoadingSkeleton() }
                error != null -> item { WorldCupState(error.orEmpty()) }
                data != null -> {
                    val cup = data!!
                    item { WorldCupHero(cup) }
                    item { WorldCupTabs(selected) { selected = it } }
                    when (selected) {
                        "GROUPS" -> item { WorldCupGroups(cup.groups) }
                        "BRACKET" -> item { WorldCupBracket(cup) }
                        "LIBRARY" -> item { WorldCupLibrary(cup) }
                    }
                }
            }
        }
        GoalioBottomBar(Modifier.align(Alignment.BottomCenter), "World Cup", onOpenHome, onOpenMatches, {}, onOpenGames)
    }
}

@Composable
private fun WorldCupTopBar(onBack: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("<", color = Color.White, fontSize = metrics.sp(28), fontWeight = FontWeight.Black, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.width(metrics.dp(18)))
        Text(trans("WORLD CUP").uppercase(), color = Color.White, fontSize = metrics.sp(24), fontWeight = FontWeight.Black, letterSpacing = 5.sp)
        Spacer(Modifier.weight(1f))
        Text(trans("Search"), color = GoalioColors.Accent, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
        Spacer(Modifier.width(metrics.dp(14)))
        Text(trans("Settings"), color = GoalioColors.Accent, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
    }
}

@Composable
private fun WorldCupHero(cup: WorldCupBootstrapInfo) {
    val metrics = rememberGoalioMetrics()
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(metrics.dp(18)), color = GoalioColors.Surface1, modifier = Modifier.fillMaxWidth().height(metrics.dp(260))) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("file:///android_asset/northamerica.webp")
                    .build(),
                contentDescription = "North America 2026 background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Bottom-weighted dark gradient so text pops above the image
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Black.copy(alpha = 0.05f),
                                0.45f to Color.Black.copy(alpha = 0.35f),
                                1.0f to Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
            )
            Column(Modifier.align(Alignment.BottomStart).padding(metrics.dp(24))) {
                Surface(
                    color = GoalioColors.Accent.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, GoalioColors.Accent)
                ) {
                    Text(
                        "ROAD TO 2026",
                        color = Color.White,
                        fontSize = metrics.sp(10),
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = metrics.dp(12), vertical = metrics.dp(6))
                    )
                }
                Spacer(Modifier.height(metrics.dp(12)))
                Text(trans("NORTH AMERICA 2026"), color = Color.White, fontSize = metrics.sp(22), fontWeight = FontWeight.Black)
                Spacer(Modifier.height(metrics.dp(14)))
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(32))) {
                    HeroMetric("${cup.tournament.daysToFinal ?: 0}", "DAYS TO FINAL")
                    HeroMetric("${cup.tournament.hostCities}", "HOST CITIES")
                }
                Spacer(Modifier.height(metrics.dp(10)))
                Text(
                    cup.tournament.stage.uppercase(),
                    color = GoalioColors.Accent,
                    fontSize = metrics.sp(11),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
private fun HeroMetric(value: String, label: String) {
    val metrics = rememberGoalioMetrics()
    Column {
        Text(value, color = Color.White, fontSize = metrics.sp(38), fontWeight = FontWeight.Light)
        Text(label, color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
    }
}

@Composable
private fun WorldCupTabs(selected: String, onSelected: (String) -> Unit) {
    val metrics = rememberGoalioMetrics()
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(12)),
        contentPadding = PaddingValues(bottom = metrics.dp(8))
    ) {
        items(listOf("GROUPS", "BRACKET", "LIBRARY")) { tab ->
            val isSelected = selected == tab
            val tabBrush = if (isSelected) {
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF8F3900),
                        Color(0xFF100600)
                    )
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Transparent
                    )
                )
            }
            Surface(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onSelected(tab) },
                color = Color.Transparent,
                shape = CircleShape,
                border = BorderStroke(1.5.dp, GoalioColors.Tertiary)
            ) {
                Box(
                    modifier = Modifier
                        .background(tabBrush)
                        .padding(horizontal = metrics.dp(24), vertical = metrics.dp(12)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = trans(tab),
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                        fontSize = metrics.sp(12),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun WorldCupGroups(groups: List<WorldCupGroupInfo>) {
    val metrics = rememberGoalioMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
        SectionTitle("Groups")
        groups.forEach { group -> WorldCupGroupTable(group) }
    }
}

@Composable
private fun WorldCupGroupTable(group: WorldCupGroupInfo) {
    val metrics = rememberGoalioMetrics()
    var isExpanded by remember { mutableStateOf(true) }
    
    Surface(
        color = GoalioColors.Surface1,
        shape = RoundedCornerShape(metrics.dp(16)),
        border = BorderStroke(1.dp, GoalioColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GoalioColors.Surface2)
                    .clickable { isExpanded = !isExpanded }
                    .padding(horizontal = metrics.dp(16), vertical = metrics.dp(14)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(metrics.dp(6), metrics.dp(18))
                            .background(GoalioColors.Accent, RoundedCornerShape(metrics.dp(3)))
                    )
                    Spacer(Modifier.width(metrics.dp(10)))
                    Text(
                        text = "${trans("Group")} ${group.code}",
                        color = Color.White,
                        fontSize = metrics.sp(15),
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    text = if (isExpanded) trans("COLLAPSE") else trans("EXPAND"),
                    color = GoalioColors.Accent,
                    fontSize = metrics.sp(11),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }

            if (isExpanded) {
                val statsScrollState = rememberScrollState()
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GoalioColors.Neutral)
                        .padding(horizontal = metrics.dp(16), vertical = metrics.dp(8)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = trans("TEAM"),
                        color = GoalioColors.TextSecondary,
                        fontSize = metrics.sp(10),
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.width(metrics.dp(130))
                    )
                    
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(statsScrollState),
                        horizontalArrangement = Arrangement.spacedBy(metrics.dp(8))
                    ) {
                        listOf("MP", "W", "D", "L", "GF", "GA", "GD", "PTS").forEach { label ->
                            Text(
                                text = trans(label),
                                color = GoalioColors.TextSecondary,
                                fontSize = metrics.sp(10),
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.width(metrics.dp(44)),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                group.teams.forEachIndexed { index, team ->
                    if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(GoalioColors.Divider))
                    
                    val isQualifying = index < 2
                    val rowBg = if (isQualifying) GoalioColors.Tertiary.copy(alpha = 0.08f) else Color.Transparent
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg)
                            .padding(horizontal = metrics.dp(16), vertical = metrics.dp(12)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.width(metrics.dp(130)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = (team.rank ?: index + 1).toString(),
                                color = if (isQualifying) GoalioColors.Accent else GoalioColors.TextSecondary,
                                fontSize = metrics.sp(12),
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.width(metrics.dp(18))
                            )
                            Box(Modifier.size(metrics.dp(20)), contentAlignment = Alignment.Center) {
                                if (!team.logo.isNullOrBlank()) {
                                    AsyncImage(
                                        model = team.logo,
                                        contentDescription = "${team.name} flag",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(countryFlag(team.name), fontSize = metrics.sp(14))
                                }
                            }
                            Spacer(Modifier.width(metrics.dp(8)))
                            Text(
                                text = team.name,
                                color = Color.White,
                                fontSize = metrics.sp(13),
                                fontWeight = if (isQualifying) FontWeight.Black else FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(statsScrollState),
                            horizontalArrangement = Arrangement.spacedBy(metrics.dp(8)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GroupStat(team.played)
                            GroupStat(team.wins)
                            GroupStat(team.draws)
                            GroupStat(team.losses)
                            GroupStat(team.goalsFor)
                            GroupStat(team.goalsAgainst)
                            GroupStat(team.goalDifference, signed = true)
                            GroupStat(team.points, bold = true, highlight = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupStat(value: Int?, signed: Boolean = false, bold: Boolean = false, highlight: Boolean = false) {
    val metrics = rememberGoalioMetrics()
    val text = when {
        value == null -> "-"
        signed && value > 0 -> "+$value"
        else -> value.toString()
    }
    Text(
        text = text,
        color = if (highlight) GoalioColors.Accent else if (bold) Color.White else GoalioColors.TextPrimary,
        fontSize = metrics.sp(13),
        fontWeight = if (bold || highlight) FontWeight.Black else FontWeight.Medium,
        modifier = Modifier.width(metrics.dp(44)),
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

@Composable
private fun WorldCupBracket(cup: WorldCupBootstrapInfo) {
    val metrics = rememberGoalioMetrics()
    val bracket = cup.bracket
    val hasMatches = bracket.rounds.values.any { it.isNotEmpty() }
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
        SectionTitle("The Knockout Path")
        if (!hasMatches) {
            WorldCupState("Bracket data is loading from World Cup feed.")
        } else {
            Surface(color = Color(0xFF050505), shape = RoundedCornerShape(metrics.dp(14)), border = BorderStroke(1.dp, Color(0xFF2B2B2B)), modifier = Modifier.fillMaxWidth()) {
                ConnectedBracket(cup)
            }
        }
    }
}

@Composable
private fun ConnectedBracket(cup: WorldCupBootstrapInfo) {
    val bracket = cup.bracket
    val metrics = rememberGoalioMetrics()
    val cardWidth = 260f * metrics.scale
    val cardHeight = 92f * metrics.scale
    val columnGap = 72f * metrics.scale
    val rowGap = 28f * metrics.scale
    val headerHeight = 48f * metrics.scale
    val firstStep = cardHeight + rowGap
    val columns = remember(bracket, metrics.scale) {
        fun matches(code: String) = bracket.rounds[code].orEmpty().sortedBy { it.slotIndex }
        fun slots(items: List<WorldCupBracketMatchInfo>, range: IntRange) =
            items.filter { it.slotIndex in range }.sortedBy { it.slotIndex }
        fun nextLevel(source: List<Float>) = source.chunked(2).map { pair -> pair.average().toFloat() }

        val outer = List(8) { headerHeight + cardHeight / 2f + it * firstStep }
        val round16Centers = nextLevel(outer)
        val quarterCenters = nextLevel(round16Centers)
        val semiCenters = nextLevel(quarterCenters)
        val finalCenters = semiCenters.take(1)
        val r32 = matches("R32")
        val r16 = matches("R16")
        val quarters = matches("QF")
        val semis = matches("SF")
        val final = matches("FINAL").filter { it.slotIndex == 0 }.take(1)
        val allMatches = listOf(r32, r16, quarters, semis, final).flatten()
        val fallbackCenters = buildMap<Pair<String, Int>, Float> {
            r32.forEach { put("R32" to it.slotIndex, outer[it.slotIndex % 8]) }
            r16.forEach { put("R16" to it.slotIndex, round16Centers[it.slotIndex % 4]) }
            quarters.forEach { put("QF" to it.slotIndex, quarterCenters[it.slotIndex % 2]) }
            semis.forEach { put("SF" to it.slotIndex, semiCenters.first()) }
            final.forEach { put("FINAL" to it.slotIndex, finalCenters.first()) }
        }
        val centersBySlot = mutableMapOf<Pair<String, Int>, Float>()
        r32.forEach { match -> centersBySlot["R32" to match.slotIndex] = fallbackCenters.getValue("R32" to match.slotIndex) }
        listOf("R16", "QF", "SF", "FINAL").forEach { targetRound ->
            matches(targetRound).forEach { target ->
                val sourceCenters = allMatches.mapNotNull { source ->
                    source.nextMatchSlot
                        ?.takeIf { it.round == targetRound && it.slotIndex == target.slotIndex }
                        ?.let { centersBySlot[source.round to source.slotIndex] }
                }
                centersBySlot[targetRound to target.slotIndex] =
                    sourceCenters.takeIf { it.isNotEmpty() }?.average()?.toFloat()
                        ?: fallbackCenters.getValue(targetRound to target.slotIndex)
            }
        }

        fun column(code: String, title: String, items: List<WorldCupBracketMatchInfo>) = BracketColumn(
            roundCode = code,
            title = title,
            matches = items,
            centers = items.map { centersBySlot[code to it.slotIndex] ?: fallbackCenters.getValue(code to it.slotIndex) }
        )

        listOf(
            column("R32", "ROUND OF 32", slots(r32, 0..7)),
            column("R16", "ROUND OF 16", slots(r16, 0..3)),
            column("QF", "QUARTERFINALS", slots(quarters, 0..1)),
            column("SF", "SEMIFINALS", slots(semis, 0..0)),
            column("FINAL", "FINAL", final),
            column("SF", "SEMIFINALS", slots(semis, 1..1)),
            column("QF", "QUARTERFINALS", slots(quarters, 2..3)),
            column("R16", "ROUND OF 16", slots(r16, 4..7)),
            column("R32", "ROUND OF 32", slots(r32, 8..15))
        )
    }
    val nodePositions = remember(columns) {
        buildMap<Pair<String, Int>, BracketNodePosition> {
            columns.forEachIndexed { columnIndex, column ->
                column.matches.zip(column.centers).forEach { (match, center) ->
                    put(column.roundCode to match.slotIndex, BracketNodePosition(columnIndex, center))
                }
            }
        }
    }
    val connectionGroups = remember(columns, nodePositions) {
        columns.flatMap { it.matches }.mapNotNull { source ->
            val targetSlot = source.nextMatchSlot ?: return@mapNotNull null
            val sourcePosition = nodePositions[source.round to source.slotIndex] ?: return@mapNotNull null
            val targetPosition = nodePositions[targetSlot.round to targetSlot.slotIndex] ?: return@mapNotNull null
            BracketConnectionSource(targetSlot.round to targetSlot.slotIndex, sourcePosition, targetPosition)
        }.groupBy { it.targetKey }.values
    }
    val contentWidth = columns.size * cardWidth + (columns.size - 1) * columnGap + 28f * metrics.scale
    val contentHeight = headerHeight + 8 * firstStep + 10f * metrics.scale
    val bracketScroll = rememberScrollState()

    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp

    val targetColumnIndex = remember(columns, cup) {
        // 1. Search for first column containing a LIVE match
        var index = columns.indexOfFirst { col ->
            col.matches.any { match ->
                cup.liveMatches.any { it.matchId == match.eventId } ||
                match.status?.contains("LIVE", ignoreCase = true) == true
            }
        }
        if (index != -1) return@remember index

        // 2. Search for first column containing a TODAY match
        index = columns.indexOfFirst { col ->
            col.matches.any { match ->
                cup.todayMatches.any { it.matchId == match.eventId } ||
                match.status?.contains("TODAY", ignoreCase = true) == true
            }
        }
        if (index != -1) return@remember index

        // 3. Search for first column containing a match that has teams decided and is not completed
        index = columns.indexOfFirst { col ->
            col.matches.any { match ->
                !match.homeTeam.isNullOrBlank() && !match.awayTeam.isNullOrBlank() && match.status != "COMPLETED"
            }
        }
        if (index != -1) return@remember index

        // 4. Default fallback: middle one (index 4) if all matches are completed or no match started
        4
    }

    LaunchedEffect(bracketScroll.maxValue, targetColumnIndex) {
        if (bracketScroll.maxValue > 0) {
            val scrollTargetDp = targetColumnIndex * (cardWidth + columnGap) - (screenWidth - cardWidth) / 2f
            val scrollTargetPx = with(density) { scrollTargetDp.dp.toPx() }.toInt()
            bracketScroll.scrollTo(scrollTargetPx.coerceIn(0, bracketScroll.maxValue))
        }
    }

    Box(Modifier.fillMaxWidth().horizontalScroll(bracketScroll)) {
        Box(Modifier.width(contentWidth.dp).height(contentHeight.dp).padding(horizontal = metrics.dp(14))) {
            Canvas(Modifier.fillMaxSize()) {
                val connector = Color(0xFFFF8500).copy(alpha = .78f)
                val widthPx = cardWidth.dp.toPx()
                val gapPx = columnGap.dp.toPx()

                fun drawSources(sources: List<BracketNodePosition>, target: BracketNodePosition, towardRight: Boolean) {
                    if (sources.isEmpty()) return
                    val sourceColumn = sources.first().columnIndex
                    val startX = sourceColumn * (widthPx + gapPx) + if (towardRight) widthPx else 0f
                    val endX = target.columnIndex * (widthPx + gapPx) + if (towardRight) 0f else widthPx
                    val middleX = (startX + endX) / 2f
                    sources.forEach { source ->
                        drawLine(connector, Offset(startX, source.centerY.dp.toPx()), Offset(middleX, source.centerY.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                    }
                    val allCenters = sources.map { it.centerY } + target.centerY
                    drawLine(connector, Offset(middleX, allCenters.min().dp.toPx()), Offset(middleX, allCenters.max().dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                    drawLine(connector, Offset(middleX, target.centerY.dp.toPx()), Offset(endX, target.centerY.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
                }

                connectionGroups.forEach { group ->
                    val target = group.first().targetPosition
                    drawSources(group.map { it.sourcePosition }.filter { it.columnIndex < target.columnIndex }, target, towardRight = true)
                    drawSources(group.map { it.sourcePosition }.filter { it.columnIndex > target.columnIndex }, target, towardRight = false)
                }
            }
            columns.forEachIndexed { columnIndex, column ->
                val x = columnIndex * (cardWidth + columnGap)
                Text(
                    column.title, color = if (columnIndex == 4) GoalioColors.Accent else GoalioColors.TextSecondary,
                    fontSize = metrics.sp(11), fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                    modifier = Modifier.offset(x.dp, 0.dp).width(cardWidth.dp), maxLines = 1
                )
                column.matches.zip(column.centers).forEach { (match, center) ->
                    BracketMatchBox(
                        match = match,
                        modifier = Modifier.offset(x.dp, (center - cardHeight / 2f).dp).width(cardWidth.dp).height(cardHeight.dp),
                        highlighted = columnIndex == 4
                    )
                }
            }
        }
    }
}

private data class BracketColumn(
    val roundCode: String,
    val title: String,
    val matches: List<WorldCupBracketMatchInfo>,
    val centers: List<Float>
)

private data class BracketNodePosition(val columnIndex: Int, val centerY: Float)

private data class BracketConnectionSource(
    val targetKey: Pair<String, Int>,
    val sourcePosition: BracketNodePosition,
    val targetPosition: BracketNodePosition
)

@Composable
private fun BracketMatchBox(match: WorldCupBracketMatchInfo, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    val metrics = rememberGoalioMetrics()
    val isLive = match.status?.contains("LIVE", ignoreCase = true) == true
    val isToday = match.status?.contains("TODAY", ignoreCase = true) == true
    
    val cardBorderColor = when {
        isLive -> GoalioColors.Accent
        isToday -> GoalioColors.Secondary
        highlighted -> GoalioColors.Accent.copy(alpha = 0.6f)
        else -> Color(0xFF33363A)
    }
    
    val cardBg = when {
        isLive -> Color(0xFF201308)
        highlighted -> Color(0xFF1B1B1D)
        else -> GoalioColors.Surface2
    }

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(metrics.dp(8)),
        border = BorderStroke(1.dp, cardBorderColor),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = metrics.dp(10), vertical = metrics.dp(6)),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isLive) "LIVE" else if (isToday) "TODAY" else match.status?.uppercase() ?: "MATCH",
                    color = if (isLive) Color(0xFFFF5252) else if (isToday) GoalioColors.Secondary else GoalioColors.TextSecondary,
                    fontSize = metrics.sp(8),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                if (isLive) {
                    Box(
                        modifier = Modifier
                            .size(metrics.dp(5))
                            .clip(CircleShape)
                            .background(Color(0xFFFF5252))
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(4))) {
                BracketTeamRow(
                    name = match.homeTeam.orEmpty(),
                    score = match.homeScore,
                    logo = match.homeTeamLogo,
                    isWinner = match.winnerTeamId == "home" || (match.homeScore != null && match.awayScore != null && match.homeScore > match.awayScore)
                )
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2D3035)))
                BracketTeamRow(
                    name = match.awayTeam.orEmpty(),
                    score = match.awayScore,
                    logo = match.awayTeamLogo,
                    isWinner = match.winnerTeamId == "away" || (match.homeScore != null && match.awayScore != null && match.awayScore > match.homeScore)
                )
            }
        }
    }
}

@Composable
private fun BracketTeamRow(name: String, score: Int?, logo: String?, isWinner: Boolean) {
    val metrics = rememberGoalioMetrics()
    val displayName = name.ifBlank { "TBD" }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(metrics.dp(16)), contentAlignment = Alignment.Center) {
            if (!logo.isNullOrBlank()) {
                AsyncImage(
                    model = logo,
                    contentDescription = "$displayName flag",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(countryFlag(displayName), fontSize = metrics.sp(12), maxLines = 1)
            }
        }
        Spacer(Modifier.width(metrics.dp(6)))
        Text(
            text = displayName,
            color = if (displayName == "TBD") GoalioColors.TextSecondary else if (isWinner) Color.White else GoalioColors.TextPrimary,
            fontSize = metrics.sp(11),
            fontWeight = if (isWinner) FontWeight.Black else FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (score != null) {
            Text(
                text = score.toString(),
                color = if (isWinner) GoalioColors.Accent else GoalioColors.TextSecondary,
                fontSize = metrics.sp(12),
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.End,
                modifier = Modifier.width(metrics.dp(20))
            )
        }
    }
}

@Composable
private fun WorldCupMatches(cup: WorldCupBootstrapInfo, onOpenMatches: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    val matches = (cup.liveMatches + cup.todayMatches + cup.upcomingMatches + cup.recentResults).distinctBy { it.matchId }
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
        SectionTitle("World Cup Matches")
        if (matches.isEmpty()) WorldCupState("No World Cup fixtures available yet.")
        matches.take(8).forEach { match ->
            Surface(color = GoalioColors.Surface2, shape = RoundedCornerShape(metrics.dp(10)), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenMatches)) {
                Row(Modifier.padding(metrics.dp(15)), verticalAlignment = Alignment.CenterVertically) {
                    Text(match.statusDescription ?: match.status ?: "Match", color = GoalioColors.Accent, fontSize = metrics.sp(12), fontWeight = FontWeight.Black, modifier = Modifier.width(metrics.dp(86)))
                    Text("${match.homeTeam?.shortName ?: "TBD"} vs ${match.awayTeam?.shortName ?: "TBD"}", color = Color.White, fontSize = metrics.sp(15), fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(worldCupScoreLine(match), color = GoalioColors.TextSecondary, fontSize = metrics.sp(14), fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

private val HardcodedLibraryItems = listOf(
    WorldCupLibraryItemInfo(
        id = "wc2026-news",
        title = "FIFA World Cup 2026™ – Latest News & Updates",
        category = "News",
        body = "Stay up to date with the very latest FIFA World Cup 2026 news, match reports, team updates, and official announcements straight from FIFA headquarters.",
        readMinutes = 3,
        url = "https://www.fifa.com/en/tournaments/mens/worldcup/canadamexicousa2026",
        imageUrl = "https://images.unsplash.com/photo-1431324155629-1a6edd1d228a?q=80&w=800&auto=format&fit=crop"
    ),
    WorldCupLibraryItemInfo(
        id = "host-cities-2026",
        title = "16 Host Cities – USA, Canada & Mexico",
        category = "Guide",
        body = "The 2026 edition is staged across three nations and 16 iconic cities: from New York to Los Angeles, Vancouver to Mexico City. Explore every stadium, city profile, and what to expect at each venue.",
        readMinutes = 8,
        url = "https://www.fifa.com/en/tournaments/mens/worldcup/canadamexicousa2026/host-cities",
        imageUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?q=80&w=800&auto=format&fit=crop"
    ),
    WorldCupLibraryItemInfo(
        id = "knockout-format-2026",
        title = "The Expanded Format – 48 Teams, 104 Matches",
        category = "Format",
        body = "FIFA World Cup 2026 is the first men's edition with 48 teams. Featuring a new Round of 32 knockout stage, the tournament spans 39 days across 104 thrilling matches before the world champion is crowned.",
        readMinutes = 5,
        url = "https://www.fifa.com/en/tournaments/mens/worldcup/canadamexicousa2026/match-schedule",
        imageUrl = "https://images.unsplash.com/photo-1579952360673-2a04953c80e7?q=80&w=800&auto=format&fit=crop"
    ),
    WorldCupLibraryItemInfo(
        id = "tickets-2026",
        title = "Match Tickets – How to Get Yours",
        category = "Tickets",
        body = "Learn about the official FIFA ticketing process for the 2026 World Cup. Register your details, sign up for ballot alerts, and find out how to secure seats for the greatest football show on Earth.",
        readMinutes = 4,
        url = "https://www.fifa.com/en/tournaments/mens/worldcup/canadamexicousa2026/tickets",
        imageUrl = "https://images.unsplash.com/photo-1540747737956-3787293a9fc4?q=80&w=800&auto=format&fit=crop"
    ),
    WorldCupLibraryItemInfo(
        id = "history-wc",
        title = "FIFA World Cup™ History – 90 Years of Glory",
        category = "History",
        body = "From Uruguay 1930 to North America 2026, relive the greatest moments, top scorers, iconic goals and the champions who wrote football history in FIFA's official tournament archive.",
        readMinutes = 10,
        url = "https://www.fifa.com/en/tournaments/mens/worldcup",
        imageUrl = "https://images.unsplash.com/photo-1551958219-acbc92a4d8b2?q=80&w=800&auto=format&fit=crop"
    )
)

@Composable
private fun WorldCupLibrary(cup: WorldCupBootstrapInfo) {
    val metrics = rememberGoalioMetrics()
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
        SectionTitle(trans("World Cup Library"))
        Surface(color = Color(0xFF171717), shape = RoundedCornerShape(metrics.dp(14)), border = BorderStroke(1.dp, Color(0xFF2A2A2A)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(metrics.dp(18))) {
                Text(dynamicTrans(cup.randomFact.title), color = GoalioColors.Accent, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
                Spacer(Modifier.height(metrics.dp(8)))
                Text(dynamicTrans(cup.randomFact.body), color = Color.White, fontSize = metrics.sp(17), fontWeight = FontWeight.Bold)
            }
        }
        HardcodedLibraryItems.forEach { item ->
            val assetName = when (item.id) {
                "wc2026-news" -> "news.jpg"
                "host-cities-2026" -> "guide.png"
                "knockout-format-2026" -> "format.webp"
                "tickets-2026" -> "ticket.webp"
                "history-wc" -> "history.webp"
                else -> "news.jpg"
            }
            Surface(
                color = GoalioColors.Surface2,
                shape = RoundedCornerShape(metrics.dp(14)),
                border = BorderStroke(1.dp, GoalioColors.CardBorder),
                onClick = {
                    if (!item.url.isNullOrBlank()) {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(metrics.dp(160))
                            .clip(RoundedCornerShape(topStart = metrics.dp(14), topEnd = metrics.dp(14)))
                    ) {
                        AsyncImage(
                            model = "file:///android_asset/$assetName",
                            contentDescription = dynamicTrans(item.title),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.1f),
                                            Color.Black.copy(alpha = 0.65f)
                                        )
                                    )
                                )
                        )
                    }
                    Column(Modifier.padding(metrics.dp(18))) {
                        Text(dynamicTrans(item.category).uppercase(), color = GoalioColors.Accent, fontSize = metrics.sp(11), fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(metrics.dp(8)))
                        Text(dynamicTrans(item.title), color = Color.White, fontSize = metrics.sp(18), fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(metrics.dp(4)))
                        Text(dynamicTrans(item.body), color = GoalioColors.TextSecondary, fontSize = metrics.sp(14), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(metrics.dp(12)))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${item.readMinutes} ${trans("min read")}", color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.Bold)
                            if (!item.url.isNullOrBlank()) {
                                Text("${trans("Read on FIFA.com")} →", color = GoalioColors.Accent, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val metrics = rememberGoalioMetrics()
    Text(trans(text), color = Color.White, fontSize = metrics.sp(22), fontWeight = FontWeight.Black)
}

@Composable
private fun WorldCupState(text: String) {
    val metrics = rememberGoalioMetrics()
    Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(metrics.dp(14)), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = GoalioColors.TextSecondary, fontSize = metrics.sp(15), fontWeight = FontWeight.Bold, modifier = Modifier.padding(metrics.dp(18)))
    }
}

@Composable
private fun WorldCupLoadingSkeleton() {
    val metrics = rememberGoalioMetrics()
    val transition = rememberInfiniteTransition(label = "worldCupSkeleton")
    val alpha by transition.animateFloat(
        initialValue = .26f,
        targetValue = .68f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "worldCupSkeletonAlpha"
    )
    val shimmer = Color(0xFF6A6A6A).copy(alpha = alpha)
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(18))) {
        Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(metrics.dp(18)), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = Modifier.fillMaxWidth().height(metrics.dp(260))) {
            Column(Modifier.padding(metrics.dp(22)), verticalArrangement = Arrangement.Bottom) {
                Box(Modifier.width(metrics.dp(104)).height(metrics.dp(22)).clip(CircleShape).background(shimmer))
                Spacer(Modifier.height(metrics.dp(14)))
                Box(Modifier.fillMaxWidth(.72f).height(metrics.dp(28)).clip(CircleShape).background(shimmer.copy(alpha = alpha * .9f)))
                Spacer(Modifier.height(metrics.dp(18)))
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(18))) {
                    repeat(3) {
                        Box(Modifier.weight(1f).height(metrics.dp(54)).clip(RoundedCornerShape(metrics.dp(12))).background(shimmer.copy(alpha = alpha * .74f)))
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
            repeat(3) {
                Box(Modifier.width(metrics.dp(106)).height(metrics.dp(42)).clip(RoundedCornerShape(50)).background(shimmer))
            }
        }
        repeat(4) {
            Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(metrics.dp(14)), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(metrics.dp(16)), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(metrics.dp(34)).clip(CircleShape).background(shimmer))
                    Spacer(Modifier.width(metrics.dp(12)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.dp(8))) {
                        Box(Modifier.fillMaxWidth(.62f).height(metrics.dp(14)).clip(CircleShape).background(shimmer))
                        Box(Modifier.fillMaxWidth(.42f).height(metrics.dp(10)).clip(CircleShape).background(shimmer.copy(alpha = alpha * .75f)))
                    }
                    Box(Modifier.width(metrics.dp(38)).height(metrics.dp(16)).clip(CircleShape).background(shimmer))
                }
            }
        }
    }
}

@Composable
private fun WorldCupBottomNav(modifier: Modifier = Modifier, onOpenHome: () -> Unit, onOpenMatches: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(color = Color(0xFF3B3B3B), shape = RoundedCornerShape(metrics.dp(28)), modifier = modifier.fillMaxWidth().padding(horizontal = metrics.dp(8), vertical = metrics.dp(10))) {
        Row(Modifier.padding(metrics.dp(8)), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            WorldCupNavTab("Home", false, onOpenHome)
            WorldCupNavTab("Matches", false, onOpenMatches)
            WorldCupNavTab("World Cup", true) {}
            WorldCupNavTab("Games", false) {}
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.WorldCupNavTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(color = if (selected) GoalioColors.Accent else Color.Transparent, shape = RoundedCornerShape(50), modifier = Modifier.weight(1f).height(metrics.dp(56)).clickable(onClick = onClick)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(trans(label), color = if (selected) Color.White else Color(0xFFBEB8AA), fontSize = metrics.sp(12), fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

private fun worldCupScoreLine(match: ScheduleMatch): String {
    val home = match.homeTeam?.score
    val away = match.awayTeam?.score
    return if (home == null || away == null) "-" else "$home - $away"
}

private val CountryCodes = mapOf(
    "Algeria" to "DZ", "Argentina" to "AR", "Australia" to "AU", "Austria" to "AT",
    "Belgium" to "BE", "Bosnia & Herzegovina" to "BA", "Brazil" to "BR", "Cabo Verde" to "CV",
    "Canada" to "CA", "Colombia" to "CO", "Congo DR" to "CD", "Croatia" to "HR",
    "Czechia" to "CZ", "Ecuador" to "EC", "Egypt" to "EG", "England" to "GB",
    "France" to "FR", "Germany" to "DE", "Ghana" to "GH", "Ivory Coast" to "CI",
    "Japan" to "JP", "Mexico" to "MX", "Morocco" to "MA", "Netherlands" to "NL",
    "Nigeria" to "NG", "Norway" to "NO", "Paraguay" to "PY", "Portugal" to "PT",
    "Senegal" to "SN", "South Africa" to "ZA", "South Korea" to "KR", "Spain" to "ES",
    "Sweden" to "SE", "Switzerland" to "CH", "United States" to "US", "USA" to "US"
)

private fun countryFlag(team: String): String {
    val code = CountryCodes[team] ?: return ""
    return code.map { letter -> String(Character.toChars(0x1F1E6 + (letter - 'A'))) }.joinToString("")
}
