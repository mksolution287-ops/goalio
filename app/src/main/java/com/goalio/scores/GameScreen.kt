package com.goalio.scores

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalio.scores.ui.theme.GoalioColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GameScreen(
    onBack: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenMatches: () -> Unit,
    onOpenWorldCup: () -> Unit,
    onOpenSettings: () -> Unit,
    currentUsername: String?,
    onSaveUsername: suspend (String) -> String?
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val metrics = rememberGoalioMetrics()
    val scope = rememberCoroutineScope()

    var session by remember { mutableStateOf<QuizSessionInfo?>(null) }
    var index by remember { mutableStateOf(0) }
    var remaining by remember { mutableStateOf(15) }
    var answer by remember { mutableStateOf<QuizAnswerInfo?>(null) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var leaderboard by remember { mutableStateOf(QuizRepository.cachedLeaderboard(context)) }
    var username by rememberSaveable(currentUsername) { mutableStateOf(currentUsername.orEmpty()) }
    var usernameAvailable by remember { mutableStateOf<Boolean?>(null) }
    var checkingUsername by remember { mutableStateOf(false) }
    var savingUsername by remember { mutableStateOf(false) }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var usernameSaved by remember(currentUsername) { mutableStateOf(!currentUsername.isNullOrBlank()) }
    val hasUsername = usernameSaved
    val usernameValid = isValidGameUsername(username)

    suspend fun start() {
        loading = true; error = null
        runCatching { QuizRepository.start() }
            .onSuccess { session = it; index = 0; remaining = 15; answer = null; selectedIndex = null }
            .onFailure { error = it.message }
        loading = false
    }

    suspend fun submit(picked: Int) {
        val current = session?.questions?.getOrNull(index) ?: return
        if (submitting || answer != null) return
        submitting = true
        runCatching { QuizRepository.answer(session!!.sessionId, current.id, picked) }
            .onSuccess {
                answer = it
                selectedIndex = picked
                leaderboard = runCatching { QuizRepository.leaderboard(context) }.getOrDefault(leaderboard)
            }
            .onFailure { error = it.message }
        submitting = false
    }

    LaunchedEffect(Unit) {
        leaderboard = runCatching { QuizRepository.leaderboard(context) }.getOrDefault(leaderboard)
    }

    LaunchedEffect(username) {
        usernameError = null
        usernameAvailable = null
        checkingUsername = false
        if (username.isBlank() || !usernameValid) return@LaunchedEffect
        delay(350)
        checkingUsername = true
        runCatching { GoalioBackendApi.isUsernameAvailable(username) }
            .onSuccess { usernameAvailable = it }
            .onFailure { usernameError = it.userFacingBackendMessage("Could not check username.") }
        checkingUsername = false
    }

    LaunchedEffect(session?.sessionId, index, answer) {
        if (session == null || answer != null) return@LaunchedEffect
        remaining = session!!.questions[index].timeLimitSeconds
        while (remaining > 0 && answer == null) { delay(1_000); remaining-- }
        if (remaining == 0 && answer == null) submit(-1)
    }

    GoalioBackground {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = metrics.horizontalPadding,
                end = metrics.horizontalPadding,
                top = metrics.dp(18),
                bottom = metrics.dp(120)
            ),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(20))
        ) {
            item { GoalioTopBar("GAMES", onBack = onBack, onSettings = onOpenSettings) }

            item { GameHeader(leaderboard, metrics) }

            if (!hasUsername) {
                item {
                    UsernameGateCard(
                        username = username,
                        onUsernameChange = { value ->
                            username = value.lowercase().filter { it in 'a'..'z' || it.isDigit() || it == '_' }.take(20)
                        },
                        usernameValid = usernameValid,
                        usernameAvailable = usernameAvailable,
                        checking = checkingUsername,
                        saving = savingUsername,
                        error = usernameError,
                        metrics = metrics,
                        onSave = {
                            scope.launch {
                                savingUsername = true
                                usernameError = onSaveUsername(username.trim().lowercase())
                                if (usernameError == null) {
                                    username = username.trim().lowercase()
                                    usernameSaved = true
                                }
                                savingUsername = false
                            }
                        }
                    )
                }
                item { LeaderboardSection(leaderboard, metrics) }
            } else if (session == null) {
                if (loading) {
                    item { GameLoadingSkeleton(metrics) }
                } else {
                    item { HeroCard(loading, error, metrics) { scope.launch { start() } } }
                }
                item { StatsStrip(metrics) }
                item { LeaderboardSection(leaderboard, metrics) }
            } else {
                val question = session!!.questions[index]
                item {
                    AnimatedContent(
                        targetState = index,
                        transitionSpec = {
                            (fadeIn() + slideInVertically { it / 2 }).togetherWith(fadeOut())
                        },
                        label = "q_anim"
                    ) { i ->
                        QuestionCard(
                            question = question,
                            index = i,
                            total = session!!.questions.size,
                            remaining = remaining,
                            answer = answer,
                            selectedIndex = selectedIndex,
                            submitting = submitting,
                            onSubmit = { scope.launch { submit(it) } },
                            metrics = metrics
                        )
                    }
                }

                answer?.let { result ->
                    item {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically { it / 2 }
                        ) {
                            ResultBanner(result, metrics)
                        }
                    }
                    item {
                        GradientButton(
                            label = if (result.completed) "SEE RESULTS" else "NEXT QUESTION",
                            metrics = metrics
                        ) {
                            if (result.completed) session = null
                            else { index++; answer = null; selectedIndex = null; remaining = 15 }
                        }
                    }
                }
            }
        }

        GoalioBottomBar(
            Modifier.align(Alignment.BottomCenter),
            "Games",
            onOpenHome, onOpenMatches, onOpenWorldCup, {}
        )
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
private fun GameHeader(leaderboard: QuizLeaderboardInfo?, metrics: GoalioMetrics) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                trans("THE FOOTBALL FIVE"),
                color = Color.White,
                fontSize = metrics.sp(22),
                fontWeight = FontWeight.Black
            )
            Text(
                trans("Test your football knowledge"),
                color = GoalioColors.TextSecondary,
                fontSize = metrics.sp(13)
            )
        }
        Surface(
            color = Color(0xFF1A0D00),
            shape = RoundedCornerShape(metrics.dp(14)),
            border = BorderStroke(1.dp, GoalioColors.Accent)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = metrics.dp(14), vertical = metrics.dp(10)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(metrics.dp(6))
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = GoalioColors.Accent,
                    modifier = Modifier.size(metrics.dp(18))
                )
                Text(
                    "${leaderboard?.me?.xp ?: 0} XP",
                    color = GoalioColors.Accent,
                    fontSize = metrics.sp(15),
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

// ─── Hero / Welcome Card ───────────────────────────────────────────────────────

@Composable
private fun HeroCard(loading: Boolean, error: String?, metrics: GoalioMetrics, onStart: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.50f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    Surface(
        color = Color(0xFF0D0D0D),
        shape = RoundedCornerShape(metrics.dp(24)),
        border = BorderStroke(1.dp, Color(0xFF2E2010))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFFF8500).copy(alpha = glowAlpha), Color.Transparent)
                        ),
                        radius = size.width * 0.65f,
                        center = Offset(size.width * 0.5f, size.height * 0.3f)
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(metrics.dp(28)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(metrics.dp(18))
            ) {
                // Icon badge
                Box(
                    modifier = Modifier
                        .size(metrics.dp(76))
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFFFF8500), Color(0xFF7A3000))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.SportsScore,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(metrics.dp(38))
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(metrics.dp(8))) {
                    Text(
                        trans("THE FOOTBALL FIVE"),
                        color = Color.White,
                        fontSize = metrics.sp(24),
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        trans("5 questions  ·  15 seconds each\n+10 XP correct   ·   −5 XP wrong"),
                        color = GoalioColors.TextSecondary,
                        fontSize = metrics.sp(14),
                        textAlign = TextAlign.Center,
                        lineHeight = metrics.sp(22)
                    )
                }

                error?.let {
                    Text(it, color = GoalioColors.Error, fontSize = metrics.sp(13), textAlign = TextAlign.Center)
                }

                GradientButton(
                    label = if (loading) trans("LOADING…") else trans("PLAY NOW"),
                    enabled = !loading,
                    metrics = metrics,
                    onClick = onStart
                )
            }
        }
    }
}

