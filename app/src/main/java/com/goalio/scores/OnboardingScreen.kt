package com.goalio.scores

import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
fun OnboardingScreen(onBack: () -> Unit, onComplete: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()
    val page = pagerState.currentPage

    BackHandler {
        if (page > 0) scope.launch { pagerState.animateScrollToPage(page - 1) }
        else onBack()
    }

    Box(Modifier.fillMaxSize().background(GoalioColors.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            OnboardingHeader(
                currentPage = page,
                pageCount = onboardingPages.size,
                onBack = {
                    if (page > 0) scope.launch { pagerState.animateScrollToPage(page - 1) }
                    else onBack()
                },
                onSkip = onComplete
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                beyondViewportPageCount = 1
            ) { index ->
                OnboardingPageContent(index, onboardingPages[index])
            }
            PagerProgress(selected = page, count = onboardingPages.size)
            Spacer(Modifier.height(metrics.dp(16)))
            val finalPage = page == onboardingPages.lastIndex
            val buttonColor by animateColorAsState(
                targetValue = if (finalPage) GoalioColors.Tertiary else GoalioColors.Neutral,
                label = "onboardingButtonColor"
            )
            Button(
                onClick = {
                    if (finalPage) onComplete()
                    else scope.launch { pagerState.animateScrollToPage(page + 1) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.dp(62))
                    .padding(horizontal = metrics.horizontalPadding),
                shape = RoundedCornerShape(metrics.dp(20)),
                border = BorderStroke(2.dp, GoalioColors.Tertiary),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = if (finalPage) Color.Black else Color.White
                )
            ) {
                Text(
                    if (finalPage) "Done" else "Next",
                    fontSize = metrics.sp(19),
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.height(metrics.dp(12)))
            Text(
                if (finalPage) "Your football. Your way." else "Swipe to explore",
                color = Color(0xFF858585),
                fontSize = metrics.sp(12),
                letterSpacing = 1.1.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(metrics.dp(14)))
        }
    }
}

@Composable
private fun OnboardingHeader(currentPage: Int, pageCount: Int, onBack: () -> Unit, onSkip: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Row(
        Modifier
            .fillMaxWidth()
            .height(metrics.dp(74))
            .padding(horizontal = metrics.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(metrics.dp(38))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(metrics.dp(25))) {
                val strokeWidth = size.minDimension * .1f
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * .85f, size.height * .5f),
                    end = Offset(size.width * .15f, size.height * .5f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * .15f, size.height * .5f),
                    end = Offset(size.width * .43f, size.height * .2f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White,
                    start = Offset(size.width * .15f, size.height * .5f),
                    end = Offset(size.width * .43f, size.height * .8f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
            }
        }
        Spacer(Modifier.width(metrics.dp(10)))
        Column {
            Text("GOALIO", color = Color.White, fontSize = metrics.sp(22), fontWeight = FontWeight.Black, letterSpacing = 3.5.sp)
            Text(
                "${(currentPage + 1).toString().padStart(2, '0')}  /  ${pageCount.toString().padStart(2, '0')}",
                color = GoalioColors.Tertiary,
                fontSize = metrics.sp(10),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.3.sp
            )
        }
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
                modifier = Modifier.padding(horizontal = metrics.dp(20), vertical = metrics.dp(9))
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
        "BUILT FOR MATCHDAY",
        color = GoalioColors.Tertiary,
        fontSize = metrics.sp(11),
        letterSpacing = 2.sp,
        fontWeight = FontWeight.ExtraBold
    )
    Spacer(Modifier.height(metrics.dp(8)))
    Text(
        page.title,
        color = Color.White,
        fontSize = metrics.sp(29),
        lineHeight = metrics.sp(34),
        fontWeight = FontWeight.Black,
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
    Spacer(Modifier.height(metrics.dp(16)))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        page.features.forEach { feature ->
            FeaturePill(feature, Modifier.weight(1f))
        }
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
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = metrics.dp(8))
                .shadow(18.dp, RoundedCornerShape(metrics.dp(28)), ambientColor = GoalioColors.Tertiary.copy(.14f))
                .clip(RoundedCornerShape(metrics.dp(28)))
                .background(GoalioColors.Neutral)
                .border(1.dp, GoalioColors.Tertiary.copy(alpha = .42f), RoundedCornerShape(metrics.dp(28))),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(metrics.dp(16))
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = .65f))
                    .border(1.dp, GoalioColors.Tertiary.copy(.55f), RoundedCornerShape(50))
                    .padding(horizontal = metrics.dp(12), vertical = metrics.dp(7))
            ) {
                Text(
                    "FEATURE  ${(index + 1).toString().padStart(2, '0')}",
                    color = Color.White,
                    fontSize = metrics.sp(10),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.4.sp
                )
            }
            OnboardingAnimation(
                index = index,
                modifier = Modifier
                    .padding(metrics.dp(12))
                    .fillMaxWidth(.86f)
                    .aspectRatio(1.15f)
            )
        }
        Spacer(Modifier.height(metrics.dp(12)))
        OnboardingPageTextAndPills(page, metrics)
        Spacer(Modifier.height(metrics.dp(14)))
    }
}

@Composable
private fun FeaturePill(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = GoalioColors.Neutral,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, GoalioColors.Tertiary.copy(alpha = .38f)),
        modifier = modifier.height(44.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(GoalioColors.Tertiary))
                Spacer(Modifier.width(6.dp))
                Text(
                    text,
                    color = Color.White,
                    fontSize = 9.sp,
                    letterSpacing = .35.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun PagerProgress(selected: Int, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            val height by animateDpAsState(if (index == selected) 5.dp else 3.dp, label = "progressHeight")
            val color by animateColorAsState(
                if (index <= selected) GoalioColors.Tertiary else Color(0xFF383838),
                label = "progressColor"
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

