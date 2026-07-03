package zero.ramjikvarosai.hirebazzar

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import zero.ramjikvarosai.hirebazzar.ui.theme.GoalioColors

private data class LeagueHubOption(val code: String, val label: String, val color: Color)

private val LeagueHubOptions = listOf(
    LeagueHubOption("eng.1", "Premier League", Color(0xFF7A3CFF)),
    LeagueHubOption("uefa.champions", "Champions League", Color(0xFF2F7DFF)),
    LeagueHubOption("esp.1", "LaLiga", Color(0xFFFF4D4D)),
    LeagueHubOption("ita.1", "Serie A", Color(0xFF25A6FF)),
    LeagueHubOption("ger.1", "Bundesliga", Color(0xFFE03012)),
    LeagueHubOption("fra.1", "Ligue 1", Color(0xFF20D97A))
)

@Composable
fun LeagueScreen(
    onBack: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenMatches: () -> Unit,
    onOpenGames: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val metrics = rememberGoalioMetrics()
    var selectedLeague by rememberSaveable { mutableStateOf(LeagueHubOptions.first().code) }
    val selected = LeagueHubOptions.first { it.code == selectedLeague }
    var hub by remember(selectedLeague) { mutableStateOf<LeagueHubInfo?>(null) }
    var standings by remember(selectedLeague) { mutableStateOf(MatchRepository.cachedStandings(context, selectedLeague)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedLeague) {
        loading = true
        error = null
        standings = MatchRepository.cachedStandings(context, selectedLeague)
        hub = null
        runCatching { GoalioBackendApi.getLeagueHub(selectedLeague) }
            .onSuccess {
                hub = it
                standings = it.standings
            }
            .onFailure { firstError ->
                runCatching { MatchRepository.refreshStandings(context, selectedLeague) }
                    .onSuccess { standings = it }
                    .onFailure { error = firstError.message ?: "Could not load league hub." }
            }
        loading = false
    }

    GoalioBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
            contentPadding = PaddingValues(metrics.horizontalPadding, metrics.dp(18), metrics.horizontalPadding, metrics.bottomBarPadding),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(18))
        ) {
            item { GoalioTopBar(title = "LEAGUE", onBack = onBack, onSettings = onOpenSettings) }
            item { LeagueHero(selected) }
            item { LeagueSelector(selectedLeague) { selectedLeague = it } }
            item {
                when {
                    standings != null -> LeagueTable(standings!!)
                    loading -> LeagueState("Loading ${selected.label} hub...")
                    else -> LeagueState(error ?: "${selected.label} table is not available yet.")
                }
            }
            item { LeagueBracketPreview(selected, hub?.bracket) }
            item { LeagueLibrary(selected, hub?.library.orEmpty()) }
        }
        GoalioBottomBar(Modifier.align(Alignment.BottomCenter), "League", onOpenHome, onOpenMatches, {}, onOpenGames)
    }
}

@Composable
private fun LeagueHero(league: LeagueHubOption) {
    val metrics = rememberGoalioMetrics()
    Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(metrics.dp(18)), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(metrics.dp(20)), verticalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
            Surface(color = league.color.copy(alpha = .18f), shape = CircleShape, border = BorderStroke(1.dp, league.color)) {
                Text("LEAGUE HUB", color = league.color, fontSize = metrics.sp(11), fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = metrics.dp(12), vertical = metrics.dp(6)))
            }
            Text(league.label, color = Color.White, fontSize = metrics.sp(28), fontWeight = FontWeight.Black)
            Text("Tables, brackets, and quick league guides in one simple screen.", color = GoalioColors.TextSecondary, fontSize = metrics.sp(14), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LeagueSelector(selected: String, onSelected: (String) -> Unit) {
    val metrics = rememberGoalioMetrics()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
        items(LeagueHubOptions) { league ->
            val active = league.code == selected
            Surface(
                color = if (active) league.color else GoalioColors.Surface2,
                shape = CircleShape,
                border = BorderStroke(1.dp, league.color.copy(alpha = if (active) 1f else .45f)),
                modifier = Modifier.clickable { onSelected(league.code) }
            ) {
                Text(league.label, color = if (active) Color.Black else GoalioColors.TextPrimary, fontSize = metrics.sp(12), fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = metrics.dp(14), vertical = metrics.dp(10)))
            }
        }
    }
}

