package com.goalio.scores

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import com.goalio.scores.ui.theme.GoalioColors
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

private val Gold = GoalioColors.Accent
private val Panel = GoalioColors.Surface1
private val Field = GoalioColors.Surface2
private val Muted = GoalioColors.TextSecondary
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
    onSignIn: suspend (String, String) -> String?,
    onComplete: suspend (ProfileDraft) -> String?
) {
    val metrics = rememberGoalioMetrics()
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
    var nextTeamCursor by remember { mutableStateOf<String?>(null) }
    var nextPlayerCursor by remember { mutableStateOf<String?>(null) }
    var catalogLoading by remember { mutableStateOf(true) }
    var loadingMoreTeams by remember { mutableStateOf(false) }
    var loadingMorePlayers by remember { mutableStateOf(false) }
    var teamFilterLoading by remember { mutableStateOf(false) }
    var playerFilterLoading by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var catalogReloadKey by remember { mutableStateOf(0) }
    var teamSelectionError by remember { mutableStateOf<String?>(null) }
    var playerSelectionError by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }
    var usernameAvailable by remember { mutableStateOf<Boolean?>(null) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var checkingUsername by remember { mutableStateOf(false) }
    var identityMatches by remember { mutableStateOf<Boolean?>(null) }
    var checkingIdentity by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val remoteConfig = remember { FirebaseRemoteConfig.getInstance() }
    val teamLimit = remoteConfig.getLong("profile_teams_limit").toInt()
        .takeIf { it > 0 }?.coerceAtMost(6) ?: 6
    val playerLimit = remoteConfig.getLong("profile_players_limit").toInt()
        .takeIf { it > 0 }?.coerceAtMost(6) ?: 6
    val scope = rememberCoroutineScope()
    val actionInteraction = remember { MutableInteractionSource() }
    val actionPressed by actionInteraction.collectIsPressedAsState()
    val actionColor by animateColorAsState(
        if (actionPressed || submitting) GoalioColors.Tertiary else Color(0xFF241000),
        label = "profile action color"
    )
    val fullNameValid = isValidFullName(fullName)
    val usernameRuleValid = isValidUsername(username)
    val existingProfile = identityMatches == true
    val valid = fullNameValid && usernameRuleValid && !checkingUsername && !checkingIdentity && (usernameAvailable == true || existingProfile)
    val teams = remember(teamQuery, teamCatalog, teamCompetitionId) {
        teamCatalog.asSequence()
            .filter { teamCompetitionId == null || teamCompetitionId in it.competitionIds }
            .filter { it.name.contains(teamQuery, true) }
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
            .toList()
    }

    LaunchedEffect(catalogReloadKey) {
        catalogLoading = true
        catalogError = null
        val cached = if (catalogReloadKey == 0) ProfileCatalogRepository.cached(context) else null
        if (cached != null) {
            teamCatalog = cached.teams
            playerCatalog = cached.players
            nextTeamCursor = cached.nextTeamCursor
            nextPlayerCursor = cached.nextPlayerCursor
            catalogError = cached.catalogErrorMessage()
            catalogLoading = false
        }
        runCatching { ProfileCatalogRepository.preload(context.applicationContext, force = true) }
            .onSuccess { catalog ->
                teamCatalog = catalog.teams
                playerCatalog = catalog.players
                nextTeamCursor = catalog.nextTeamCursor
                nextPlayerCursor = catalog.nextPlayerCursor
                catalogError = catalog.catalogErrorMessage()
            }
            .onFailure { catalogError = it.userFacingBackendMessage("Could not load teams and players.") }
        catalogLoading = false
    }

    LaunchedEffect(teamQuery) {
        if (teamQuery.trim().length < 2) return@LaunchedEffect
        delay(250)
        runCatching { GoalioBackendApi.searchTeams(teamQuery, limit = 6) }.onSuccess { page ->
            teamCatalog = (teamCatalog + page.items).distinctBy { it.id }
            nextTeamCursor = page.nextCursor
        }
    }
    LaunchedEffect(playerQuery) {
        if (playerQuery.trim().length < 2) return@LaunchedEffect
        delay(250)
        runCatching { GoalioBackendApi.searchPlayers(playerQuery, limit = 6) }.onSuccess { page ->
            playerCatalog = (playerCatalog + page.items.map { it.withCompetitionIds(teamCatalog) })
                .distinctBy { it.id }
            nextPlayerCursor = page.nextCursor
        }
    }
    LaunchedEffect(teamCompetitionId) {
        val competitionId = teamCompetitionId ?: return@LaunchedEffect
        teamFilterLoading = true
        runCatching { GoalioBackendApi.getTeams(limit = 20, competitionId = competitionId) }
            .onSuccess { page -> teamCatalog = (teamCatalog + page.items).distinctBy { it.id }; nextTeamCursor = page.nextCursor }
            .onFailure { catalogError = it.userFacingBackendMessage("Could not load teams for this competition.") }
        teamFilterLoading = false
    }
    LaunchedEffect(playerCompetitionId) {
        val competitionId = playerCompetitionId ?: return@LaunchedEffect
        playerFilterLoading = true
        runCatching { GoalioBackendApi.getPlayers(limit = 20, competitionId = competitionId) }
            .onSuccess { page -> playerCatalog = (playerCatalog + page.items.map { it.withCompetitionIds(teamCatalog) }).distinctBy { it.id }; nextPlayerCursor = page.nextCursor }
            .onFailure { catalogError = it.userFacingBackendMessage("Could not load players for this competition.") }
        playerFilterLoading = false
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
    LaunchedEffect(fullName, username, usernameAvailable) {
        identityMatches = null
        checkingIdentity = false
        if (usernameAvailable != false || !fullNameValid || !usernameRuleValid) return@LaunchedEffect
        delay(350)
        checkingIdentity = true
        runCatching { GoalioBackendApi.profileIdentityMatches(fullName.trim(), username.trim()) }
            .onSuccess { identityMatches = it }
            .onFailure { identityMatches = false }
        checkingIdentity = false
    }

    BackHandler(onBack = onBack)
    GoalioBackground(.18f) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ProfileHeader(onBack)
            LazyColumn(
                modifier = Modifier.weight(1f).imePadding(),
                contentPadding = PaddingValues(start = metrics.horizontalPadding, end = metrics.horizontalPadding, top = metrics.dp(10), bottom = metrics.dp(24)),
                verticalArrangement = Arrangement.spacedBy(metrics.dp(18))
            ) {
                item {
                    Text("BUILD YOUR PROFILE", color = GoalioColors.Tertiary, fontSize = metrics.sp(11), fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("Make Goalio yours", color = Color.White, fontSize = metrics.sp(29), fontWeight = FontWeight.Black)
                    Text("Choose your identity, teams and players. You can update these anytime.", color = GoalioColors.TextSecondary, fontSize = metrics.sp(14), lineHeight = metrics.sp(20))
                }
                item {
                    Surface(
                        color = Panel,
                        border = BorderStroke(1.dp, GoalioColors.CardBorder),
                        shape = RoundedCornerShape(metrics.dp(24)),
                        modifier = Modifier.fillMaxWidth().animateContentSize()
                    ) {
                        Column(Modifier.padding(metrics.dp(18))) {
                            ProfileStepHeader("01", "Your identity", "Create a new profile or recover one securely.")
                            Spacer(Modifier.height(20.dp))
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
                                usernameAvailable == false && checkingIdentity -> FieldMessage("Checking profile details…", true)
                                usernameAvailable == false && identityMatches == true -> FieldMessage("Existing profile matched", true)
                                usernameAvailable == false && identityMatches == false -> FieldMessage("That username is taken, but the full name does not match.", false)
                                usernameAvailable == false -> FieldMessage("That username is already taken", false)
                                usernameError != null -> FieldMessage(usernameError.orEmpty(), false)
                            }
                            if (existingProfile) FieldMessage("Profile verified. Use the button below to sign in.", true)
                            Spacer(Modifier.height(28.dp))
                            ProfileStepHeader("02", "Favorite teams", "Build a feed around the clubs and nations you follow.")
                            if (catalogLoading) {
                                FieldMessage("Loading teams and players...", true)
                            }
                            if (catalogError != null) {
                                FieldMessage(catalogError.orEmpty(), false)
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    enabled = !catalogLoading,
                                    onClick = { catalogReloadKey += 1 },
                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Field, contentColor = Color.White),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text("RETRY LOADING FAVORITES", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(17.dp))
                            SearchInput("Search teams...", teamQuery) { teamQuery = it }
                            Spacer(Modifier.height(12.dp))
                            CompetitionFilterRow(teamCompetitionId) { teamCompetitionId = it }
                            if (teamFilterLoading) FieldMessage("Loading teams…", true)
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
                            if (nextTeamCursor != null) {
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    enabled = !loadingMoreTeams,
                                    onClick = {
                                        val cursor = nextTeamCursor ?: return@Button
                                        scope.launch {
                                            loadingMoreTeams = true
                                            runCatching {
                                                if (teamQuery.trim().length >= 2) {
                                                    GoalioBackendApi.searchTeams(teamQuery, limit = 6, cursor = cursor)
                                                } else {
                                                    GoalioBackendApi.getTeams(limit = 6, cursor = cursor, competitionId = teamCompetitionId)
                                                }
                                            }.onSuccess { page ->
                                                teamCatalog = (teamCatalog + page.items).distinctBy { it.id }
                                                nextTeamCursor = page.nextCursor
                                            }.onFailure {
                                                catalogError = it.userFacingBackendMessage("Could not load more teams.")
                                            }
                                            loadingMoreTeams = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Field, contentColor = Color.White),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(if (loadingMoreTeams) "LOADING..." else "LOAD 6 MORE TEAMS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.height(28.dp))
                            ProfileStepHeader("03", "Favorite players", "Pin the players you never want to miss.")
                            Spacer(Modifier.height(12.dp))
                            SearchInput("Search players...", playerQuery) { playerQuery = it }
                            Spacer(Modifier.height(12.dp))
                            CompetitionFilterRow(playerCompetitionId) { playerCompetitionId = it }
                            if (playerFilterLoading) FieldMessage("Loading players…", true)
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
                            if (nextPlayerCursor != null) {
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    enabled = !loadingMorePlayers,
                                    onClick = {
                                        val cursor = nextPlayerCursor ?: return@Button
                                        scope.launch {
                                            loadingMorePlayers = true
                                            runCatching {
                                                if (playerQuery.trim().length >= 2) {
                                                    GoalioBackendApi.searchPlayers(playerQuery, limit = 6, cursor = cursor)
                                                } else {
                                                    GoalioBackendApi.getPlayers(limit = 6, cursor = cursor, competitionId = playerCompetitionId)
                                                }
                                            }.onSuccess { page ->
                                                playerCatalog = (playerCatalog + page.items.map { it.withCompetitionIds(teamCatalog) })
                                                    .distinctBy { it.id }
                                                nextPlayerCursor = page.nextCursor
                                            }.onFailure {
                                                catalogError = it.userFacingBackendMessage("Could not load more players.")
                                            }
                                            loadingMorePlayers = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(46.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Field, contentColor = Color.White),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(if (loadingMorePlayers) "LOADING..." else "LOAD 6 MORE PLAYERS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                item {
                    Surface(color = GoalioColors.Neutral, shape = RoundedCornerShape(metrics.dp(18)), border = BorderStroke(1.dp, GoalioColors.Border), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(metrics.dp(16))) {
                    if (submitError != null) {
                        Text(
                            submitError.orEmpty(),
                            color = GoalioColors.Error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                    }
                    Button(
                        enabled = valid && !submitting,
                        onClick = {
                            scope.launch {
                                submitting = true
                                submitError = if (existingProfile) {
                                    onSignIn(fullName.trim(), username.trim())
                                } else {
                                    onComplete(ProfileDraft(fullName.trim(), username.trim().lowercase(), selectedTeams, selectedPlayers))
                                }
                                submitting = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(metrics.dp(58)),
                        interactionSource = actionInteraction,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = actionColor,
                            contentColor = GoalioColors.Secondary,
                            disabledContainerColor = Color(0xFF140A02),
                            disabledContentColor = GoalioColors.Tertiary.copy(alpha = .48f)
                        ),
                        border = BorderStroke(2.dp, GoalioColors.Tertiary),
                        shape = RoundedCornerShape(50)
                    ) {
                        Crossfade(targetState = Triple(submitting, existingProfile, checkingUsername || checkingIdentity), label = "profile action") { state ->
                            Text(when { state.first -> if (state.second) "SIGNING IN..." else "SAVING..."; state.third -> "CHECKING..."; state.second -> "SIGN IN"; else -> "CONTINUE" }, fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                        }
                    }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStepHeader(number: String, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = Color(0xFF241000), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, GoalioColors.Tertiary), modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Text(number, color = GoalioColors.Tertiary, fontSize = 12.sp, fontWeight = FontWeight.Black) }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = GoalioColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(subtitle, color = GoalioColors.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun ProfileHeader(onBack: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Row(
        Modifier.fillMaxWidth().height(metrics.dp(66)).padding(horizontal = metrics.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(metrics.dp(38)).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(metrics.dp(25)))
        }
        Spacer(Modifier.weight(1f))
        Text("Goalio", color = Color.White, fontSize = metrics.sp(23), fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.size(metrics.dp(38)))
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
        Text(label, color = GoalioColors.Caption, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
        Spacer(Modifier.height(7.dp))
        Row(
            Modifier.fillMaxWidth().height(60.dp).background(Field, RoundedCornerShape(13.dp))
                .border(1.dp, if (showValid) Gold else GoalioColors.Border, RoundedCornerShape(13.dp)).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(hint, color = GoalioColors.Placeholder, fontSize = 16.sp)
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
                Box(Modifier.size(24.dp).background(GoalioColors.Success, CircleShape), contentAlignment = Alignment.Center) {
                    CheckGlyph(Modifier.size(13.dp), GoalioColors.Background)
                }
            }
        }
    }
}

@Composable
private fun FieldMessage(text: String, success: Boolean) {
    Text(
        text,
        color = if (success) GoalioColors.Success else GoalioColors.Error,
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

private fun ProfileCatalog.catalogErrorMessage(): String? =
    listOfNotNull(teamError, playerError).takeIf { it.isNotEmpty() }?.joinToString(" ")

@Composable
private fun SearchInput(hint: String, value: String, onValueChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).background(GoalioColors.Search, RoundedCornerShape(50))
            .border(1.dp, GoalioColors.Border, RoundedCornerShape(50)).padding(horizontal = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchGlyph(Modifier.size(21.dp), GoalioColors.Icon)
        Spacer(Modifier.width(13.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(hint, color = GoalioColors.Placeholder, fontSize = 15.sp)
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
                color = if (selected) GoalioColors.Accent else GoalioColors.Surface2,
                contentColor = GoalioColors.TextPrimary,
                border = BorderStroke(1.dp, if (selected) GoalioColors.Accent else GoalioColors.Border),
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
    Surface(color = Color(0xFF241000), border = BorderStroke(1.dp, GoalioColors.Tertiary), shape = RoundedCornerShape(50), onClick = onRemove) {
        Text("$label ×", color = GoalioColors.Secondary, fontSize = 13.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp))
    }
}

@Composable
private fun TeamCard(team: FavoriteTeam, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    var imageFailed by remember(team.imageUrl) { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        color = GoalioColors.Neutral,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) GoalioColors.Accent else GoalioColors.CardBorder),
        modifier = modifier.height(metrics.dp(108))
    ) {
        Box(Modifier.padding(metrics.dp(10))) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(metrics.dp(52)).clip(CircleShape)
                        .background(team.primaryColor.copy(alpha = .16f))
                        .border(1.dp, team.primaryColor.copy(alpha = .38f), CircleShape)
                        .padding(metrics.dp(5)),
                    contentAlignment = Alignment.Center
                ) {
                    if (team.imageUrl != null && !imageFailed) {
                        AsyncImage(
                            model = team.imageUrl,
                            contentDescription = "${team.name} badge",
                            contentScale = ContentScale.Fit,
                            onError = { imageFailed = true },
                            modifier = Modifier.fillMaxSize().padding(metrics.dp(2))
                        )
                    } else {
                        Text(team.shortName.take(3).uppercase(), color = Color.White, fontSize = metrics.sp(12), fontWeight = FontWeight.Black)
                    }
                }
                Spacer(Modifier.height(metrics.dp(8)))
                Text(team.name, color = Color.White, fontSize = metrics.sp(13), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (selected) {
                Box(Modifier.align(Alignment.TopEnd).size(20.dp).background(GoalioColors.Accent, CircleShape), contentAlignment = Alignment.Center) {
                    CheckGlyph(Modifier.size(11.dp), GoalioColors.TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun PlayerCard(player: FavoritePlayer, selected: Boolean, onClick: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    var imageFailed by remember(player.imageUrl) { mutableStateOf(false) }
    Surface(
        color = GoalioColors.Neutral,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) GoalioColors.Accent else GoalioColors.Border),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.width(metrics.dp(196))
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(metrics.dp(144))
                    .background(GoalioColors.Surface3)
            ) {
                PlayerSilhouette(Modifier.align(Alignment.BottomCenter).size(metrics.dp(120)), GoalioColors.Gray200)
                if (player.imageUrl != null && !imageFailed) {
                    AsyncImage(
                        model = player.imageUrl,
                        contentDescription = player.name,
                        contentScale = ContentScale.Crop,
                        onError = { imageFailed = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Surface(
                    color = GoalioColors.Surface1.copy(alpha = .86f), shape = CircleShape,
                    border = BorderStroke(1.dp, GoalioColors.Border),
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).size(38.dp)
                ) { Box(contentAlignment = Alignment.Center) { Text(player.initials, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) } }
            }
            Column(Modifier.padding(metrics.dp(12))) {
                Text(player.name, color = Color.White, fontSize = metrics.sp(16), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(player.team, color = Muted, fontSize = metrics.sp(12), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(metrics.dp(9)))
                Surface(
                    onClick = onClick,
                    color = if (selected) Color(0xFF241000) else Color.Transparent,
                    contentColor = GoalioColors.TextPrimary,
                    border = BorderStroke(1.dp, if (selected) GoalioColors.Accent else GoalioColors.Border),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(39.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (selected) "PINNED" else "+ PIN TO DASHBOARD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    val cx = size.width * .5f
    drawCircle(accent.copy(alpha = .9f), size.minDimension * .045f, Offset(size.width * .33f, size.height * .29f))
    drawCircle(accent.copy(alpha = .9f), size.minDimension * .045f, Offset(size.width * .67f, size.height * .29f))
    drawCircle(accent, size.minDimension * .17f, Offset(cx, size.height * .28f))
    drawRect(accent, Offset(size.width * .43f, size.height * .39f), androidx.compose.ui.geometry.Size(size.width * .14f, size.height * .16f))
    val body = Path().apply {
        moveTo(size.width * .08f, size.height)
        cubicTo(size.width * .09f, size.height * .69f, size.width * .27f, size.height * .61f, size.width * .4f, size.height * .55f)
        cubicTo(size.width * .44f, size.height * .63f, size.width * .56f, size.height * .63f, size.width * .6f, size.height * .55f)
        cubicTo(size.width * .73f, size.height * .61f, size.width * .91f, size.height * .69f, size.width * .92f, size.height)
        close()
    }
    drawPath(body, accent)
}
