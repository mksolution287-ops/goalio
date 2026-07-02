package com.goalio.scores

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalio.scores.ui.theme.GoalioColors
import com.google.firebase.auth.FirebaseAuth
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class SettingsEditor { Profile, Teams, Players }

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onHome: () -> Unit,
    onMatches: () -> Unit,
    onWorldCup: () -> Unit,
    onGames: () -> Unit,
    onEditProfile: () -> Unit = {},
    onLanguage: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val metrics = rememberGoalioMetrics()
    val prefs = remember { context.getSharedPreferences("goalio_settings", Context.MODE_PRIVATE) }
    var profile by remember { mutableStateOf<BackendProfile?>(null) }
    var leaderboard by remember { mutableStateOf(QuizRepository.cachedLeaderboard(context)) }
    var notifications by remember { mutableStateOf(prefs.getBoolean("notifications_enabled", true)) }
    var localUsername by remember { mutableStateOf(prefs.getString("profile_username", null)) }
    var localTeams by remember { mutableStateOf(prefs.getStringSet("profile_team_names", emptySet()).orEmpty().toList().sorted()) }
    var localPlayers by remember { mutableStateOf(prefs.getStringSet("profile_player_names", emptySet()).orEmpty().toList().sorted()) }
    var editor by remember { mutableStateOf<SettingsEditor?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { GoalioBackendApi.getProfile() }
            .onSuccess { profile = it }
            .onFailure { error = it.message }
        leaderboard = runCatching { QuizRepository.leaderboard(context) }.getOrDefault(leaderboard)
    }

    val teams = localTeams.ifEmpty { profile?.favoriteTeams.orEmpty() }
    val players = localPlayers.ifEmpty { profile?.favoritePlayers.orEmpty() }
    val xp = leaderboard?.me?.xp ?: 0
    val level = xp / 100 + 1
    val levelXp = xp % 100
    val username = localUsername?.takeIf { it.isNotBlank() } ?: profile?.username ?: "Player"
    val member = profile?.createdAt?.let {
        runCatching { OffsetDateTime.parse(it).format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US)) }.getOrNull()
    }

    editor?.let { activeEditor ->
        SettingsEditDialog(
            editor = activeEditor,
            initialValue = when (activeEditor) {
                SettingsEditor.Profile -> username
                SettingsEditor.Teams -> teams.joinToString(", ")
                SettingsEditor.Players -> players.joinToString(", ")
            },
            onDismiss = { editor = null },
            onSave = { raw ->
                when (activeEditor) {
                    SettingsEditor.Profile -> {
                        val clean = raw.trim().ifBlank { "Player" }
                        localUsername = clean
                        prefs.edit().putString("profile_username", clean).apply()
                    }
                    SettingsEditor.Teams -> {
                        val clean = raw.toListValues()
                        localTeams = clean
                        prefs.edit().putStringSet("profile_team_names", clean.toSet()).apply()
                    }
                    SettingsEditor.Players -> {
                        val clean = raw.toListValues()
                        localPlayers = clean
                        prefs.edit().putStringSet("profile_player_names", clean.toSet()).apply()
                    }
                }
                editor = null
            }
        )
    }

    GoalioBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(
                start = metrics.horizontalPadding,
                end = metrics.horizontalPadding,
                top = metrics.dp(14),
                bottom = metrics.bottomBarPadding
            ),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(18))
        ) {
            item { SettingsHeader(onBack = onBack, onEdit = { editor = SettingsEditor.Profile }) }
            item { ProfileHero(username = username, member = member, level = level, onEdit = { editor = SettingsEditor.Profile }) }
            item { MasteryCard(level = level, levelXp = levelXp, totalXp = xp) }
            item { QuickStatsRow(teams = teams.size, players = players.size, notifications = notifications) }
            item {
                SettingsSection("Preferences") {
                    SettingsRow(
                        icon = Icons.Default.Language,
                        label = "Language",
                        value = prefs.getString("language", "English (UK)") ?: "English (UK)",
                        onClick = onLanguage
                    )
                    SettingsRow(
                        icon = Icons.Default.Notifications,
                        label = "Match Notifications",
                        value = if (notifications) "On" else "Off",
                        trailing = {
                            Switch(
                                checked = notifications,
                                onCheckedChange = {
                                    notifications = it
                                    prefs.edit().putBoolean("notifications_enabled", it).apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = GoalioColors.Tertiary,
                                    uncheckedThumbColor = GoalioColors.TextSecondary,
                                    uncheckedTrackColor = GoalioColors.Surface3
                                )
                            )
                        }
                    )
                    SettingsRow(
                        icon = Icons.Default.Groups,
                        label = "Favorite Teams",
                        value = teams.joinToString().ifBlank { "Add teams" },
                        onClick = { editor = SettingsEditor.Teams }
                    )
                    SettingsRow(
                        icon = Icons.Default.Person,
                        label = "Favorite Players",
                        value = players.joinToString().ifBlank { "Add players" },
                        onClick = { editor = SettingsEditor.Players },
                        showDivider = false
                    )
                }
            }
            item {
                SettingsSection("App") {
                    SettingsRow(
                        icon = Icons.Default.PrivacyTip,
                        label = "Privacy Policy",
                        value = "Open",
                        onClick = { context.openUrl("https://goalio.app/privacy") }
                    )
                    SettingsRow(
                        icon = Icons.Default.Info,
                        label = "Version",
                        value = BuildConfig.VERSION_NAME,
                        enabled = false,
                        showDivider = false
                    )
                }
            }
            item {
                SignOutCard {
                    FirebaseAuth.getInstance().signOut()
                    prefs.edit()
                        .remove("profile_complete")
                        .remove("profile_username")
                        .remove("profile_full_name")
                        .apply()
                    onSignOut()
                }
            }
            error?.let {
                item {
                    Text(it, color = GoalioColors.Live, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        GoalioBottomBar(Modifier.align(Alignment.BottomCenter), "", onHome, onMatches, onWorldCup, onGames)
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit, onEdit: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Row(Modifier.fillMaxWidth().height(metrics.dp(58)), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        Column(Modifier.weight(1f).padding(horizontal = metrics.dp(14))) {
            Text("SETTINGS", color = GoalioColors.Secondary, fontSize = metrics.sp(24), fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text("Profile, alerts and app controls", color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.SemiBold)
        }
        Surface(
            color = Color(0xFF241000),
            shape = RoundedCornerShape(metrics.dp(14)),
            border = BorderStroke(1.dp, GoalioColors.Tertiary.copy(alpha = .75f)),
            modifier = Modifier.height(metrics.dp(42)).clickable(onClick = onEdit)
        ) {
            Row(Modifier.padding(horizontal = metrics.dp(13)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(7))) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = GoalioColors.Tertiary, modifier = Modifier.size(metrics.dp(16)))
                Text("EDIT", color = GoalioColors.Tertiary, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(
        color = GoalioColors.Surface2,
        shape = CircleShape,
        border = BorderStroke(1.dp, GoalioColors.Border),
        modifier = Modifier.size(metrics.dp(42)).clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = GoalioColors.Secondary, modifier = Modifier.size(metrics.dp(21)))
        }
    }
}

@Composable
private fun ProfileHero(username: String, member: String?, level: Int, onEdit: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(
        color = GoalioColors.Surface1,
        shape = RoundedCornerShape(metrics.dp(22)),
        border = BorderStroke(1.dp, GoalioColors.CardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier
                .background(Brush.linearGradient(listOf(Color(0xFF211305), GoalioColors.Surface1, Color(0xFF101010))))
                .padding(metrics.dp(20))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(16))) {
                Box(
                    Modifier
                        .size(metrics.dp(86))
                        .background(Color(0xFF3A2100), CircleShape)
                        .padding(metrics.dp(4))
                        .background(GoalioColors.Tertiary, CircleShape)
                        .padding(metrics.dp(5))
                        .background(Color.Black, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(username.initialBadge(), color = Color.White, fontSize = metrics.sp(25), fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.dp(5))) {
                    Text(username, color = GoalioColors.Secondary, fontSize = metrics.sp(25), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Member since ${member ?: "recently"}", color = GoalioColors.TextSecondary, fontSize = metrics.sp(13), fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(8))) {
                        Pill("LEVEL $level", GoalioColors.Tertiary)
                        Pill("PRO FAN", GoalioColors.TextSecondary)
                    }
                }
                RoundIconButton(Icons.Default.Edit, "Edit profile", onEdit)
            }
        }
    }
}

@Composable
private fun Pill(label: String, color: Color) {
    val metrics = rememberGoalioMetrics()
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(50), border = BorderStroke(1.dp, color.copy(alpha = .55f))) {
        Text(label, color = color, fontSize = metrics.sp(10), fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = metrics.dp(9), vertical = metrics.dp(5)))
    }
}

@Composable
private fun MasteryCard(level: Int, levelXp: Int, totalXp: Int) {
    val metrics = rememberGoalioMetrics()
    val target = (totalXp / 100 + 1) * 100
    Surface(color = GoalioColors.Neutral, shape = RoundedCornerShape(metrics.dp(18)), border = BorderStroke(1.dp, GoalioColors.Border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(metrics.dp(18)), verticalArrangement = Arrangement.spacedBy(metrics.dp(14))) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("TRIVIA MASTERY", color = GoalioColors.TextSecondary, fontSize = metrics.sp(11), fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    Text("Level $level", color = GoalioColors.Tertiary, fontSize = metrics.sp(30), fontWeight = FontWeight.Black)
                }
                Text("$totalXp XP", color = GoalioColors.Secondary, fontSize = metrics.sp(18), fontWeight = FontWeight.Black)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$levelXp / 100", color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("Next: $target XP", color = GoalioColors.TextTertiary, fontSize = metrics.sp(12), fontWeight = FontWeight.Bold)
            }
            Box(Modifier.fillMaxWidth().height(metrics.dp(11)).background(GoalioColors.Surface3, CircleShape)) {
                Box(
                    Modifier
                        .fillMaxWidth((levelXp / 100f).coerceIn(.04f, 1f))
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(GoalioColors.Tertiary, Color(0xFFFFC247))), CircleShape)
                )
            }
        }
    }
}