@Composable
private fun LeagueTable(standings: LeagueStandings) {
    val metrics = rememberGoalioMetrics()
    val statsScrollState = rememberScrollState()
    Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(metrics.dp(16)), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = Modifier.fillMaxWidth()) {
        Column {
            Text("League Table", color = Color.White, fontSize = metrics.sp(20), fontWeight = FontWeight.Black, modifier = Modifier.padding(metrics.dp(16)))
            LeagueTableHeader(statsScrollState)
            standings.teams.forEachIndexed { index, team ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(GoalioColors.Divider))
                LeagueTableRow(index, team, statsScrollState)
            }
        }
    }
}

@Composable
private fun LeagueTableHeader(statsScrollState: androidx.compose.foundation.ScrollState) {
    val metrics = rememberGoalioMetrics()
    Row(Modifier.fillMaxWidth().background(GoalioColors.Neutral).padding(horizontal = metrics.dp(14), vertical = metrics.dp(8)), verticalAlignment = Alignment.CenterVertically) {
        Text("TEAM", color = GoalioColors.TextSecondary, fontSize = metrics.sp(10), fontWeight = FontWeight.Black, modifier = Modifier.width(metrics.dp(150)))
        Row(Modifier.weight(1f).horizontalScroll(statsScrollState), horizontalArrangement = Arrangement.spacedBy(metrics.dp(8))) {
            listOf("MP", "W", "D", "L", "GF", "GA", "GD", "PTS").forEach {
                Text(it, color = GoalioColors.TextSecondary, fontSize = metrics.sp(10), fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.width(metrics.dp(42)))
            }
        }
    }
}

