package com.goalio.scores

import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.goalio.scores.ui.theme.GoalioColors
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val title: String,
    val subtitle: String,
    val features: List<String>
)

private val onboardingPages = listOf(
    OnboardingPage(
        "Never Miss a Kick",
        "Experience the game as it happens with live scores, real-time stats, and instant match alerts.",
        listOf("LIVE MATCH CENTER", "DEEP STATS", "CUSTOM ALERTS")
    ),
    OnboardingPage(
        "Track Every Tournament",
        "Follow fixtures, standings, and match details from the World Cup and the biggest leagues.",
        listOf("GROUP TABLES", "FIXTURES", "MATCH DETAILS")
    ),
    OnboardingPage(
        "Build Your Dashboard",
        "Pin favorite teams and players so your home feed opens with the football you care about.",
        listOf("FAVORITES", "PLAYER HEROES", "SMART FEED")
    )
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()
    val page = pagerState.currentPage

    BackHandler(enabled = page > 0) {
        scope.launch { pagerState.animateScrollToPage(page - 1) }
    }

    Box(Modifier.fillMaxSize().background(GoalioColors.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            OnboardingHeader(onSkip = onComplete)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                beyondViewportPageCount = 1
            ) { index ->
                OnboardingPageContent(index, onboardingPages[index])
            }
            PagerDots(selected = page, count = onboardingPages.size)
            Spacer(Modifier.height(metrics.dp(18)))
            Button(
                onClick = {
                    if (page == onboardingPages.lastIndex) onComplete()
                    else scope.launch { pagerState.animateScrollToPage(page + 1) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.dp(62))
                    .padding(horizontal = metrics.horizontalPadding),
                shape = RoundedCornerShape(metrics.dp(20)),
                border = BorderStroke(2.dp, GoalioColors.Tertiary),
                colors = ButtonDefaults.buttonColors(containerColor = GoalioColors.Neutral, contentColor = Color.White)
            ) {
                Text(
                    if (page == onboardingPages.lastIndex) "Kick Off" else "Next  >",
                    fontSize = metrics.sp(19),
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.height(metrics.dp(20)))
            Text(
                "Premium Experience for Serious Fans",
                color = Color(0xFF8D8D8D),
                fontSize = metrics.sp(13),
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(metrics.dp(18)))
        }
    }
}

@Composable
private fun OnboardingHeader(onSkip: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Row(
        Modifier
            .fillMaxWidth()
            .height(metrics.dp(82))
            .padding(horizontal = metrics.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.football_ball),
            contentDescription = null,
            modifier = Modifier.size(metrics.dp(30))
        )
        Spacer(Modifier.width(metrics.dp(10)))
        Text("Goalio", color = Color.White, fontSize = metrics.sp(27), fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        Spacer(Modifier.weight(1f))
        Surface(
            color = Color(0xFF241000),
            contentColor = Color.White,
            border = BorderStroke(2.dp, GoalioColors.Tertiary),
            shape = RoundedCornerShape(50),
            modifier = Modifier.clickable(onClick = onSkip)
        ) {
            Text(
                "Skip",
                fontSize = metrics.sp(15),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = metrics.dp(22), vertical = metrics.dp(10))
            )
        }
    }
}

@Composable
private fun OnboardingAnimation(index: Int, modifier: Modifier = Modifier) {
    val assetName = when (index) {
        0 -> "xag5k7cbUw.json"
        1 -> "A92h7xNCQ6.json"
        else -> "Football team players.json"
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(assetName))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        speed = 1.6f
    )
    val fontMap = remember {
        mapOf("HelveticaNeueLTStd-MdEx" to Typeface.create("sans-serif", Typeface.BOLD))
    }
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier,
        fontMap = fontMap
    )
}

@Composable
private fun OnboardingPageTextAndPills(page: OnboardingPage, metrics: GoalioMetrics) {
    Text(
        page.title,
        color = Color.White,
        fontSize = metrics.sp(27),
        lineHeight = metrics.sp(32),
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(metrics.dp(12)))
    Text(
        page.subtitle,
        color = Color(0xFFE0E0E0),
        fontSize = metrics.sp(16),
        lineHeight = metrics.sp(23),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(.94f)
    )
    Spacer(Modifier.height(metrics.dp(18)))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        page.features.forEach { FeaturePill(it) }
    }
}

@Composable
private fun OnboardingPageContent(index: Int, page: OnboardingPage) {
    val metrics = rememberGoalioMetrics()
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = metrics.horizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            OnboardingAnimation(
                index = index,
                modifier = Modifier
                    .fillMaxWidth(if (index == 0 && metrics.compact) .88f else .92f)
                    .aspectRatio(if (index == 0) 1.0f else 1.3f)
            )
        }
        OnboardingPageTextAndPills(page, metrics)
        Spacer(Modifier.height(metrics.dp(18)))
    }
}

@Composable
private fun FeaturePill(text: String) {
    Surface(
        color = Color(0xED242826),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, GoalioColors.Accent.copy(alpha = .42f))
    ) {
        Text(
            text,
            color = Color(0xFFF0F0F0),
            fontSize = 11.sp,
            letterSpacing = .7.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun PagerDots(selected: Int, count: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = if (index == selected) 30.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (index == selected) Color(0xFF5C5F5C) else Color(0xFF353836))
            )
        }
    }
}