@Composable
private fun QuickStatsRow(teams: Int, players: Int, notifications: Boolean) {
    val metrics = rememberGoalioMetrics()
    Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
        StatChip("Teams", teams.toString(), Icons.Default.Groups, Modifier.weight(1f))
        StatChip("Players", players.toString(), Icons.Default.Person, Modifier.weight(1f))
        StatChip("Alerts", if (notifications) "On" else "Off", Icons.Default.Notifications, Modifier.weight(1f))
    }
}

@Composable
private fun StatChip(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    val metrics = rememberGoalioMetrics()
    Surface(color = GoalioColors.Surface2, shape = RoundedCornerShape(metrics.dp(14)), border = BorderStroke(1.dp, GoalioColors.Border), modifier = modifier.height(metrics.dp(76))) {
        Column(Modifier.padding(metrics.dp(12)), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(6))) {
                Icon(icon, contentDescription = null, tint = GoalioColors.Tertiary, modifier = Modifier.size(metrics.dp(16)))
                Text(value, color = GoalioColors.Secondary, fontSize = metrics.sp(19), fontWeight = FontWeight.Black, maxLines = 1)
            }
            Text(label.uppercase(Locale.US), color = GoalioColors.TextTertiary, fontSize = metrics.sp(10), fontWeight = FontWeight.Black, letterSpacing = .8.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val metrics = rememberGoalioMetrics()
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
        Text(title.uppercase(Locale.US), color = GoalioColors.TextSecondary, fontSize = metrics.sp(11), fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.padding(start = metrics.dp(4)))
        Surface(color = GoalioColors.Neutral, shape = RoundedCornerShape(metrics.dp(18)), border = BorderStroke(1.dp, GoalioColors.Border), modifier = Modifier.fillMaxWidth()) {
            Column(content = content)
        }
    }
}