// ─── Stats Strip ──────────────────────────────────────────────────────────────

@Composable
private fun UsernameGateCard(
    username: String,
    onUsernameChange: (String) -> Unit,
    usernameValid: Boolean,
    usernameAvailable: Boolean?,
    checking: Boolean,
    saving: Boolean,
    error: String?,
    metrics: GoalioMetrics,
    onSave: () -> Unit
) {
    val message = when {
        username.isBlank() -> "Write username to play"
        !usernameValid -> "3-20 characters; start with a letter. Use letters, numbers, or single underscores."
        checking -> "Checking username..."
        usernameAvailable == true -> "Username is available"
        usernameAvailable == false -> "That username is already taken"
        error != null -> error
        else -> null
    }
    val success = usernameAvailable == true && error == null

    Surface(
        color = Color(0xFF0D0D0D),
        shape = RoundedCornerShape(metrics.dp(24)),
        border = BorderStroke(1.dp, Color(0xFF2E2010))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(metrics.dp(22)),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(14))
        ) {
            Text(
                trans("Write Username"),
                color = Color.White,
                fontSize = metrics.sp(22),
                fontWeight = FontWeight.Black
            )
            Surface(
                color = GoalioColors.Surface2,
                shape = RoundedCornerShape(metrics.dp(14)),
                border = BorderStroke(1.dp, if (success) GoalioColors.Accent else GoalioColors.Border),
                modifier = Modifier.fillMaxWidth().height(metrics.dp(58))
            ) {
                Box(Modifier.padding(horizontal = metrics.dp(16)), contentAlignment = Alignment.CenterStart) {
                    if (username.isEmpty()) {
                        Text("goalgetter99", color = GoalioColors.Placeholder, fontSize = metrics.sp(16))
                    }
                    BasicTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = metrics.sp(16), fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            message?.let {
                Text(
                    trans(it),
                    color = if (success) GoalioColors.Success else GoalioColors.TextSecondary,
                    fontSize = metrics.sp(12)
                )
            }
            GradientButton(
                label = if (saving) "SAVING..." else "SAVE USERNAME",
                enabled = usernameValid && usernameAvailable == true && !checking && !saving,
                metrics = metrics,
                onClick = onSave
            )
        }
    }
}

