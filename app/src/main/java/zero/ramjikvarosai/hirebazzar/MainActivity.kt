package zero.ramjikvarosai.hirebazzar

import android.Manifest
import android.os.Bundle
import android.os.Build
import android.os.LocaleList
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.unit.dp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import zero.ramjikvarosai.hirebazzar.ui.theme.GoalioTheme
import zero.ramjikvarosai.hirebazzar.ui.theme.GoalioColors
import com.onesignal.OneSignal
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity() {
    private companion object {
        const val PREF_LANGUAGE_RETURN_SCREEN = "language_return_screen"
        const val PREF_SKIP_SPLASH_ONCE = "skip_splash_once"
    }

    override fun onResume() {
        super.onResume()
        GoalioAppVisibility.markForeground()
    }

    override fun onPause() {
        GoalioAppVisibility.markBackground()
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            splashScreen.setOnExitAnimationListener { splashScreenView ->
                splashScreenView.animate()
                    .alpha(0f)
                    .setDuration(220L)
                    .withEndAction {
                        (splashScreenView.parent as? ViewGroup)?.removeView(splashScreenView)
                    }
                    .start()
            }
        }
        setContent {
            GoalioTheme(dynamicColor = false) {
                val settings = remember { getSharedPreferences("goalio_settings", MODE_PRIVATE) }
                val pendingLanguageReturn = remember { settings.getString(PREF_LANGUAGE_RETURN_SCREEN, null) }
                val hasSavedLanguage = remember { settings.contains("language") }
                val skipSplashOnce = remember { settings.getBoolean(PREF_SKIP_SPLASH_ONCE, false) }
                var currentLanguage by remember { mutableStateOf(settings.getString("language", "system") ?: "system") }
                var showSplash by remember { mutableStateOf(hasSavedLanguage && !skipSplashOnce) }
                var languageSelected by remember { mutableStateOf(settings.contains("language")) }
                var onboardingComplete by remember {
                    mutableStateOf(settings.getBoolean("onboarding_complete", false))
                }
                var profileComplete by remember {
                    mutableStateOf(settings.getBoolean("profile_complete", false))
                }
                var profileUsername by remember { mutableStateOf(settings.getString("profile_username", null)) }
                var appScreen by remember { mutableStateOf(pendingLanguageReturn ?: "home") }
                var screenHistory by remember { mutableStateOf(emptyList<String>()) }
                var languageReturnScreen by remember { mutableStateOf<String?>(null) }
                var selectedMatch by remember { mutableStateOf<ScheduleMatch?>(null) }
                var selectedDetailTab by remember { mutableStateOf("Overview") }
                var competitionHubMode by remember { mutableStateOf(GoalioRemoteConfig.competitionHubMode()) }
                val navigateTo: (String) -> Unit = { target ->
                    if (target != appScreen) {
                        screenHistory = screenHistory + appScreen
                        appScreen = target
                    }
                }
                val navigateBack: () -> Unit = {
                    val previous = screenHistory.lastOrNull()
                    if (previous != null) {
                        screenHistory = screenHistory.dropLast(1)
                        appScreen = previous
                    } else if (appScreen != "home") {
                        appScreen = "home"
                    }
                }
                val openCompetitionHub: () -> Unit = {
                    navigateTo(competitionHubMode.screen ?: "matches")
                }
                val effectiveLanguage = if (currentLanguage == "system") Locale.getDefault().toLanguageTag() else currentLanguage
                val notificationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }

                BackHandler(
                    enabled = !showSplash && languageSelected && onboardingComplete && profileComplete &&
                        (appScreen != "home" || screenHistory.isNotEmpty()),
                    onBack = navigateBack
                )

                LaunchedEffect(currentLanguage) {
                    applyLanguage(currentLanguage)
                    AppLanguageState.current = effectiveLanguage
                }

                LaunchedEffect(pendingLanguageReturn) {
                    if (pendingLanguageReturn != null) {
                        settings.edit()
                            .remove(PREF_LANGUAGE_RETURN_SCREEN)
                            .apply()
                    }
                }

                LaunchedEffect(skipSplashOnce) {
                    if (skipSplashOnce) {
                        settings.edit()
                            .remove(PREF_SKIP_SPLASH_ONCE)
                            .apply()
                    }
                }

                LaunchedEffect(Unit) {
                    FirebaseRemoteConfig.getInstance().fetchAndActivate().addOnCompleteListener {
                        competitionHubMode = GoalioRemoteConfig.competitionHubMode()
                    }
                }
                LaunchedEffect(Unit) {
                    delay(2800)
                    showSplash = false
                }
                LaunchedEffect(competitionHubMode, appScreen) {
                    if (appScreen == "worldcup" && competitionHubMode.screen != "worldcup") {
                        appScreen = competitionHubMode.screen ?: "home"
                    }
                    if (appScreen == "league" && competitionHubMode.screen != "league") {
                        appScreen = competitionHubMode.screen ?: "home"
                    }
                }
                LaunchedEffect(showSplash) {
                    if (!showSplash && !settings.getBoolean("notification_prompt_requested", false)) {
                        // The language screen is composed first, so it remains behind the OS prompt.
                        delay(300)
                        settings.edit().putBoolean("notification_prompt_requested", true).apply()
                        val appId = GoalioRemoteConfig.oneSignalAppId()
                        if (appId.matches(Regex("[0-9a-fA-F-]{36}"))) {
                            OneSignal.Notifications.requestPermission(false)
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }
                LaunchedEffect(showSplash) {
                    if (!showSplash) {
                        runCatching { GoalioBackendApi.getProfile() }.onSuccess { profile ->
                            if (profile.onboardingCompleted) {
                                settings.edit().putBoolean("onboarding_complete", true).apply()
                                onboardingComplete = true
                            }
                            if (profile.profileCompleted) {
                                settings.edit()
                                    .putString("profile_full_name", profile.name)
                                    .putString("profile_username", profile.username)
                                    .putStringSet("profile_team_ids", profile.favoriteTeamIds.toSet())
                                    .putStringSet("profile_player_ids", profile.favoritePlayerIds.toSet())
                                    .putStringSet("profile_team_names", profile.favoriteTeams.toSet())
                                    .putStringSet("profile_player_names", profile.favoritePlayers.toSet())
                                    .putBoolean("profile_complete", true)
                                    .apply()
                                profileComplete = true
                                profileUsername = profile.username
                            }
                        }
                    }
                }
                CompositionLocalProvider(LocalAppLanguage provides effectiveLanguage) {
                    when {
                        showSplash -> SplashScreen()
                        !languageSelected -> LanguageScreen(
                            onBack = if (languageReturnScreen != null || onboardingComplete) {
                                {
                                    val returnScreen = languageReturnScreen
                                    if (returnScreen != null) {
                                        languageReturnScreen = null
                                        appScreen = returnScreen
                                    }
                                    languageSelected = true
                                }
                            } else null,
                            onDone = { languageTag ->
                                val returnScreen = languageReturnScreen
                                val editor = settings.edit()
                                    .putString("language", languageTag)
                                    .putBoolean(PREF_SKIP_SPLASH_ONCE, true)
                                if (returnScreen != null) {
                                    editor.putString(PREF_LANGUAGE_RETURN_SCREEN, returnScreen)
                                }
                                editor.apply()
                                currentLanguage = languageTag
                                returnScreen?.let { appScreen = it }
                                languageReturnScreen = null
                                languageSelected = true
                            },
                            initialLanguage = currentLanguage
                        )
                        !onboardingComplete -> OnboardingScreen(
                            onBack = { languageSelected = false },
                            onComplete = {
                                settings.edit().putBoolean("onboarding_complete", true).apply()
                                onboardingComplete = true
                            }
                        )
                    !profileComplete -> ProfileSetupScreen(
                        onBack = {
                            settings.edit().putBoolean("onboarding_complete", false).apply()
                            onboardingComplete = false
                        },
                        onSignIn = { _, _ -> "Could not sign in right now. Please retry later." },
                        onComplete = { profile ->
                            settings.edit()
                                .putStringSet("profile_team_ids", profile.teamIds)
                                .putStringSet("profile_player_ids", profile.playerIds)
                                .putStringSet("profile_team_names", profile.teamNames)
                                .putStringSet("profile_player_names", profile.playerNames)
                                .putBoolean("profile_complete", true)
                                .apply()
                            profileComplete = true
                            null
                        }
                    )
                    else -> when (appScreen) {
                        "matches" -> MatchScreen(
                            onBack = navigateBack,
                            onOpenHome = { navigateTo("home") },
                            onOpenWorldCup = openCompetitionHub,
                            onOpenGames = { navigateTo("games") },
                            onOpenSettings = { navigateTo("settings") },
                            onOpenMatch = { match ->
                                selectedMatch = match
                                selectedDetailTab = "Overview"
                                navigateTo("detail")
                            }
                        )
                        "detail" -> selectedMatch?.let { match ->
                            MatchDetailScreen(
                                league = match.league,
                                matchId = match.matchId,
                                initialMatch = match,
                                initialTab = selectedDetailTab,
                                onBack = navigateBack,
                                onOpenHome = { navigateTo("home") },
                                onOpenMatches = { navigateTo("matches") },
                                onOpenWorldCup = openCompetitionHub,
                                onOpenGames = { navigateTo("games") },
                                onOpenSettings = { navigateTo("settings") }
                            )
                        } ?: run {
                            appScreen = "matches"
                            MatchScreen(
                                onBack = navigateBack,
                                onOpenHome = { navigateTo("home") },
                                onOpenWorldCup = openCompetitionHub,
                                onOpenGames = { navigateTo("games") },
                                onOpenSettings = { navigateTo("settings") },
                                onOpenMatch = { match ->
                                    selectedMatch = match
                                    selectedDetailTab = "Overview"
                                    navigateTo("detail")
                                }
                            )
                        }
                        "worldcup" -> WorldCupScreen(
                            onBack = navigateBack,
                            onOpenHome = { navigateTo("home") },
                            onOpenMatches = { navigateTo("matches") },
                            onOpenGames = { navigateTo("games") },
                            onOpenSettings = { navigateTo("settings") }
                        )
                        "league" -> LeagueScreen(
                            onBack = navigateBack,
                            onOpenHome = { navigateTo("home") },
                            onOpenMatches = { navigateTo("matches") },
                            onOpenGames = { navigateTo("games") },
                            onOpenSettings = { navigateTo("settings") }
                        )
                        "games" -> GameScreen(
                            onBack = navigateBack,
                            onOpenHome = { navigateTo("home") },
                            onOpenMatches = { navigateTo("matches") },
                            onOpenWorldCup = openCompetitionHub,
                            onOpenSettings = { navigateTo("settings") },
                            currentFullName = settings.getString("profile_full_name", null),
                            currentUsername = profileUsername,
                            onSaveProfile = { name, username ->
                                try {
                                    val saved = GoalioBackendApi.saveProfile(
                                        ProfileDraft(
                                            name,
                                            username,
                                            settings.getStringSet("profile_team_ids", emptySet()).orEmpty(),
                                            settings.getStringSet("profile_player_ids", emptySet()).orEmpty(),
                                            settings.getStringSet("profile_team_names", emptySet()).orEmpty(),
                                            settings.getStringSet("profile_player_names", emptySet()).orEmpty()
                                        )
                                    )
                                    settings.edit()
                                        .putString("profile_full_name", saved.name)
                                        .putString("profile_username", saved.username)
                                        .putStringSet("profile_team_ids", saved.favoriteTeamIds.toSet())
                                        .putStringSet("profile_player_ids", saved.favoritePlayerIds.toSet())
                                        .putStringSet("profile_team_names", saved.favoriteTeams.toSet())
                                        .putStringSet("profile_player_names", saved.favoritePlayers.toSet())
                                        .putBoolean("profile_complete", true)
                                        .apply()
                                    profileUsername = saved.username
                                    null
                                } catch (error: Exception) {
                                    if (error is BackendException && error.statusCode == 503) {
                                        "Could not save username. Server is busy right now. Please retry later."
                                    } else {
                                        "Could not save username. Check your internet connection and retry later."
                                    }
                                }
                            }
                        )
                        "settings" -> SettingsScreen(
                            onBack = navigateBack, onHome = { navigateTo("home") },
                            onMatches = { navigateTo("matches") }, onWorldCup = openCompetitionHub,
                            onGames = { navigateTo("games") },
                            onEditProfile = { profileComplete = false },
                            onLanguage = {
                                languageReturnScreen = "settings"
                                languageSelected = false
                            },
                            onSignOut = { profileComplete = false; profileUsername = null }
                        )
                        else -> PersonalizedHomeScreen(
                            fallbackName = settings.getString("profile_full_name", null),
                            fallbackTeams = settings.getStringSet("profile_team_names", emptySet()).orEmpty(),
                            fallbackPlayers = settings.getStringSet("profile_player_names", emptySet()).orEmpty(),
                            onOpenMatches = { navigateTo("matches") },
                            onOpenWorldCup = openCompetitionHub,
                            onOpenGames = { navigateTo("games") },
                            onOpenSettings = { navigateTo("settings") },
                            onOpenMatch = { match, tab ->
                                selectedMatch = match
                                selectedDetailTab = tab
                                navigateTo("detail")
                            }
                        )
                    }
                }
            }
        }
    }
    }

    private fun applyLanguage(languageTag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSystemService(android.app.LocaleManager::class.java)
                .applicationLocales = if (languageTag == "system") LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(languageTag)
        } else {
            @Suppress("DEPRECATION")
            resources.updateConfiguration(
                resources.configuration.apply { setLocale(if (languageTag == "system") Locale.getDefault() else Locale.forLanguageTag(languageTag)) },
                resources.displayMetrics
            )
        }
    }
}