@Composable
private fun LeagueTableRow(index: Int, team: StandingTeamInfo, statsScrollState: androidx.compose.foundation.ScrollState) {
    val metrics = rememberGoalioMetrics()
    val rank = team.rank ?: index + 1
    Row(Modifier.fillMaxWidth().padding(horizontal = metrics.dp(14), vertical = metrics.dp(12)), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.width(metrics.dp(150)), verticalAlignment = Alignment.CenterVertically) {
            Text(rank.toString(), color = if (rank <= 4) GoalioColors.Accent else GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.Black, modifier = Modifier.width(metrics.dp(24)))
            Box(Modifier.size(metrics.dp(22)), contentAlignment = Alignment.Center) {
                if (!team.logo.isNullOrBlank()) {
                    AsyncImage(model = team.logo, contentDescription = "${team.name} logo", modifier = Modifier.fillMaxSize())
                } else {
                    Text(team.abbreviation?.take(2) ?: team.name.take(2).uppercase(), color = GoalioColors.TextSecondary, fontSize = metrics.sp(10), fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.width(metrics.dp(8)))
            Text(team.name, color = Color.White, fontSize = metrics.sp(13), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.weight(1f).horizontalScroll(statsScrollState), horizontalArrangement = Arrangement.spacedBy(metrics.dp(8))) {
            LeagueStat(team.played)
            LeagueStat(team.wins)
            LeagueStat(team.draws)
            LeagueStat(team.losses)
            LeagueStat(team.goalsFor)
            LeagueStat(team.goalsAgainst)
            LeagueStat(team.goalDifference, signed = true)
            LeagueStat(team.points, highlight = true)
        }
    }
}

@Composable
private fun LeagueStat(value: Int?, signed: Boolean = false, highlight: Boolean = false) {
    val metrics = rememberGoalioMetrics()
    val text = when {
        value == null -> "-"
        signed && value > 0 -> "+$value"
        else -> value.toString()
    }
    Text(text, color = if (highlight) GoalioColors.Accent else GoalioColors.TextPrimary, fontSize = metrics.sp(13), fontWeight = if (highlight) FontWeight.Black else FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(metrics.dp(42)))
}

@Composable
private fun LeagueBracketPreview(league: LeagueHubOption, bracket: WorldCupBracketInfo?) {
    val metrics = rememberGoalioMetrics()
    Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(metrics.dp(16)), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(metrics.dp(16)), verticalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
            Text("League Brackets", color = Color.White, fontSize = metrics.sp(20), fontWeight = FontWeight.Black)
            if (bracket == null) {
                Text("Loading seeded bracket from backend.", color = GoalioColors.TextSecondary, fontSize = metrics.sp(13), fontWeight = FontWeight.SemiBold)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
                    items(listOf("QF" to "Quarterfinal", "SF" to "Semifinal", "FINAL" to "Final")) { (code, title) ->
                        BracketRoundColumn(title, bracket.rounds[code].orEmpty(), league.color)
                    }
                }
            }
            Text(bracket?.bracketType ?: "LEAGUE_SEEDED_KNOCKOUT", color = league.color, fontSize = metrics.sp(11), fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun BracketRoundColumn(title: String, matches: List<WorldCupBracketMatchInfo>, color: Color) {
    val metrics = rememberGoalioMetrics()
    Column(Modifier.width(metrics.dp(210)), verticalArrangement = Arrangement.spacedBy(metrics.dp(8))) {
        Text(title.uppercase(), color = color, fontSize = metrics.sp(11), fontWeight = FontWeight.Black)
        if (matches.isEmpty()) {
            Surface(color = GoalioColors.Surface2, shape = RoundedCornerShape(metrics.dp(10)), border = BorderStroke(1.dp, GoalioColors.Border), modifier = Modifier.fillMaxWidth()) {
                Text("No slots", color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.Bold, modifier = Modifier.padding(metrics.dp(12)))
            }
        } else {
            matches.forEach { match ->
                Surface(color = GoalioColors.Surface2, shape = RoundedCornerShape(metrics.dp(10)), border = BorderStroke(1.dp, GoalioColors.Border), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(metrics.dp(10)), verticalArrangement = Arrangement.spacedBy(metrics.dp(8))) {
                        Text(match.status ?: "Projected", color = GoalioColors.TextSecondary, fontSize = metrics.sp(10), fontWeight = FontWeight.Black)
                        BracketTeamText(match.homeTeam ?: "TBD", match.homeScore, color)
                        Box(Modifier.fillMaxWidth().height(1.dp).background(GoalioColors.Divider))
                        BracketTeamText(match.awayTeam ?: "TBD", match.awayScore, color)
                    }
                }
            }
        }
    }
}

@Composable
private fun BracketTeamText(name: String, score: Int?, color: Color) {
    val metrics = rememberGoalioMetrics()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = Color.White, fontSize = metrics.sp(12), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (score != null) Text(score.toString(), color = color, fontSize = metrics.sp(13), fontWeight = FontWeight.Black)
    }
}

@Composable
private fun LeagueLibrary(league: LeagueHubOption, items: List<WorldCupLibraryItemInfo>) {
    val metrics = rememberGoalioMetrics()
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
        Text("League Library", color = Color.White, fontSize = metrics.sp(20), fontWeight = FontWeight.Black)
        val displayItems = items.ifEmpty {
            listOf(WorldCupLibraryItemInfo("league-loading", "Loading league library", "Guide", "Backend league cards will appear here.", 2))
        }
        displayItems.forEach { item ->
            Surface(
                color = GoalioColors.Surface2,
                shape = RoundedCornerShape(metrics.dp(12)),
                border = BorderStroke(1.dp, GoalioColors.Border),
                modifier = Modifier.fillMaxWidth().clickable(enabled = !item.url.isNullOrBlank()) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) }
                }
            ) {
                Column(Modifier.padding(metrics.dp(14)), verticalArrangement = Arrangement.spacedBy(metrics.dp(4))) {
                    Text(item.category.uppercase(), color = league.color, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
                    Text(item.title, color = Color.White, fontSize = metrics.sp(15), fontWeight = FontWeight.Black)
                    Text(item.body, color = GoalioColors.TextSecondary, fontSize = metrics.sp(13), fontWeight = FontWeight.SemiBold)
                    Text("${item.readMinutes} min read", color = GoalioColors.TextTertiary, fontSize = metrics.sp(11), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LeagueState(text: String) {
    val metrics = rememberGoalioMetrics()
    Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(metrics.dp(14)), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = GoalioColors.TextSecondary, fontSize = metrics.sp(15), fontWeight = FontWeight.Bold, modifier = Modifier.padding(metrics.dp(18)))
    }
}