@Composable
private fun GameLoadingSkeleton(metrics: GoalioMetrics) {
    val transition = rememberInfiniteTransition(label = "gameSkeleton")
    val alpha by transition.animateFloat(
        initialValue = .26f,
        targetValue = .68f,
        animationSpec = infiniteRepeatable(tween(850), RepeatMode.Reverse),
        label = "gameSkeletonAlpha"
    )
    val shimmer = Color(0xFF6A6A6A).copy(alpha = alpha)
    Surface(
        color = Color(0xFF0D0D0D),
        shape = RoundedCornerShape(metrics.dp(24)),
        border = BorderStroke(1.dp, Color(0xFF2E2010))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(metrics.dp(28)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(metrics.dp(18))
        ) {
            Box(Modifier.size(metrics.dp(76)).clip(CircleShape).background(shimmer))
            Box(Modifier.width(metrics.dp(210)).height(metrics.dp(24)).clip(CircleShape).background(shimmer))
            Box(Modifier.width(metrics.dp(260)).height(metrics.dp(13)).clip(CircleShape).background(shimmer.copy(alpha = alpha * .75f)))
            Box(Modifier.width(metrics.dp(220)).height(metrics.dp(13)).clip(CircleShape).background(shimmer.copy(alpha = alpha * .58f)))
            Box(Modifier.fillMaxWidth().height(metrics.dp(54)).clip(RoundedCornerShape(metrics.dp(16))).background(shimmer.copy(alpha = alpha * .85f)))
        }
    }
}