@Composable
private fun ColumnScope.SettingsRow(
    icon: ImageVector,
    label: String,
    value: String = "",
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    showDivider: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val metrics = rememberGoalioMetrics()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = metrics.dp(72))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = metrics.dp(16), vertical = metrics.dp(10)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = if (enabled) Color(0xFF241000) else GoalioColors.Surface3,
            shape = RoundedCornerShape(metrics.dp(12)),
            border = BorderStroke(1.dp, if (enabled) GoalioColors.Tertiary.copy(alpha = .42f) else GoalioColors.Border),
            modifier = Modifier.size(metrics.dp(42))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = if (enabled) GoalioColors.Tertiary else GoalioColors.Disabled, modifier = Modifier.size(metrics.dp(21)))
            }
        }
        Spacer(Modifier.width(metrics.dp(12)))
        Column(Modifier.weight(1f)) {
            Text(label, color = if (enabled) GoalioColors.TextPrimary else GoalioColors.Disabled, fontSize = metrics.sp(16), fontWeight = FontWeight.Black, maxLines = 1)
            if (value.isNotBlank() && trailing == null) {
                Text(value, color = if (enabled) GoalioColors.TextSecondary else GoalioColors.Disabled, fontSize = metrics.sp(12), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            trailing()
        } else if (enabled) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = GoalioColors.TextTertiary, modifier = Modifier.size(metrics.dp(20)))
        } else {
            Text(value, color = GoalioColors.Disabled, fontSize = metrics.sp(13), fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
    if (showDivider) {
        Box(Modifier.fillMaxWidth().padding(start = metrics.dp(70)).height(1.dp).background(GoalioColors.Divider))
    }
}

@Composable
private fun SignOutCard(onClick: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(
        color = Color(0xFF190606),
        shape = RoundedCornerShape(metrics.dp(18)),
        border = BorderStroke(1.dp, Color(0xFF4A1010)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(metrics.dp(18)), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFF2B0909), shape = CircleShape, modifier = Modifier.size(metrics.dp(42))) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color(0xFFFF8B82), modifier = Modifier.size(metrics.dp(21)))
                }
            }
            Spacer(Modifier.width(metrics.dp(12)))
            Column(Modifier.weight(1f)) {
                Text("Sign Out", color = Color(0xFFFFB1AA), fontSize = metrics.sp(17), fontWeight = FontWeight.Black)
                Text("Leave this Goalio account on this device", color = Color(0xFFB97772), fontSize = metrics.sp(12), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SettingsEditDialog(editor: SettingsEditor, initialValue: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val metrics = rememberGoalioMetrics()
    var text by remember(editor, initialValue) { mutableStateOf(initialValue) }
    val title = when (editor) {
        SettingsEditor.Profile -> "Edit Profile"
        SettingsEditor.Teams -> "Favorite Teams"
        SettingsEditor.Players -> "Favorite Players"
    }
    val helper = when (editor) {
        SettingsEditor.Profile -> "Update your display name for Goalio."
        SettingsEditor.Teams -> "Separate teams with commas."
        SettingsEditor.Players -> "Separate players with commas."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GoalioColors.Neutral,
        title = { Text(title, color = GoalioColors.Secondary, fontSize = metrics.sp(21), fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
                Text(helper, color = GoalioColors.TextSecondary, fontSize = metrics.sp(13), fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = editor == SettingsEditor.Profile,
                    minLines = if (editor == SettingsEditor.Profile) 1 else 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GoalioColors.Secondary,
                        unfocusedTextColor = GoalioColors.Secondary,
                        focusedBorderColor = GoalioColors.Tertiary,
                        unfocusedBorderColor = GoalioColors.Border,
                        cursorColor = GoalioColors.Tertiary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(colors = ButtonDefaults.buttonColors(containerColor = GoalioColors.Tertiary, contentColor = Color.Black), onClick = { onSave(text) }) {
                Text("Save", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GoalioColors.TextSecondary, fontWeight = FontWeight.Black)
            }
        }
    )
}

private fun String.toListValues(): List<String> =
    split(",", "\n")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()

private fun String.initialBadge(): String =
    Regex("\\p{L}+")
        .findAll(this)
        .map { it.value.first().uppercaseChar().toString() }
        .take(2)
        .joinToString("")
        .ifBlank { "PL" }

private fun Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