@Composable
fun GoalioBackground(backgroundAlpha: Float = 1f, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(GoalioColors.Background)) {
        content()
    }
}

@Composable
fun SplashScreen() {
    val metrics = rememberGoalioMetrics()
    val transition = rememberInfiniteTransition(label = "splash motion")
    val rotation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "splash ball angle"
    ).value
    val sweep = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "pitch sweep"
    ).value
    GoalioBackground {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1C1005), GoalioColors.Background),
                    center = center,
                    radius = size.minDimension * .72f
                )
            )
            val lineColor = GoalioColors.Tertiary.copy(alpha = .09f)
            val fieldWidth = size.width * .88f
            val fieldHeight = size.height * .62f
            val left = (size.width - fieldWidth) / 2f
            val top = (size.height - fieldHeight) / 2f
            val stroke = (size.minDimension * .004f).coerceAtLeast(1.5f)
            drawRoundRect(
                color = lineColor,
                topLeft = Offset(left, top),
                size = Size(fieldWidth, fieldHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke * 4f),
                style = Stroke(width = stroke)
            )
            drawLine(lineColor, Offset(left, center.y), Offset(left + fieldWidth, center.y), stroke)
            drawCircle(lineColor, radius = fieldWidth * .14f, center = center, style = Stroke(width = stroke))
            drawCircle(lineColor, radius = stroke * 1.35f, center = center)
            val boxWidth = fieldWidth * .34f
            val boxHeight = fieldHeight * .13f
            drawRect(lineColor, Offset(center.x - boxWidth / 2f, top), Size(boxWidth, boxHeight), style = Stroke(stroke))
            drawRect(lineColor, Offset(center.x - boxWidth / 2f, top + fieldHeight - boxHeight), Size(boxWidth, boxHeight), style = Stroke(stroke))
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(metrics.dp(if (metrics.compact) 210 else 244)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.goalio_ball),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .offset(x = metrics.dp(5), y = metrics.dp(6))
                            .size(metrics.dp(if (metrics.compact) 76 else 88))
                            .graphicsLayer {
                                rotationZ = rotation
                                alpha = .98f
                            }
                    )
                    Image(
                        painter = painterResource(R.drawable.goalio_mark_noball),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("GOAL", color = Color.White, fontSize = metrics.sp(if (metrics.compact) 38 else 44), fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                    Text("IO", color = GoalioColors.Tertiary, fontSize = metrics.sp(if (metrics.compact) 38 else 44), fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic)
                }
                Spacer(Modifier.height(metrics.dp(5)))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(metrics.dp(10))) {
                    Box(Modifier.size(width = metrics.dp(28), height = metrics.dp(2)).background(GoalioColors.Tertiary))
                    Text("LIVE FOOTBALL SCORES", color = Color(0xFFD8D8D8), fontSize = metrics.sp(10), fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                    Box(Modifier.size(width = metrics.dp(28), height = metrics.dp(2)).background(GoalioColors.Tertiary))
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = metrics.dp(64))
                    .size(
                        width = metrics.dp(if (metrics.compact) 180 else 220),
                        height = metrics.dp(6)
                    )
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = .14f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(sweep.coerceIn(.08f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(GoalioColors.Tertiary)
                )
            }
        }
    }
}

@Composable
private fun GoalioHomeScreen() {
    GoalioBackground(.4f) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(APP_DISPLAY_NAME, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun LoadingGoal(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "splash loading")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ball progress"
    ).value
    val trackWidth = 300.dp
    val ballSize = 42.dp
    val goalWidth = 64.dp
    val travelPx = with(LocalDensity.current) { (trackWidth - ballSize).toPx() }
    val density = LocalDensity.current
    val ballVisible = progress < .86f

    Box(modifier = modifier.width(trackWidth).size(width = trackWidth, height = 76.dp)) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(trackWidth - 20.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF9B9B9B))
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width((trackWidth - 20.dp) * progress)
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(GoalioColors.Accent)
        )
        Image(
            painter = painterResource(R.drawable.football_goal),
            contentDescription = "Goal",
            contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.CenterEnd).size(goalWidth)
        )
        Image(
            painter = painterResource(R.drawable.football_ball),
            contentDescription = "Loading",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = with(density) { (travelPx * progress).toDp() })
                .size(ballSize)
                .graphicsLayer {
                    alpha = if (ballVisible) 1f else 0f
                    rotationZ = progress * 720f
                }
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun SplashPreview() = GoalioTheme(dynamicColor = false) { SplashScreen() }