@Composable
private fun StatsStrip(metrics: GoalioMetrics) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))
    ) {
        StatTile(
            icon = Icons.Filled.Schedule,
            value = "15s",
            label = "Per Question",
            metrics = metrics,
            modifier = Modifier.weight(1f)
        )
        StatTile(
            icon = Icons.Filled.Star,
            value = "+10",
            label = "XP Correct",
            metrics = metrics,
            modifier = Modifier.weight(1f)
        )
        StatTile(
            icon = Icons.Filled.EmojiEvents,
            value = "Top 10",
            label = "Leaderboard",
            metrics = metrics,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    metrics: GoalioMetrics,
    modifier: Modifier
) {
    Surface(
        color = GoalioColors.Surface1,
        shape = RoundedCornerShape(metrics.dp(16)),
        border = BorderStroke(1.dp, GoalioColors.CardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = metrics.dp(18)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(metrics.dp(8))
        ) {
            Box(
                modifier = Modifier
                    .size(metrics.dp(40))
                    .clip(CircleShape)
                    .background(GoalioColors.Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = GoalioColors.Accent,
                    modifier = Modifier.size(metrics.dp(20))
                )
            }
            Text(value, color = GoalioColors.Accent, fontSize = metrics.sp(18), fontWeight = FontWeight.Black)
            Text(label, color = GoalioColors.TextSecondary, fontSize = metrics.sp(11), fontWeight = FontWeight.Bold)
        }
    }
}

// ─── Question Card ────────────────────────────────────────────────────────────

@Composable
private fun QuestionCard(
    question: QuizQuestionInfo,
    index: Int,
    total: Int,
    remaining: Int,
    answer: QuizAnswerInfo?,
    selectedIndex: Int?,
    submitting: Boolean,
    onSubmit: (Int) -> Unit,
    metrics: GoalioMetrics
) {
    val isUrgent = remaining <= 5
    val timerColor = if (isUrgent) GoalioColors.Error else GoalioColors.Accent

    Surface(
        color = Color(0xFF0E0E0E),
        shape = RoundedCornerShape(metrics.dp(24)),
        border = BorderStroke(1.dp, Color(0xFF252525))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(metrics.dp(22)),
            verticalArrangement = Arrangement.spacedBy(metrics.dp(18))
        ) {
            // Category + Timer
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.dp(4))) {
                    Surface(
                        color = GoalioColors.Accent.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(metrics.dp(6)),
                        border = BorderStroke(1.dp, GoalioColors.Accent.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = metrics.dp(10), vertical = metrics.dp(4)),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(metrics.dp(4))
                        ) {
                            Icon(
                                Icons.Filled.QueryStats,
                                contentDescription = null,
                                tint = GoalioColors.Accent,
                                modifier = Modifier.size(metrics.dp(12))
                            )
                            Text(
                                question.category.uppercase(),
                                color = GoalioColors.Accent,
                                fontSize = metrics.sp(10),
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    Text(
                        "Q${index + 1} of $total",
                        color = GoalioColors.TextSecondary,
                        fontSize = metrics.sp(12),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Timer circle
                Box(contentAlignment = Alignment.Center) {
                    Surface(
                        shape = CircleShape,
                        color = timerColor.copy(alpha = 0.1f),
                        border = BorderStroke(2.5.dp, timerColor),
                        modifier = Modifier.size(metrics.dp(58))
                    ) {}
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Timer,
                            contentDescription = null,
                            tint = timerColor,
                            modifier = Modifier.size(metrics.dp(14))
                        )
                        Text(
                            "${remaining}",
                            color = timerColor,
                            fontSize = metrics.sp(18),
                            fontWeight = FontWeight.Black,
                            lineHeight = metrics.sp(20)
                        )
                    }
                }
            }

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.dp(5))
                    .clip(CircleShape)
                    .background(GoalioColors.Surface2)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((index + 1).toFloat() / total)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFF8500), Color(0xFFFFBC00))
                            )
                        )
                )
            }

            // Question text
            Text(
                question.prompt,
                color = Color.White,
                fontSize = metrics.sp(20),
                fontWeight = FontWeight.Black,
                lineHeight = metrics.sp(28)
            )

            // Options
            question.options.indices.forEach { i ->
                val isCorrect = answer != null && i == answer.correctAnswerIndex
                val isWrongPick = answer != null && i == selectedIndex && !isCorrect
                val isNeutral = answer != null && !isCorrect && !isWrongPick

                val bgColor = when {
                    isCorrect -> Color(0xFF0C2B1D)
                    isWrongPick -> Color(0xFF2E0D0D)
                    else -> Color(0xFF141414)
                }
                val borderColor = when {
                    isCorrect -> Color(0xFF38D985)
                    isWrongPick -> Color(0xFFFF5252)
                    else -> Color(0xFF2A2A2A)
                }
                val labelBg = when {
                    isCorrect -> Color(0xFF38D985).copy(alpha = 0.18f)
                    isWrongPick -> Color(0xFFFF5252).copy(alpha = 0.18f)
                    else -> GoalioColors.Accent.copy(alpha = 0.12f)
                }
                val labelColor = when {
                    isCorrect -> Color(0xFF38D985)
                    isWrongPick -> Color(0xFFFF5252)
                    else -> GoalioColors.Accent
                }

                Surface(
                    color = bgColor,
                    shape = RoundedCornerShape(metrics.dp(14)),
                    border = BorderStroke(1.5.dp, borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = answer == null && !submitting) { onSubmit(i) }
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = metrics.dp(16),
                            vertical = metrics.dp(14)
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))
                    ) {
                        // Letter badge
                        Box(
                            modifier = Modifier
                                .size(metrics.dp(30))
                                .clip(CircleShape)
                                .background(labelBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${('A'.code + i).toChar()}",
                                color = labelColor,
                                fontSize = metrics.sp(13),
                                fontWeight = FontWeight.Black
                            )
                        }

                        Text(
                            question.options[i],
                            color = if (isNeutral) GoalioColors.TextSecondary else Color.White,
                            fontSize = metrics.sp(14),
                            fontWeight = if (isCorrect || isWrongPick) FontWeight.Black else FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )

                        if (isCorrect) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Color(0xFF38D985), modifier = Modifier.size(metrics.dp(20)))
                        } else if (isWrongPick) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = Color(0xFFFF5252), modifier = Modifier.size(metrics.dp(20)))
                        }
                    }
                }
            }
        }
    }
}

