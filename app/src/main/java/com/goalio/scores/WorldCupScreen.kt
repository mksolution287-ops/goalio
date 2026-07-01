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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalio.scores.ui.theme.GoalioColors

@Composable
fun WorldCupScreen(
    onBack: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenMatches: () -> Unit
) {
    val metrics = rememberGoalioMetrics()
    var data by remember { mutableStateOf<WorldCupBootstrapInfo?>(null) }
    var selected by remember { mutableStateOf("Groups") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { GoalioBackendApi.getWorldCupBootstrap() }
            .onSuccess { data = it }
            .onFailure { error = it.message ?: "Could not load World Cup hub." }
    }

    GoalioBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            contentPadding = PaddingValues(metrics.horizontalPadding, metrics.dp(18), metrics.horizontalPadding, metrics.bottomBarPadding),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(22))
        ) {
            item { WorldCupTopBar(onBack) }
            when {
                data == null && error == null -> item { WorldCupState("Loading World Cup data...") }
                error != null -> item { WorldCupState(error.orEmpty()) }
                data != null -> {
                    val cup = data!!
                    item { WorldCupHero(cup) }
                    item { WorldCupTabs(selected) { selected = it } }
                    when (selected) {
                        "Matches" -> item { WorldCupMatches(cup, onOpenMatches) }
                        "Bracket" -> item { WorldCupBracket(cup.bracket) }
                        "Library" -> item { WorldCupLibrary(cup) }
                        else -> {
                            item { WorldCupGroups(cup.groups) }
                            item { WorldCupBracket(cup.bracket.take(2)) }
                            item { WorldCupLibrary(cup) }
                        }
                    }
                }
            }
        }
        WorldCupBottomNav(Modifier.align(Alignment.BottomCenter), onOpenHome, onOpenMatches)
    }
}

@Composable
private fun WorldCupTopBar(onBack: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("<", color = Color.White, fontSize = metrics.sp(28), fontWeight = FontWeight.Black, modifier = Modifier.clickable(onClick = onBack))
        Spacer(Modifier.width(metrics.dp(18)))
        Text("WORLD CUP", color = Color.White, fontSize = metrics.sp(24), fontWeight = FontWeight.Black, letterSpacing = 5.sp)
        Spacer(Modifier.weight(1f))
        Text("⌕", color = GoalioColors.Accent, fontSize = metrics.sp(24), fontWeight = FontWeight.Black)
        Spacer(Modifier.width(metrics.dp(18)))
        Text("⚙", color = GoalioColors.Accent, fontSize = metrics.sp(20), fontWeight = FontWeight.Black)
    }
}

@Composable
private fun WorldCupHero(cup: WorldCupBootstrapInfo) {
    val metrics = rememberGoalioMetrics()
    Surface(shape = RoundedCornerShape(metrics.dp(18)), color = GoalioColors.Surface1, modifier = Modifier.fillMaxWidth().height(metrics.dp(270))) {
        Box(
            Modifier.background(
                Brush.verticalGradient(listOf(Color(0xFF183139), Color(0xFF0D0F10), Color(0xFF151515)))
            )
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(Color.White.copy(alpha = .16f), radius = size.width * .28f, center = Offset(size.width * .06f, size.height * .08f))
                drawCircle(Color.White.copy(alpha = .16f), radius = size.width * .28f, center = Offset(size.width * .94f, size.height * .08f))
                drawLine(Color(0xFFFF8500), Offset(size.width * .25f, size.height * .55f), Offset(size.width * .75f, size.height * .55f), 3f, StrokeCap.Round)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(metrics.dp(30))) {
                Surface(color = Color(0xFF7A2C19), shape = RoundedCornerShape(50), border = BorderStroke(1.dp, Color(0xFFFF8500))) {
                    Text("ROAD TO 2026", color = Color.White, fontSize = metrics.sp(12), fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = metrics.dp(14), vertical = metrics.dp(7)))
                }
                Spacer(Modifier.height(metrics.dp(18)))
                Text("NORTH AMERICA 2026", color = Color.White, fontSize = metrics.sp(24), fontWeight = FontWeight.Black)
                Spacer(Modifier.height(metrics.dp(18)))
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(38))) {
                    HeroMetric("${cup.tournament.daysToFinal ?: 0}", "DAYS TO FINAL")
                    HeroMetric("${cup.tournament.hostCities}", "HOST CITIES")
                }
                Spacer(Modifier.height(metrics.dp(10)))
                Text(cup.tournament.stage.uppercase(), color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
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
    LazyRow(horizontalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
        items(listOf("Matches", "Groups", "Bracket", "Library")) { tab ->
            Surface(
                color = if (selected == tab) GoalioColors.Accent else Color.Transparent,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, GoalioColors.Accent),
                modifier = Modifier.clickable { onSelected(tab) }
            ) {
                Text(tab.uppercase(), color = Color.White, fontSize = metrics.sp(12), fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = metrics.dp(16), vertical = metrics.dp(10)))
            }
        }
    }
}

