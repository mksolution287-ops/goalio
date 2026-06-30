package com.goalio.scores

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage

data class FavoriteTeam(
    val id: String,
    val name: String,
    val shortName: String,
    val primaryColor: Color,
    val imageUrl: String? = null,
    val competitionIds: Set<Int> = emptySet()
)

data class FavoritePlayer(
    val id: String,
    val name: String,
    val team: String,
    val initials: String,
    val accent: Color,
    val imageUrl: String? = null,
    val competitionIds: Set<Int> = emptySet()
)

data class ProfileDraft(
    val fullName: String,
    val username: String,
    val teamIds: Set<String>,
    val playerIds: Set<String>
)

private val Gold = Color(0xFF897846)
private val Panel = Color(0xF2131514)
private val Field = Color(0xFF202322)
private val Muted = Color(0xFFAAA9AA)
private data class CompetitionFilter(val label: String, val id: Int?)
private val CompetitionFilters = listOf(
    CompetitionFilter("All", null),
    CompetitionFilter("World Cup", 1),
    CompetitionFilter("EPL", 39),
    CompetitionFilter("LaLiga", 140),
    CompetitionFilter("Serie A", 135),
    CompetitionFilter("Bundesliga", 78),
    CompetitionFilter("Ligue 1", 61)
)
private val FeaturedPlayerKeys = listOf("messi", "ronaldo", "mbapp", "haaland", "salah", "neymar")

