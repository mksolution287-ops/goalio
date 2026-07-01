package com.goalio.scores

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalio.scores.ui.theme.GoalioColors
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(onBack: () -> Unit, onHome: () -> Unit, onMatches: () -> Unit, onWorldCup: () -> Unit, onGames: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("goalio_settings", Context.MODE_PRIVATE) }
    var profile by remember { mutableStateOf<BackendProfile?>(null) }
    var leaderboard by remember { mutableStateOf(QuizRepository.cachedLeaderboard(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { GoalioBackendApi.getProfile() }.onSuccess { profile = it }.onFailure { error = it.message }
        leaderboard = runCatching { QuizRepository.leaderboard(context) }.getOrDefault(leaderboard)
    }
    val favoriteTeams = profile?.favoriteTeams?.takeIf { it.isNotEmpty() }
        ?: prefs.getStringSet("profile_team_names", emptySet()).orEmpty().toList()
    val favoritePlayers = profile?.favoritePlayers?.takeIf { it.isNotEmpty() }
        ?: prefs.getStringSet("profile_player_names", emptySet()).orEmpty().toList()
    val xp = leaderboard?.me?.xp ?: 0
    val username = profile?.username ?: prefs.getString("profile_username", null) ?: "player"
    val fullName = profile?.name ?: prefs.getString("profile_full_name", null)

    GoalioBackground {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().widthIn(max = 640.dp).align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 22.dp, top = 16.dp, end = 22.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item { GoalioTopBar("GOALIO", onBack = onBack, onSettings = {}) }
            item { ProfileHero(username, fullName, profile?.createdAt) }
            item { StatsRow(xp) }
            item { SectionTitle("PREFERENCES") }
            item {
                SettingsGroup(buildList {
                    add("◎" to "Language" to "English")
                    add("♢" to "Notifications" to "Enabled")
                    if (favoriteTeams.isNotEmpty()) add("⚽" to "Favorite Teams" to favoriteTeams.joinToString(", "))
                    if (favoritePlayers.isNotEmpty()) add("★" to "Favorite Players" to favoritePlayers.joinToString(", "))
                })
            }
            item { SectionTitle("SUPPORT") }
            item { SettingsGroup(listOf("◈" to "Privacy Policy" to "Open", "☆" to "Rate App" to "", "↗" to "Share App" to "", "ⓘ" to "Version" to "1.0")) }
            error?.let { item { Text(it, color = GoalioColors.Live, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) } }
        }
        GoalioBottomBar(Modifier.align(Alignment.BottomCenter), "", onHome, onMatches, onWorldCup, onGames)
    }
}

@Composable private fun ProfileHero(username: String, name: String?, createdAt: String?) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(116.dp).background(GoalioColors.Accent, CircleShape).padding(6.dp).background(Color(0xFF202020), CircleShape), contentAlignment = Alignment.Center) {
            Text(username.take(2).uppercase(), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(14.dp)); Text("@$username", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black)
        val member = createdAt?.let { runCatching { OffsetDateTime.parse(it).format(DateTimeFormatter.ofPattern("MMM yyyy")) }.getOrNull() }
        Text(listOfNotNull(name, member?.let { "Member since $it" }).joinToString(" • "), color = GoalioColors.TextSecondary, fontSize = 14.sp)
    }
}

@Composable private fun StatsRow(xp: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        StatCard("TRIVIA XP", xp.toString(), "Global leaderboard", Modifier.weight(1f))
        StatCard("LEVEL", (xp / 100 + 1).toString(), "${xp % 100} / 100 XP", Modifier.weight(1f))
    }
}

@Composable private fun StatCard(label: String, value: String, footer: String, modifier: Modifier) {
    Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = modifier.heightIn(min = 150.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) { Text(label, color = GoalioColors.TextSecondary, fontWeight = FontWeight.Black); Text(value, color = GoalioColors.Accent, fontSize = 34.sp, fontWeight = FontWeight.Black); Text(footer, color = GoalioColors.TextSecondary, fontSize = 12.sp) }
    }
}

@Composable private fun SectionTitle(value: String) = Text(value, color = GoalioColors.TextSecondary, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)

@Composable private fun SettingsGroup(rows: List<Pair<Pair<String, String>, String>>) {
    Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, GoalioColors.CardBorder)) {
        Column(Modifier.fillMaxWidth()) { rows.forEachIndexed { index, row ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(GoalioColors.CardBorder))
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Text(row.first.first, color = GoalioColors.TextPrimary, fontSize = 22.sp, modifier = Modifier.width(42.dp)); Text(row.first.second, color = GoalioColors.TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(row.second, color = GoalioColors.TextSecondary, maxLines = 2, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 180.dp)) }
        } }
    }
}