@Composable
private fun WorldCupGroups(groups: List<WorldCupGroupInfo>) {
    val metrics = rememberGoalioMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
        SectionTitle("Live Groups")
        groups.take(4).forEach { group ->
            Surface(color = GoalioColors.Surface2, shape = RoundedCornerShape(metrics.dp(14)), border = BorderStroke(1.dp, Color(0xFF343434)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(metrics.dp(18)), verticalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
                    Text("Group ${group.code}", color = Color.White, fontSize = metrics.sp(20), fontWeight = FontWeight.Black)
                    group.teams.take(4).forEach { team ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${team.rank ?: "-"}", color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), modifier = Modifier.width(metrics.dp(24)))
                            Box(Modifier.size(metrics.dp(20)).clip(RoundedCornerShape(3.dp)).background(GoalioColors.Accent))
                            Spacer(Modifier.width(metrics.dp(12)))
                            Text(team.name, color = GoalioColors.TextPrimary, fontSize = metrics.sp(15), fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${team.points ?: 0} pts", color = GoalioColors.TextPrimary, fontSize = metrics.sp(13), fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorldCupBracket(rounds: List<WorldCupBracketRoundInfo>) {
    val metrics = rememberGoalioMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
        SectionTitle("The Knockout Path")
        if (rounds.isEmpty()) {
            WorldCupState("Bracket will appear when knockout fixtures are published.")
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
                items(rounds) { round ->
                    Surface(color = GoalioColors.Surface2, shape = RoundedCornerShape(metrics.dp(10)), border = BorderStroke(1.dp, Color(0xFF333333)), modifier = Modifier.width(metrics.dp(250))) {
                        Column(Modifier.padding(metrics.dp(16)), verticalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
                            Text(round.round.uppercase(), color = GoalioColors.TextSecondary, fontSize = metrics.sp(11), fontWeight = FontWeight.Black)
                            round.matches.take(3).forEach { match ->
                                Text("${match.homeTeam ?: "TBD"} ${match.homeScore?.toString() ?: "-"}  ·  ${match.awayTeam ?: "TBD"} ${match.awayScore?.toString() ?: "-"}", color = Color.White, fontSize = metrics.sp(14), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
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

@Composable
private fun WorldCupLibrary(cup: WorldCupBootstrapInfo) {
    val metrics = rememberGoalioMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
        SectionTitle("World Cup Library")
        Surface(color = Color(0xFF171717), shape = RoundedCornerShape(metrics.dp(14)), border = BorderStroke(1.dp, Color(0xFF2A2A2A)), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(metrics.dp(18))) {
                Text(cup.randomFact.title, color = GoalioColors.Accent, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
                Spacer(Modifier.height(metrics.dp(8)))
                Text(cup.randomFact.body, color = Color.White, fontSize = metrics.sp(17), fontWeight = FontWeight.Bold)
            }
        }
        cup.library.forEach { item ->
            Surface(color = GoalioColors.Surface2, shape = RoundedCornerShape(metrics.dp(14)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(metrics.dp(18))) {
                    Text(item.category.uppercase(), color = GoalioColors.Accent, fontSize = metrics.sp(11), fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(metrics.dp(8)))
                    Text(item.title, color = Color.White, fontSize = metrics.sp(18), fontWeight = FontWeight.Black)
                    Text(item.body, color = GoalioColors.TextSecondary, fontSize = metrics.sp(14), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(metrics.dp(10)))
                    Text("${item.readMinutes} min read", color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val metrics = rememberGoalioMetrics()
    Text(text, color = Color.White, fontSize = metrics.sp(22), fontWeight = FontWeight.Black)
}

@Composable
private fun WorldCupState(text: String) {
    val metrics = rememberGoalioMetrics()
    Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(metrics.dp(14)), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = GoalioColors.TextSecondary, fontSize = metrics.sp(15), fontWeight = FontWeight.Bold, modifier = Modifier.padding(metrics.dp(18)))
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

private fun worldCupScoreLine(match: ScheduleMatch): String {
    val home = match.homeTeam?.score
    val away = match.awayTeam?.score
    return if (home == null || away == null) "-" else "$home - $away"
}