// ─── Result Banner ────────────────────────────────────────────────────────────

@Composable
private fun ResultBanner(result: QuizAnswerInfo, metrics: GoalioMetrics) {
    val isCorrect = result.correct
    val accentColor = if (isCorrect) Color(0xFF38D985) else Color(0xFFFF6B6B)
    val bgBrush = if (isCorrect)
        Brush.horizontalGradient(listOf(Color(0xFF0A271B), Color(0xFF0F3326)))
    else
        Brush.horizontalGradient(listOf(Color(0xFF2A0A0A), Color(0xFF3A1010)))

    val icon = when {
        isCorrect -> Icons.Filled.Check
        result.timedOut -> Icons.Filled.Timer
        else -> Icons.Filled.Close
    }
    val headline = when {
        isCorrect -> "+${result.xpDelta} XP  ·  CORRECT!"
        result.timedOut -> "TIME'S UP  ·  ${result.xpDelta} XP"
        else -> "${result.xpDelta} XP  ·  WRONG ANSWER"
    }

    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(metrics.dp(18)),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgBrush)
                .padding(metrics.dp(20))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(8))) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(metrics.dp(10))
                ) {
                    Box(
                        modifier = Modifier
                            .size(metrics.dp(34))
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(metrics.dp(18)))
                    }
                    Text(
                        headline,
                        color = accentColor,
                        fontSize = metrics.sp(15),
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    result.explanation,
                    color = GoalioColors.TextSecondary,
                    fontSize = metrics.sp(14),
                    lineHeight = metrics.sp(21)
                )
            }
        }
    }
}

// ─── Leaderboard ─────────────────────────────────────────────────────────────

@Composable
private fun LeaderboardSection(leaderboard: QuizLeaderboardInfo?, metrics: GoalioMetrics) {
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = GoalioColors.Accent,
                modifier = Modifier.size(metrics.dp(20))
            )
            Spacer(Modifier.width(metrics.dp(8)))
            Text(
                trans("LEADERBOARD"),
                color = Color.White,
                fontSize = metrics.sp(18),
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f)
            )
            Text(
                trans("TOP 10"),
                color = GoalioColors.Accent,
                fontSize = metrics.sp(11),
                fontWeight = FontWeight.Black
            )
        }

        val entries = leaderboard?.entries.orEmpty().take(10)
        if (entries.isEmpty()) {
            Surface(
                color = GoalioColors.Surface1,
                shape = RoundedCornerShape(metrics.dp(16)),
                border = BorderStroke(1.dp, GoalioColors.CardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    trans("No leaderboard data yet. Play to get ranked!"),
                    color = GoalioColors.TextSecondary,
                    fontSize = metrics.sp(14),
                    modifier = Modifier.padding(metrics.dp(20)),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            entries.forEach { player ->
                LeaderboardRow(player, metrics)
            }
        }
    }
}