@Composable
fun ProfileSetupScreen(
    onBack: () -> Unit,
    onComplete: suspend (ProfileDraft) -> String?
) {
    var fullName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var teamQuery by rememberSaveable { mutableStateOf("") }
    var playerQuery by rememberSaveable { mutableStateOf("") }
    var teamCompetitionId by rememberSaveable { mutableStateOf<Int?>(null) }
    var playerCompetitionId by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedTeams by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedPlayers by rememberSaveable { mutableStateOf(setOf<String>()) }
    var teamCatalog by remember { mutableStateOf(emptyList<FavoriteTeam>()) }
    var playerCatalog by remember { mutableStateOf(emptyList<FavoritePlayer>()) }
    var catalogLoading by remember { mutableStateOf(true) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var teamSelectionError by remember { mutableStateOf<String?>(null) }
    var playerSelectionError by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var usernameAvailable by remember { mutableStateOf<Boolean?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var checkingUsername by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val remoteConfig = remember { FirebaseRemoteConfig.getInstance() }
    val teamLimit = remoteConfig.getLong("profile_teams_limit").toInt()
        .takeIf { it > 0 }?.coerceAtMost(6) ?: 6
    val playerLimit = remoteConfig.getLong("profile_players_limit").toInt()
        .takeIf { it > 0 }?.coerceAtMost(6) ?: 6
    val scope = rememberCoroutineScope()
    val fullNameValid = isValidFullName(fullName)
    val usernameRuleValid = isValidUsername(username)
    val valid = fullNameValid && usernameAvailable == true
    val teams = remember(teamQuery, teamCatalog, teamCompetitionId) {
        teamCatalog.asSequence()
            .filter { teamCompetitionId == null || teamCompetitionId in it.competitionIds }
            .filter { it.name.contains(teamQuery, true) }
            .take(6)
            .toList()
    }
    val players = remember(playerQuery, playerCatalog, playerCompetitionId) {
        playerCatalog.asSequence()
            .filter { playerCompetitionId == null || playerCompetitionId in it.competitionIds }
            .filter { it.name.contains(playerQuery, true) || it.team.contains(playerQuery, true) }
            .sortedBy { player ->
                FeaturedPlayerKeys.indexOfFirst { it in player.name.lowercase() }
                    .takeIf { it >= 0 } ?: Int.MAX_VALUE
            }
            .take(6)
            .toList()
    }

    LaunchedEffect(Unit) {
        catalogError = null
        val cached = ProfileCatalogRepository.cached()
        if (cached != null) {
            teamCatalog = cached.teams
            playerCatalog = cached.players
            catalogLoading = false
            return@LaunchedEffect
        }
        runCatching { ProfileCatalogRepository.preload(context.applicationContext) }
            .onSuccess { catalog ->
                teamCatalog = catalog.teams
                playerCatalog = catalog.players
            }
            .onFailure { catalogError = it.userFacingBackendMessage("Could not load teams and players.") }
        catalogLoading = false
    }

    LaunchedEffect(teamQuery) {
        if (teamQuery.trim().length < 2) return@LaunchedEffect
        delay(250)
        runCatching { GoalioBackendApi.searchTeams(teamQuery) }.onSuccess { results ->
            teamCatalog = (teamCatalog + results).distinctBy { it.id }
        }
    }
    LaunchedEffect(playerQuery) {
        if (playerQuery.trim().length < 2) return@LaunchedEffect
        delay(250)
        runCatching { GoalioBackendApi.searchPlayers(playerQuery) }.onSuccess { results ->
            playerCatalog = (playerCatalog + results.map { it.withCompetitionIds(teamCatalog) })
                .distinctBy { it.id }
        }
    }
    LaunchedEffect(username) {
        usernameAvailable = null
        usernameError = null
        checkingUsername = false
        if (!usernameRuleValid) return@LaunchedEffect
        delay(350)
        checkingUsername = true
        runCatching { GoalioBackendApi.isUsernameAvailable(username) }
            .onSuccess { usernameAvailable = it }
            .onFailure { usernameError = it.userFacingBackendMessage("Could not check username.") }
        checkingUsername = false
    }

    BackHandler(onBack = onBack)
    GoalioBackground(.18f) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            ProfileHeader(onBack)
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    Surface(
                        color = Panel,
                        border = BorderStroke(1.dp, Color(0xFF303231)),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Text("Set up your profile", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                            Text("Make Goalio yours. You can change these anytime.", color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                            Spacer(Modifier.height(24.dp))
                            LabeledInput("FULL NAME", "e.g., Alex Morgan", fullName, { fullName = it }, true)
                            if (fullName.isNotBlank() && !fullNameValid) {
                                FieldMessage("Enter first and last name; every name must have at least 2 letters.", false)
                            }
                            Spacer(Modifier.height(18.dp))
                            LabeledInput(
                                "PICK YOUR USERNAME",
                                "e.g., GoalGetter99",
                                username,
                                { value ->
                                    username = value.lowercase().filter { it in 'a'..'z' || it.isDigit() || it == '_' }.take(20)
                                },
                                false,
                                showValid = usernameAvailable == true
                            )
                            when {
                                username.isBlank() -> Unit
                                !usernameRuleValid -> FieldMessage("3–20 characters; start with a letter. Use letters, numbers, or single underscores.", false)
                                checkingUsername -> FieldMessage("Checking username…", true)
                                usernameAvailable == true -> FieldMessage("Username is available", true)
                                usernameAvailable == false -> FieldMessage("That username is already taken", false)
                                usernameError != null -> FieldMessage(usernameError.orEmpty(), false)
                            }
                            Spacer(Modifier.height(30.dp))
                            Text("Follow your favorites", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("Get live updates for the teams you love.", color = Color(0xFFD0CFD0), fontSize = 15.sp)
                            if (catalogLoading) {
                                FieldMessage("Loading teams and players...", true)
                            }
                            if (catalogError != null) {
                                FieldMessage(catalogError.orEmpty(), false)
                            }
                            Spacer(Modifier.height(17.dp))
                            CompetitionFilterRow(teamCompetitionId) { teamCompetitionId = it }
                            Spacer(Modifier.height(12.dp))
                            SearchInput("Search teams...", teamQuery) { teamQuery = it }
                            Text(
                                "${selectedTeams.size}/$teamLimit teams selected",
                                color = Muted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                            )
                            if (teamSelectionError != null) {
                                FieldMessage(teamSelectionError.orEmpty(), false)
                            }
                            AnimatedVisibility(selectedTeams.isNotEmpty()) {
                                Row(
                                    Modifier.padding(top = 13.dp).horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    teamCatalog.filter { it.id in selectedTeams }.forEach { team ->
                                        SelectionChip(team.name) {
                                            selectedTeams = selectedTeams - team.id
                                            teamSelectionError = null
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(15.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                maxItemsInEachRow = 2
                            ) {
                                teams.forEach { team ->
                                    TeamCard(
                                        team,
                                        selected = team.id in selectedTeams,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            when {
                                                team.id in selectedTeams -> {
                                                    selectedTeams = selectedTeams - team.id
                                                    teamSelectionError = null
                                                }
                                                selectedTeams.size >= teamLimit -> {
                                                    teamSelectionError = "You can select up to $teamLimit teams."
                                                }
                                                else -> {
                                                    selectedTeams = selectedTeams + team.id
                                                    teamSelectionError = null
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                            Spacer(Modifier.height(28.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Who's your hero?", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                SearchGlyph(Modifier.size(25.dp), Color.White)
                            }
                            Spacer(Modifier.height(12.dp))
                            CompetitionFilterRow(playerCompetitionId) { playerCompetitionId = it }
                            Spacer(Modifier.height(12.dp))
                            SearchInput("Search players...", playerQuery) { playerQuery = it }
                            Text(
                                "${selectedPlayers.size}/$playerLimit players selected",
                                color = Muted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                            )
                            if (playerSelectionError != null) {
                                FieldMessage(playerSelectionError.orEmpty(), false)
                            }
                            Spacer(Modifier.height(15.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(players, key = { it.id }) { player ->
                                    PlayerCard(player, player.id in selectedPlayers) {
                                        when {
                                            player.id in selectedPlayers -> {
                                                selectedPlayers = selectedPlayers - player.id
                                                playerSelectionError = null
                                            }
                                            selectedPlayers.size >= playerLimit -> {
                                                playerSelectionError = "You can select up to $playerLimit players."
                                            }
                                            else -> {
                                                selectedPlayers = selectedPlayers + player.id
                                                playerSelectionError = null
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Surface(color = Color(0xF2131413), border = BorderStroke(1.dp, Color(0xFF3A3529))) {
                Column {
                    if (submitError != null) {
                        Text(
                            submitError.orEmpty(),
                            color = Color(0xFFFF8A80),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 26.dp, vertical = 4.dp)
                        )
                    }
                    Button(
                        enabled = valid && !submitting,
                        onClick = {
                            val draft = ProfileDraft(
                                fullName = fullName.trim(),
                                username = username.trim().lowercase(),
                                teamIds = selectedTeams,
                                playerIds = selectedPlayers
                            )
                            scope.launch {
                                submitting = true
                                submitError = onComplete(draft)
                                submitting = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp).height(58.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                            disabledContainerColor = Color(0xFF454545),
                            disabledContentColor = Color(0xFF999999)
                        ),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(if (submitting) "SAVING…" else "CONTINUE  →", fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            BackGlyph(Modifier.size(25.dp), Color.White)
        }
        Spacer(Modifier.weight(1f))
        Text("⚽ Goalio", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(38.dp))
    }
}

@Composable
private fun LabeledInput(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    capitalize: Boolean,
    showValid: Boolean = false
) {
    Column {
        Text(label, color = Color(0xFFDADADA), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
        Spacer(Modifier.height(7.dp))
        Row(
            Modifier.fillMaxWidth().height(60.dp).background(Field, RoundedCornerShape(13.dp))
                .border(1.dp, if (showValid) Gold else Color(0xFF565149), RoundedCornerShape(13.dp)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(hint, color = Color(0xFF77736B), fontSize = 16.sp)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(Color.White, 16.sp),
                    keyboardOptions = KeyboardOptions(capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (showValid) {
                Box(Modifier.size(24.dp).background(Color(0xFF28C76F), CircleShape), contentAlignment = Alignment.Center) {
                    CheckGlyph(Modifier.size(13.dp), Color(0xFF07180E))
                }
            }
        }
    }
}

@Composable
private fun FieldMessage(text: String, success: Boolean) {
    Text(
        text,
        color = if (success) Color(0xFF64D98B) else Color(0xFFFF8A80),
        fontSize = 11.sp,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
    )
}

private fun isValidFullName(value: String): Boolean {
    val parts = value.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return parts.size >= 2 && parts.all { part ->
        val letters = part.replace("-", "").replace("'", "")
        letters.length >= 2 && letters.all(Char::isLetter)
    }
}

private fun isValidUsername(value: String): Boolean {
    if (!Regex("[a-z][a-z0-9_]{2,19}").matches(value)) return false
    if (value.endsWith('_') || "__" in value) return false
    return value !in setOf("admin", "administrator", "goalio", "support", "moderator", "root", "system")
}

private fun Throwable.userFacingBackendMessage(prefix: String): String {
    if (this is BackendException && statusCode == 503) {
        return "$prefix Firebase quota is exhausted right now. Try again after quota resets."
    }
    return "$prefix Verify the backend connection and try again."
}

@Composable
private fun SearchInput(hint: String, value: String, onValueChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).background(Color.Black, RoundedCornerShape(50))
            .border(1.dp, Color(0xFF62583D), RoundedCornerShape(50)).padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchGlyph(Modifier.size(21.dp), Color(0xFFE5E5E5))
        Spacer(Modifier.width(13.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = Color(0xFF777A86), fontSize = 15.sp)
            BasicTextField(value, onValueChange, singleLine = true, textStyle = TextStyle(Color.White, 15.sp), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CompetitionFilterRow(selectedId: Int?, onSelected: (Int?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(CompetitionFilters, key = { it.label }) { competition ->
            val selected = competition.id == selectedId
            Surface(
                onClick = { onSelected(competition.id) },
                color = if (selected) Color.White else Color(0xFF242625),
                contentColor = if (selected) Color.Black else Color.White,
                border = BorderStroke(1.dp, if (selected) Color.White else Color(0xFF62583D)),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    competition.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SelectionChip(label: String, onRemove: () -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(50), onClick = onRemove) {
        Text("$label  ×", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp))
    }
}

@Composable
private fun TeamCard(team: FavoriteTeam, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color(0xFF292C2B),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color.White else Color(0xFF5D543A)),
        modifier = modifier.height(112.dp)
    ) {
        Box(Modifier.padding(12.dp)) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(width = 48.dp, height = 34.dp).clip(RoundedCornerShape(5.dp))
                        .background(Brush.verticalGradient(listOf(team.primaryColor.copy(alpha = .75f), team.primaryColor))),
                    contentAlignment = Alignment.Center
                ) {
                    if (team.imageUrl != null) {
                        AsyncImage(
                            model = team.imageUrl,
                            contentDescription = "${team.name} badge",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(team.shortName.take(1), color = if (team.shortName == "ENG") Color.Black else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(9.dp))
                Text(team.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            if (selected) {
                Box(Modifier.align(Alignment.TopEnd).size(20.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                    CheckGlyph(Modifier.size(11.dp), Color(0xFF6C5C2F))
                }
            }
        }
    }
}

@Composable
private fun PlayerCard(player: FavoritePlayer, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = Color(0xFF292C2B),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Color.White else Color(0xFF62583D)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.width(210.dp)
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(160.dp)
                    .background(Brush.radialGradient(listOf(player.accent.copy(alpha = .56f), Color(0xFF090B0B))))
            ) {
                if (player.imageUrl != null) {
                    AsyncImage(
                        model = player.imageUrl,
                        contentDescription = player.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    PlayerSilhouette(Modifier.align(Alignment.BottomCenter).size(132.dp), player.accent)
                }
                Surface(
                    color = Color(0xD90D0F0F), shape = CircleShape,
                    border = BorderStroke(1.dp, player.accent.copy(alpha = .8f)),
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).size(38.dp)
                ) { Box(contentAlignment = Alignment.Center) { Text(player.initials, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) } }
            }
            Column(Modifier.padding(13.dp)) {
                Text(player.name, color = Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(player.team, color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Surface(
                    onClick = onClick,
                    color = if (selected) Color.White else Color.Transparent,
                    contentColor = if (selected) Color.Black else Color.White,
                    border = BorderStroke(1.dp, Color.White),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(39.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (selected) "PINNED ✓" else "+ PIN TO DASHBOARD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchGlyph(modifier: Modifier, color: Color) = Canvas(modifier) {
    drawCircle(color, radius = size.minDimension * .3f, center = Offset(size.width * .42f, size.height * .42f), style = Stroke(size.minDimension * .12f))
    drawLine(color, Offset(size.width * .64f, size.height * .64f), Offset(size.width * .91f, size.height * .91f), size.minDimension * .12f, StrokeCap.Round)
}

@Composable
private fun BackGlyph(modifier: Modifier, color: Color) = Canvas(modifier) {
    drawLine(color, Offset(size.width * .85f, size.height * .5f), Offset(size.width * .15f, size.height * .5f), size.minDimension * .1f, StrokeCap.Round)
    drawLine(color, Offset(size.width * .15f, size.height * .5f), Offset(size.width * .43f, size.height * .2f), size.minDimension * .1f, StrokeCap.Round)
    drawLine(color, Offset(size.width * .15f, size.height * .5f), Offset(size.width * .43f, size.height * .8f), size.minDimension * .1f, StrokeCap.Round)
}

@Composable
private fun CheckGlyph(modifier: Modifier, color: Color) = Canvas(modifier) {
    val path = Path().apply {
        moveTo(size.width * .12f, size.height * .52f)
        lineTo(size.width * .4f, size.height * .8f)
        lineTo(size.width * .9f, size.height * .2f)
    }
    drawPath(path, color, style = Stroke(size.minDimension * .14f, cap = StrokeCap.Round))
}

@Composable
private fun PlayerSilhouette(modifier: Modifier, accent: Color) = Canvas(modifier) {
    drawCircle(Color(0xFFE8C7AF), size.minDimension * .13f, Offset(size.width * .5f, size.height * .25f))
    drawCircle(Color(0xFF17130F), size.minDimension * .135f, Offset(size.width * .5f, size.height * .2f))
    val body = Path().apply {
        moveTo(size.width * .27f, size.height)
        lineTo(size.width * .32f, size.height * .48f)
        lineTo(size.width * .44f, size.height * .38f)
        lineTo(size.width * .56f, size.height * .38f)
        lineTo(size.width * .68f, size.height * .48f)
        lineTo(size.width * .73f, size.height)
        close()
    }
    drawPath(body, accent)
    drawLine(Color.White.copy(alpha = .65f), Offset(size.width * .5f, size.height * .44f), Offset(size.width * .5f, size.height), size.width * .025f)
}
