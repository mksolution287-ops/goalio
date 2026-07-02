package com.goalio.scores

import android.Manifest
import android.os.Bundle
import android.os.Build
import android.os.LocaleList
import android.view.ViewGroup
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.unit.dp
import com.goalio.scores.ui.theme.GoalioTheme
import com.goalio.scores.ui.theme.GoalioColors
import com.onesignal.OneSignal
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity() {
    private companion object {
        const val PREF_LANGUAGE_RETURN_SCREEN = "language_return_screen"
        const val PREF_SUPPRESS_NEXT_SPLASH = "suppress_next_splash"
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
                val suppressNextSplash = remember { settings.getBoolean(PREF_SUPPRESS_NEXT_SPLASH, false) }
                var currentLanguage by remember { mutableStateOf(settings.getString("language", "en-GB") ?: "en-GB") }
                var showSplash by remember { mutableStateOf(savedInstanceState == null && !suppressNextSplash) }
                var languageSelected by remember { mutableStateOf(settings.contains("language")) }
                var onboardingComplete by remember {
                    mutableStateOf(settings.getBoolean("onboarding_complete", false))
                }
                var profileComplete by remember {
                    mutableStateOf(settings.getBoolean("profile_complete", false))
                }
                var profileUsername by remember { mutableStateOf(settings.getString("profile_username", null)) }
                var appScreen by remember { mutableStateOf(pendingLanguageReturn ?: "home") }
                var languageReturnScreen by remember { mutableStateOf<String?>(null) }
                var selectedMatch by remember { mutableStateOf<ScheduleMatch?>(null) }
                var selectedDetailTab by remember { mutableStateOf("Overview") }
                val effectiveLanguage = if (currentLanguage == "system") Locale.getDefault().toLanguageTag() else currentLanguage
                val notificationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }

                LaunchedEffect(currentLanguage) {
                    applyLanguage(currentLanguage)
                    AppLanguageState.current = effectiveLanguage
                }

                LaunchedEffect(pendingLanguageReturn) {
                    if (pendingLanguageReturn != null || suppressNextSplash) {
                        settings.edit()
                            .remove(PREF_LANGUAGE_RETURN_SCREEN)
                            .remove(PREF_SUPPRESS_NEXT_SPLASH)
                            .apply()
                    }
                }

                LaunchedEffect(Unit) {
                    delay(2800)
                    showSplash = false
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
                            onBack = {
                                val returnScreen = languageReturnScreen
                                if (returnScreen != null) {
                                    languageReturnScreen = null
                                    appScreen = returnScreen
                                    languageSelected = true
                                } else if (onboardingComplete) {
                                    languageSelected = true
                                } else {
                                    showSplash = true
                                }
                            },
                            onDone = { languageTag ->
                                val returnScreen = languageReturnScreen
                                val editor = settings.edit()
                                    .putString("language", languageTag)
                                    .putBoolean(PREF_SUPPRESS_NEXT_SPLASH, true)
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
                        onSignIn = { name, username ->
                            try {
                                val saved = GoalioBackendApi.signInExistingProfile(name, username)
                                settings.edit()
                                    .putString("profile_full_name", saved.name)
                                    .putString("profile_username", saved.username)
                                    .putStringSet("profile_team_ids", saved.favoriteTeamIds.toSet())
                                    .putStringSet("profile_player_ids", saved.favoritePlayerIds.toSet())
                                    .putStringSet("profile_team_names", saved.favoriteTeams.toSet())
                                    .putStringSet("profile_player_names", saved.favoritePlayers.toSet())
                                    .putBoolean("profile_complete", true)
                                    .apply()
                                profileComplete = true
                                profileUsername = saved.username
                                null
                            } catch (error: Exception) {
                                error.message ?: "Full name or username did not match."
                            }
                        },
                        onComplete = { profile ->
                            try {
                                val saved = GoalioBackendApi.saveProfile(profile)
                                settings.edit()
                                    .putString("profile_full_name", saved.name)
                                    .putString("profile_username", saved.username)
                                    .putStringSet("profile_team_ids", saved.favoriteTeamIds.toSet())
                                    .putStringSet("profile_player_ids", saved.favoritePlayerIds.toSet())
                                    .putStringSet("profile_team_names", saved.favoriteTeams.toSet())
                                    .putStringSet("profile_player_names", saved.favoritePlayers.toSet())
                                    .putBoolean("profile_complete", true)
                                    .apply()
                                profileComplete = true
                                profileUsername = saved.username
                                null
                            } catch (error: Exception) {
                                error.message ?: "Could not save your profile. Check the connection and try again."
                            }
                        }
                    )
                    else -> when (appScreen) {
                        "matches" -> MatchScreen(
                            onBack = { appScreen = "home" },
                            onOpenHome = { appScreen = "home" },
                            onOpenWorldCup = { appScreen = "worldcup" },
                            onOpenGames = { appScreen = "games" },
                            onOpenSettings = { appScreen = "settings" },
                            onOpenMatch = { match ->
                                selectedMatch = match
                                selectedDetailTab = "Overview"
                                appScreen = "detail"
                            }
                        )
                        "detail" -> selectedMatch?.let { match ->
                            MatchDetailScreen(
                                league = match.league,
                                matchId = match.matchId,
                                initialMatch = match,
                                initialTab = selectedDetailTab,
                                onBack = { appScreen = "matches" },
                                onOpenHome = { appScreen = "home" },
                                onOpenMatches = { appScreen = "matches" },
                                onOpenWorldCup = { appScreen = "worldcup" },
                                onOpenGames = { appScreen = "games" },
                                onOpenSettings = { appScreen = "settings" }
                            )
                        } ?: run {
                            appScreen = "matches"
                            MatchScreen(
                                onBack = { appScreen = "home" },
                                onOpenHome = { appScreen = "home" },
                                onOpenWorldCup = { appScreen = "worldcup" },
                                onOpenGames = { appScreen = "games" },
                                onOpenSettings = { appScreen = "settings" },
                                onOpenMatch = { match ->
                                    selectedMatch = match
                                    selectedDetailTab = "Overview"
                                    appScreen = "detail"
                                }
                            )
                        }
                        "worldcup" -> WorldCupScreen(
                            onBack = { appScreen = "home" },
                            onOpenHome = { appScreen = "home" },
                            onOpenMatches = { appScreen = "matches" },
                            onOpenGames = { appScreen = "games" },
                            onOpenSettings = { appScreen = "settings" }
                        )
                        "games" -> GameScreen(
                            onBack = { appScreen = "home" },
                            onOpenHome = { appScreen = "home" },
                            onOpenMatches = { appScreen = "matches" },
                            onOpenWorldCup = { appScreen = "worldcup" },
                            onOpenSettings = { appScreen = "settings" },
                            currentUsername = profileUsername,
                            onSaveUsername = { username ->
                                try {
                                    val saved = GoalioBackendApi.saveProfile(
                                        ProfileDraft(
                                            settings.getString("profile_full_name", null).orEmpty(),
                                            username,
                                            settings.getStringSet("profile_team_ids", emptySet()).orEmpty(),
                                            settings.getStringSet("profile_player_ids", emptySet()).orEmpty()
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
                                    error.message ?: "Could not save username. Check the connection and try again."
                                }
                            }
                        )
                        "settings" -> SettingsScreen(
                            onBack = { appScreen = "home" }, onHome = { appScreen = "home" },
                            onMatches = { appScreen = "matches" }, onWorldCup = { appScreen = "worldcup" },
                            onGames = { appScreen = "games" },
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
                            onOpenMatches = { appScreen = "matches" },
                            onOpenWorldCup = { appScreen = "worldcup" },
                            onOpenGames = { appScreen = "games" },
                            onOpenSettings = { appScreen = "settings" },
                            onOpenMatch = { match, tab ->
                                selectedMatch = match
                                selectedDetailTab = tab
                                appScreen = "detail"
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
    GoalioBackground {
        Box(Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(R.drawable.goalio_logo),
                contentDescription = APP_DISPLAY_NAME,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(metrics.dp(180))
            )
            LoadingGoal(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = metrics.dp(if (metrics.compact) 46 else 66))
                    .padding(horizontal = metrics.horizontalPadding)
            )
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