@Composable
private fun LeaderboardRow(player: QuizLeaderInfo, metrics: GoalioMetrics) {
    val isMe = player.isMe
    val isTop3 = player.rank <= 3
    val rankColor = when (player.rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFB0BEC5)
        3 -> Color(0xFFCD7F32)
        else -> GoalioColors.TextSecondary
    }
    val avatarBrush = if (isMe)
        Brush.radialGradient(listOf(GoalioColors.Accent, Color(0xFF7A3000)))
    else
        Brush.radialGradient(listOf(Color(0xFF2E2E2E), Color(0xFF1A1A1A)))

    Surface(
        color = if (isMe) Color(0xFF1A0C00) else GoalioColors.Surface1,
        shape = RoundedCornerShape(metrics.dp(14)),
        border = BorderStroke(
            if (isMe) 1.5.dp else 1.dp,
            if (isMe) GoalioColors.Accent else GoalioColors.CardBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = metrics.dp(16), vertical = metrics.dp(14)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank
            Box(modifier = Modifier.width(metrics.dp(42)), contentAlignment = Alignment.Center) {
                if (isTop3) {
                    Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = rankColor,
                        modifier = Modifier.size(metrics.dp(22))
                    )
                } else {
                    Text(
                        "#${player.rank}",
                        color = rankColor,
                        fontSize = metrics.sp(13),
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Avatar circle with initial
            Box(
                modifier = Modifier
                    .size(metrics.dp(38))
                    .clip(CircleShape)
                    .background(avatarBrush),
                contentAlignment = Alignment.Center
            ) {
                if (isMe) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(metrics.dp(20))
                    )
                } else {
                    Text(
                        player.username.take(1).uppercase(),
                        color = Color.White,
                        fontSize = metrics.sp(15),
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(Modifier.width(metrics.dp(12)))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "@${player.username}",
                    color = if (isMe) GoalioColors.Accent else Color.White,
                    fontSize = metrics.sp(14),
                    fontWeight = if (isMe) FontWeight.Black else FontWeight.Bold
                )
                if (isMe) {
                    Text(
                        trans("YOU"),
                        color = GoalioColors.Accent.copy(alpha = 0.7f),
                        fontSize = metrics.sp(10),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                }
            }

            // XP pill
            Surface(
                color = if (isMe) GoalioColors.Accent.copy(alpha = 0.15f) else Color(0xFF1C1C1C),
                shape = RoundedCornerShape(metrics.dp(8)),
                border = BorderStroke(1.dp, if (isMe) GoalioColors.Accent.copy(alpha = 0.5f) else Color(0xFF282828))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = metrics.dp(10), vertical = metrics.dp(5)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(metrics.dp(4))
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = if (isMe) GoalioColors.Accent else GoalioColors.TextSecondary,
                        modifier = Modifier.size(metrics.dp(12))
                    )
                    Text(
                        "${player.xp}",
                        color = if (isMe) GoalioColors.Accent else GoalioColors.TextSecondary,
                        fontSize = metrics.sp(12),
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

// ─── Shared: Gradient pill button ─────────────────────────────────────────────

@Composable
private fun GradientButton(
    label: String,
    enabled: Boolean = true,
    metrics: GoalioMetrics,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.dp(56))
            .clip(CircleShape)
            .background(
                if (enabled)
                    Brush.horizontalGradient(listOf(Color(0xFFFF8500), Color(0xFFBF4A00)))
                else
                    Brush.linearGradient(listOf(Color(0xFF2A2A2A), Color(0xFF1E1E1E)))
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            trans(label),
            color = Color.White,
            fontSize = metrics.sp(14),
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp
        )
    }
}

private fun isValidGameUsername(value: String): Boolean {
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
