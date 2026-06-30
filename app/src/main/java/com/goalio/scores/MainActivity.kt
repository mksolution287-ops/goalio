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
import androidx.compose.ui.unit.dp
import com.goalio.scores.ui.theme.GoalioTheme
import com.goalio.scores.ui.theme.GoalioColors
import com.onesignal.OneSignal
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity() {
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
                var showSplash by remember { mutableStateOf(true) }
                var languageSelected by remember { mutableStateOf(settings.contains("language")) }
                var onboardingComplete by remember {
                    mutableStateOf(settings.getBoolean("onboarding_complete", false))
                }
                var profileComplete by remember {
                    mutableStateOf(settings.getBoolean("profile_complete", false))
                }
                var appScreen by remember { mutableStateOf("home") }
                var selectedMatch by remember { mutableStateOf<ScheduleMatch?>(null) }
                val notificationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }
                LaunchedEffect(Unit) {
                    delay(2800)
                    showSplash = false
                }
                LaunchedEffect(showSplash) {
                    if (!showSplash && !settings.getBoolean("notification_prompt_requested", false)) {
                        // The language screen is composed first, so it remains behind the OS prompt.
                        delay(300)
                        settings.edit().putBoolean("notification_prompt_requested", true).apply()
                        val appId = getString(R.string.onesignal_app_id)
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
                            }
                        }
                    }
                }
                when {
                    showSplash -> SplashScreen()
                    !languageSelected -> LanguageScreen(
                        onBack = { showSplash = true },
                        onDone = { languageTag ->
                            settings.edit().putString("language", languageTag).apply()
                            applyLanguage(languageTag)
                            languageSelected = true
                        }
                    )
                    !onboardingComplete -> OnboardingScreen(
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
                        onSkip = {
                            settings.edit().putBoolean("profile_complete", true).apply()
                            profileComplete = true
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
                            onOpenMatch = { match ->
                                selectedMatch = match
                                appScreen = "detail"
                            }
                        )
                        "detail" -> selectedMatch?.let { match ->
                            MatchDetailScreen(
                                league = match.league,
                                matchId = match.matchId,
                                initialMatch = match,
                                onBack = { appScreen = "matches" },
                                onOpenHome = { appScreen = "home" },
                                onOpenMatches = { appScreen = "matches" }
                            )
                        } ?: run {
                            appScreen = "matches"
                            MatchScreen(
                                onBack = { appScreen = "home" },
                                onOpenHome = { appScreen = "home" },
                                onOpenMatch = { match ->
                                    selectedMatch = match
                                    appScreen = "detail"
                                }
                            )
                        }
                        else -> PersonalizedHomeScreen(
                            fallbackName = settings.getString("profile_full_name", null),
                            fallbackTeams = settings.getStringSet("profile_team_names", emptySet()).orEmpty(),
                            fallbackPlayers = settings.getStringSet("profile_player_names", emptySet()).orEmpty(),
                            onOpenMatches = { appScreen = "matches" },
                            onOpenMatch = { match ->
                                selectedMatch = match
                                appScreen = "detail"
                            }
                        )
                    }
                }
            }
        }
    }

    private fun applyLanguage(languageTag: String) {
        if (languageTag == "system") return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSystemService(android.app.LocaleManager::class.java)
                .applicationLocales = LocaleList.forLanguageTags(languageTag)
        } else {
            @Suppress("DEPRECATION")
            resources.updateConfiguration(
                resources.configuration.apply { setLocale(Locale.forLanguageTag(languageTag)) },
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
    val transition = rememberInfiniteTransition(label = "splash geometry")
    val sweep = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
        label = "sweep"
    ).value
    val pulse = transition.animateFloat(
        initialValue = .65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    ).value

    GoalioBackground {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height * .42f)
                val radius = size.minDimension * .28f
                repeat(4) { ring ->
                    drawCircle(
                        color = Color.White.copy(alpha = .025f + ring * .012f),
                        radius = radius * (.62f + ring * .34f) * pulse,
                        center = center,
                        style = Stroke(width = 2.2f)
                    )
                }
                repeat(8) { index ->
                    val angle = Math.toRadians((sweep + index * 60).toDouble())
                    val start = Offset(
                        center.x + kotlin.math.cos(angle).toFloat() * radius * .45f,
                        center.y + kotlin.math.sin(angle).toFloat() * radius * .45f
                    )
                    val end = Offset(
                        center.x + kotlin.math.cos(angle).toFloat() * radius * 1.1f,
                        center.y + kotlin.math.sin(angle).toFloat() * radius * 1.1f
                    )
                    drawLine(
                        color = if (index % 2 == 0) GoalioColors.TextPrimary else GoalioColors.TextSecondary,
                        start = start,
                        end = end,
                        strokeWidth = 1.2f,
                        alpha = .08f
                    )
                }
                drawArc(
                    color = GoalioColors.Accent,
                    startAngle = sweep,
                    sweepAngle = 80f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 2.2f),
                    alpha = .22f
                )
            }
            Surface(
                color = GoalioColors.Surface1,
                shape = RoundedCornerShape(metrics.dp(34)),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoalioColors.CardBorder),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(metrics.dp(if (metrics.compact) 112 else 132))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "Goalio",
                        modifier = Modifier.size(metrics.dp(if (metrics.compact) 92 else 108))
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = metrics.dp(if (metrics.compact) 110 else 132))
                    .padding(horizontal = metrics.horizontalPadding)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "GOALIO",
                    color = GoalioColors.TextPrimary,
                    fontSize = metrics.sp(27),
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 7.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "LIVE FOOTBALL SCORES",
                    color = GoalioColors.TextSecondary,
                    fontSize = metrics.sp(11),
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun GoalioHomeScreen() {
    GoalioBackground(.4f) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("GOALIO", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                letterSpacing = 7.sp)
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
                .size(height = 5.dp, width = trackWidth - 20.dp)
                .clip(RoundedCornerShape(50))
                .background(GoalioColors.ProgressBackground)
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width((trackWidth - 20.dp) * progress)
                .size(height = 5.dp, width = (trackWidth - 20.dp) * progress)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
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
private fun SplashPreview() = GoalioTheme(dynamicColor = false) { SplashScreen() }
