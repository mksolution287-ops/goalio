package zero.ramjikvarosai.hirebazzar

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import zero.ramjikvarosai.hirebazzar.ui.theme.GoalioColors
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("goalio_settings", Context.MODE_PRIVATE) }
    var profile by remember { mutableStateOf<BackendProfile?>(null) }
    var leaderboard by remember { mutableStateOf(QuizRepository.cachedLeaderboard(context)) }
    var notifications by remember { mutableStateOf(prefs.getBoolean("notifications_enabled", true)) }
    var localUsername by remember { mutableStateOf(prefs.getString("profile_username", null)) }
    var localTeams by remember { mutableStateOf(prefs.getStringSet("profile_team_names", emptySet()).orEmpty().toList().sorted()) }
    var localPlayers by remember { mutableStateOf(prefs.getStringSet("profile_player_names", emptySet()).orEmpty().toList().sorted()) }
    var editor by remember { mutableStateOf<SettingsEditor?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val langCode = prefs.getString("language", "en-GB") ?: "en-GB"
    val displayName = when (langCode) {
        "system" -> "System Default"
        "ar-SA" -> "العربية"
        "en-GB" -> "English"
        "fr-FR" -> "Français"
        "de-DE" -> "Deutsch"
        "hi-IN" -> "हिन्दी"
        "it-IT" -> "Italiano"
        "ja-JP" -> "日本語"
        "ko-KR" -> "한국어"
        "pt-BR" -> "Português"
        "ru-RU" -> "Русский"
        "zh-CN" -> "简体中文"
        "es-419" -> "Español"
        "zh-TW" -> "繁體中文"
        else -> "English"
    }

    LaunchedEffect(Unit) {
        runCatching { GoalioBackendApi.getProfile() }
            .onSuccess { profile = it }
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
        when (activeEditor) {
            SettingsEditor.Profile -> SettingsEditDialog(
                initialValue = username,
                onDismiss = { editor = null },
                onSave = { raw ->
                    val clean = raw.trim().ifBlank { "Player" }
                    localUsername = clean
                    prefs.edit().putString("profile_username", clean).apply()
                    editor = null
                }
            )
            SettingsEditor.Teams -> TeamPickerDialog(
                initialSelection = teams,
                onDismiss = { editor = null },
                onSave = { clean ->
                    localTeams = clean.sorted()
                    prefs.edit().putStringSet("profile_team_names", clean.toSet()).apply()
                    editor = null
                }
            )
            SettingsEditor.Players -> PlayerPickerDialog(
                initialSelection = players,
                onDismiss = { editor = null },
                onSave = { clean ->
                    localPlayers = clean.sorted()
                    prefs.edit().putStringSet("profile_player_names", clean.toSet()).apply()
                    editor = null
                }
            )
        }
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
                SettingsSection(trans("Preferences")) {
                    SettingsRow(
                        icon = Icons.Default.Language,
                        label = trans("Language"),
                        value = trans(displayName),
                        onClick = onLanguage
                    )
                    SettingsRow(
                        icon = Icons.Default.Notifications,
                        label = trans("Match Notifications"),
                        value = if (notifications) trans("On") else trans("Off"),
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
                        label = trans("Favorite Teams"),
                        value = teams.joinToString().ifBlank { trans("Add teams") },
                        onClick = { editor = SettingsEditor.Teams }
                    )
                    SettingsRow(
                        icon = Icons.Default.Person,
                        label = trans("Favorite Players"),
                        value = players.joinToString().ifBlank { trans("Add players") },
                        onClick = { editor = SettingsEditor.Players },
                        showDivider = false
                    )
                }
            }
            item {
                SettingsSection(trans("App")) {
                    SettingsRow(
                        icon = Icons.Default.PrivacyTip,
                        label = trans("Privacy Policy"),
                        value = trans("Open"),
                        onClick = { context.openUrl(GoalioRemoteConfig.privacyPolicyUrl()) }
                    )
                    SettingsRow(
                        icon = Icons.Default.Info,
                        label = trans("Version"),
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
        RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, trans("Back"), onBack)
        Column(Modifier.weight(1f).padding(horizontal = metrics.dp(14))) {
            Text(trans("SETTINGS"), color = GoalioColors.Secondary, fontSize = metrics.sp(24), fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text(trans("Profile, alerts and app controls"), color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.SemiBold)
        }
        Surface(
            color = Color(0xFF241000),
            shape = RoundedCornerShape(metrics.dp(14)),
            border = BorderStroke(1.dp, GoalioColors.Tertiary.copy(alpha = .75f)),
            modifier = Modifier.height(metrics.dp(42)).clickable(onClick = onEdit)
        ) {
            Row(Modifier.padding(horizontal = metrics.dp(13)), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(7))) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = GoalioColors.Tertiary, modifier = Modifier.size(metrics.dp(16)))
                Text(trans("EDIT"), color = GoalioColors.Tertiary, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
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
                    Text("${trans("Member since")} ${member ?: trans("recently")}", color = GoalioColors.TextSecondary, fontSize = metrics.sp(13), fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(8))) {
                        Pill("${trans("Level")} $level", GoalioColors.Tertiary)
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
                    Text("TRIVIA ${trans("Mastery").uppercase()}", color = GoalioColors.TextSecondary, fontSize = metrics.sp(11), fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    Text("${trans("Level")} $level", color = GoalioColors.Tertiary, fontSize = metrics.sp(30), fontWeight = FontWeight.Black)
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
        StatChip(trans("Teams"), teams.toString(), Icons.Default.Groups, Modifier.weight(1f))
        StatChip(trans("Players"), players.toString(), Icons.Default.Person, Modifier.weight(1f))
        StatChip(trans("Alerts"), if (notifications) trans("On") else trans("Off"), Icons.Default.Notifications, Modifier.weight(1f))
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
        Box(Modifier.size(metrics.dp(34)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = if (enabled) Color.White else GoalioColors.Disabled, modifier = Modifier.size(metrics.dp(23)))
        }
        Spacer(Modifier.width(metrics.dp(14)))
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
        Box(Modifier.fillMaxWidth().padding(start = metrics.dp(64)).height(1.dp).background(GoalioColors.Divider))
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
                Text(trans("Sign Out"), color = Color(0xFFFFB1AA), fontSize = metrics.sp(17), fontWeight = FontWeight.Black)
                Text(stringResource(R.string.leave_account, APP_DISPLAY_NAME), color = Color(0xFFB97772), fontSize = metrics.sp(12), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SettingsEditDialog(initialValue: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val metrics = rememberGoalioMetrics()
    var text by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GoalioColors.Neutral,
        title = { Text(trans("Edit Profile"), color = GoalioColors.Secondary, fontSize = metrics.sp(21), fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
                Text(stringResource(R.string.update_display_name, APP_DISPLAY_NAME), color = GoalioColors.TextSecondary, fontSize = metrics.sp(13), fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
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
                Text(trans("Save"), fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(trans("Cancel"), color = GoalioColors.TextSecondary, fontWeight = FontWeight.Black)
            }
        }
    )
}

@Composable
private fun TeamPickerDialog(initialSelection: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    CatalogPickerDialog(
        title = trans("Favorite Teams"),
        searchHint = trans("Search teams"),
        initialSelection = initialSelection,
        itemLabel = { it.name },
        loadPage = { query, cursor ->
            if (query.trim().length >= 2) GoalioBackendApi.searchTeams(query, limit = 6, cursor = cursor)
            else GoalioBackendApi.getTeams(limit = 6, cursor = cursor)
        },
        itemContent = { team, selected, onToggle -> TeamPickerItem(team, selected, onToggle) },
        onDismiss = onDismiss,
        onSave = onSave
    )
}

@Composable
private fun PlayerPickerDialog(initialSelection: List<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    CatalogPickerDialog(
        title = trans("Favorite Players"),
        searchHint = trans("Search players"),
        initialSelection = initialSelection,
        itemLabel = { it.name },
        loadPage = { query, cursor ->
            if (query.trim().length >= 2) GoalioBackendApi.searchPlayers(query, limit = 6, cursor = cursor)
            else GoalioBackendApi.getPlayers(limit = 6, cursor = cursor)
        },
        itemContent = { player, selected, onToggle -> PlayerPickerItem(player, selected, onToggle) },
        onDismiss = onDismiss,
        onSave = onSave
    )
}

@Composable
private fun <T> CatalogPickerDialog(
    title: String,
    searchHint: String,
    initialSelection: List<String>,
    itemLabel: (T) -> String,
    loadPage: suspend (String, String?) -> BackendPage<T>,
    itemContent: @Composable (T, Boolean, () -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    val metrics = rememberGoalioMetrics()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf(emptyList<T>()) }
    var selected by remember(initialSelection) { mutableStateOf(initialSelection.toSet()) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun merge(existing: List<T>, incoming: List<T>): List<T> =
        (existing + incoming).distinctBy(itemLabel)

    LaunchedEffect(query) {
        loading = true
        error = null
        delay(if (query.trim().length >= 2) 250 else 0)
        runCatching { loadPage(query, null) }
            .onSuccess { page ->
                items = page.items
                nextCursor = page.nextCursor
            }
            .onFailure { error = it.settingsMessage("Could not load $title.") }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = GoalioColors.Neutral,
        title = { Text(title, color = Color.White, fontSize = metrics.sp(21), fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
                Text("${selected.size}/6 ${trans("selected")}", color = GoalioColors.TextSecondary, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(searchHint, color = GoalioColors.TextTertiary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GoalioColors.Secondary,
                        unfocusedTextColor = GoalioColors.Secondary,
                        focusedBorderColor = GoalioColors.Tertiary,
                        unfocusedBorderColor = GoalioColors.Border,
                        cursorColor = GoalioColors.Tertiary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = GoalioColors.Live, fontSize = metrics.sp(12)) }
                if (loading) {
                    Text(trans("Loading..."), color = GoalioColors.TextSecondary, fontSize = metrics.sp(13), fontWeight = FontWeight.SemiBold)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
                        items(items) { item ->
                            val label = itemLabel(item)
                            itemContent(item, label in selected) {
                                selected = when {
                                    label in selected -> selected - label
                                    selected.size >= 6 -> selected
                                    else -> selected + label
                                }
                            }
                        }
                    }
                }
                if (!loading && nextCursor != null) {
                    Button(
                        enabled = !loadingMore,
                        onClick = {
                            val cursor = nextCursor ?: return@Button
                            scope.launch {
                                loadingMore = true
                                runCatching { loadPage(query, cursor) }
                                    .onSuccess { page ->
                                        items = merge(items, page.items)
                                        nextCursor = page.nextCursor
                                    }
                                    .onFailure { error = it.settingsMessage("Could not load more.") }
                                loadingMore = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoalioColors.Surface2, contentColor = Color.White),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(trans(if (loadingMore) "LOADING..." else "LOAD 6 MORE"), fontWeight = FontWeight.Black)
                    }
                }
                if (selected.size >= 6) {
                    Text(trans("Maximum 6 selections."), color = GoalioColors.Tertiary, fontSize = metrics.sp(12), fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(colors = ButtonDefaults.buttonColors(containerColor = GoalioColors.Tertiary, contentColor = Color.Black), onClick = { onSave(selected.toList()) }) {
                Text(trans("Save"), fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(trans("Cancel"), color = GoalioColors.TextSecondary, fontWeight = FontWeight.Black)
            }
        }
    )
}

@Composable
private fun TeamPickerItem(team: FavoriteTeam, selected: Boolean, onToggle: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    var imageFailed by remember(team.imageUrl) { mutableStateOf(false) }
    Surface(
        color = GoalioColors.Surface1,
        shape = RoundedCornerShape(metrics.dp(16)),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) GoalioColors.Tertiary else GoalioColors.Border),
        modifier = Modifier.width(metrics.dp(132)).height(metrics.dp(132)).clickable(onClick = onToggle)
    ) {
        Column(Modifier.padding(metrics.dp(12)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(metrics.dp(54)).clip(CircleShape).background(GoalioColors.Surface3), contentAlignment = Alignment.Center) {
                if (team.imageUrl != null && !imageFailed) {
                    AsyncImage(model = team.imageUrl, contentDescription = team.name, onError = { imageFailed = true }, modifier = Modifier.fillMaxSize().padding(metrics.dp(5)))
                } else {
                    Text(team.shortName.take(3).uppercase(), color = Color.White, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(metrics.dp(9)))
            Text(team.name, color = Color.White, fontSize = metrics.sp(12), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PlayerPickerItem(player: FavoritePlayer, selected: Boolean, onToggle: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    var imageFailed by remember(player.imageUrl) { mutableStateOf(false) }
    Surface(
        color = GoalioColors.Surface1,
        shape = RoundedCornerShape(metrics.dp(16)),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) GoalioColors.Tertiary else GoalioColors.Border),
        modifier = Modifier.width(metrics.dp(160)).height(metrics.dp(160)).clickable(onClick = onToggle)
    ) {
        Column(Modifier.padding(metrics.dp(12)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(metrics.dp(58)).clip(CircleShape).background(GoalioColors.Surface3), contentAlignment = Alignment.Center) {
                if (player.imageUrl != null && !imageFailed) {
                    AsyncImage(model = player.imageUrl, contentDescription = player.name, onError = { imageFailed = true }, modifier = Modifier.fillMaxSize())
                } else {
                    Text(player.initials, color = Color.White, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(metrics.dp(9)))
            Text(player.name, color = Color.White, fontSize = metrics.sp(13), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(player.team, color = GoalioColors.TextSecondary, fontSize = metrics.sp(11), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

private fun Throwable.settingsMessage(prefix: String): String =
    if (this is BackendException && statusCode == 503) {
        "$prefix Server is busy right now. Please retry later."
    } else {
        "$prefix Check your internet connection and retry later."
    }

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
