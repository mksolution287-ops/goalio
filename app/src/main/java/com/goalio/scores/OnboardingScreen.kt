package com.goalio.scores

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalio.scores.ui.theme.GoalioColors
import kotlinx.coroutines.launch

private val Gold = GoalioColors.Accent
private val Ink = GoalioColors.Background
private val Card = GoalioColors.Surface1
private val Muted = GoalioColors.TextSecondary

private data class OnboardingPage(
    val title: String,
    val subtitle: String,
    val features: List<String>
)

private val onboardingPages = listOf(
    OnboardingPage(
        "Never Miss a Moment",
        "Follow live football from the FIFA World Cup and major leagues with real-time scores, match statistics, AI-powered insights, and instant notifications—all in one place.",
        listOf("⚽  Live Scores", "📊  Match Stats", "🔔  Instant Alerts")
    ),
    OnboardingPage(
        "Your Ultimate World Cup Companion",
        "Explore every group, standing, knockout bracket, historic tournament, legendary player, and iconic moment from football's biggest stage.",
        listOf("🏆  Group Tables", "🌍  World Cup Library", "📅  Knockout Bracket")
    ),
    OnboardingPage(
        "Predict. Play. Challenge.",
        "Test your football knowledge with daily trivia, predict match winners, roll random football challenges, and compete with fans around the world.",
        listOf("🎯  Predictions", "🎮  Daily Games", "🧠  Football Trivia")
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

    Box(Modifier.fillMaxSize().background(Ink)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
        ) {
            OnboardingHeader(onSkip = onComplete)
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 0.dp),
                beyondViewportPageCount = 1
            ) { index ->
                OnboardingPageContent(index, onboardingPages[index])
            }
            PagerDots(selected = page, count = onboardingPages.size)
            Spacer(Modifier.height(metrics.dp(14)))
            Button(
                onClick = {
                    if (page == onboardingPages.lastIndex) onComplete()
                    else scope.launch { pagerState.animateScrollToPage(page + 1) }
                },
                modifier = Modifier.fillMaxWidth().height(metrics.dp(62)).padding(horizontal = metrics.horizontalPadding),
                shape = RoundedCornerShape(metrics.dp(20)),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Text(
                    if (page == onboardingPages.lastIndex) "Kick Off" else "Next  →",
                    fontSize = metrics.sp(19),
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.height(metrics.dp(12)))
            Text(
                "PREMIUM EXPERIENCE FOR SERIOUS FANS",
                color = Color(0xFF858786),
                fontSize = metrics.sp(10),
                letterSpacing = 1.7.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(metrics.dp(14)))
        }
    }
}

@Composable
private fun OnboardingHeader(onSkip: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Row(
        Modifier.fillMaxWidth().height(metrics.dp(72)).padding(horizontal = metrics.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.football_ball),
            contentDescription = null,
            modifier = Modifier.size(metrics.dp(32))
        )
        Spacer(Modifier.width(metrics.dp(10)))
        Text("Goalio", color = Color.White, fontSize = metrics.sp(27), fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.weight(1f))
        Surface(
            color = Color.White,
            contentColor = Color.Black,
            shape = RoundedCornerShape(50),
            modifier = Modifier.clickable(onClick = onSkip)
        ) {
            Text("Skip", fontSize = metrics.sp(15), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = metrics.dp(20), vertical = metrics.dp(9)))
        }
    }
}

@Composable
private fun OnboardingPageContent(index: Int, page: OnboardingPage) {
    val metrics = rememberGoalioMetrics()
    Column(
        Modifier.fillMaxSize().padding(horizontal = metrics.horizontalPadding),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when (index) {
                0 -> LiveMatchIllustration(Modifier.fillMaxWidth(if (metrics.compact) .96f else .86f).aspectRatio(1.42f))
                1 -> WorldCupIllustration(Modifier.fillMaxWidth(if (metrics.compact) .98f else .9f).aspectRatio(1.42f))
                else -> PlayIllustration(Modifier.fillMaxWidth(if (metrics.compact) .98f else .9f).aspectRatio(1.42f))
            }
        }
        Text(
            page.title,
            color = Color.White,
            fontSize = metrics.sp(26),
            lineHeight = metrics.sp(31),
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(metrics.dp(10)))
        Text(
            page.subtitle,
            color = Color(0xFFD0D1D0),
            fontSize = metrics.sp(14),
            lineHeight = metrics.sp(20),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(.96f)
        )
        Spacer(Modifier.height(metrics.dp(14)))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            page.features.forEach { FeaturePill(it) }
        }
        Spacer(Modifier.height(metrics.dp(14)))
    }
}

@Composable
private fun FeaturePill(text: String) {
    Surface(
        color = Color(0xED222524),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = .42f))
    ) {
        Text(
            text,
            color = Color(0xFFF0F0F0),
            fontSize = 11.sp,
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
                Modifier.padding(horizontal = 4.dp)
                    .size(width = if (index == selected) 30.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (index == selected) Color.White else Color(0xFF454746))
            )
        }
    }
}

@Composable
private fun LiveMatchIllustration(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "live match")
    val pulse by transition.animateFloat(0.65f, 1.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val ballX by transition.animateFloat(-1f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ball")
    val score by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "score")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawStadiumLights()
            drawRoundRect(Color(0xFF0D100F), cornerRadius = androidx.compose.ui.geometry.CornerRadius(34f, 34f), style = Stroke(3f), topLeft = Offset(size.width * .11f, size.height * .08f), size = Size(size.width * .78f, size.height * .84f))
        }
        Column(
            Modifier.fillMaxWidth(.76f).background(Card, RoundedCornerShape(22.dp)).border(1.dp, Color(0xFF343837), RoundedCornerShape(22.dp)).padding(17.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size((10 * pulse).dp).background(Color(0xFFFF3C38), CircleShape))
                Spacer(Modifier.width(7.dp))
                Text("LIVE  •  67'", color = Color(0xFFFF6561), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("WORLD CUP", color = Muted, fontSize = 8.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(15.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceAround, modifier = Modifier.fillMaxWidth()) {
                TeamBadge("ARG", GoalioColors.Black400)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (score > .72f) "2  –  1" else "1  –  1", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Second half", color = Muted, fontSize = 8.sp)
                }
                TeamBadge("FRA", GoalioColors.Black600)
            }
            Spacer(Modifier.height(15.dp))
            Box(Modifier.fillMaxWidth().height(30.dp).background(Color(0xFF0D0F0E), RoundedCornerShape(8.dp))) {
                Image(
                    painterResource(R.drawable.football_ball), null,
                    modifier = Modifier.align(Alignment.Center).size(23.dp).then(Modifier).padding(1.dp).let { base ->
                        base
                    }.run { this }.rotate(ballX * 160f)
                )
                Canvas(Modifier.fillMaxSize()) {
                    val x = size.width / 2 + ballX * size.width * .35f
                    drawCircle(Gold.copy(alpha = .2f), 17f, Offset(x, size.height / 2))
                }
            }
        }
    }
}

@Composable
private fun TeamBadge(label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(34.dp).background(color, CircleShape).border(2.dp, Color.White.copy(alpha = .7f), CircleShape), contentAlignment = Alignment.Center) {
            Text(label.take(1), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WorldCupIllustration(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "world cup")
    val glow by transition.animateFloat(.25f, .75f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "glow")
    Canvas(modifier) {
        drawCircle(Color.White.copy(alpha = .03f + glow * .03f), size.minDimension * .4f, center)
        drawGlobe(center, size.minDimension * .24f)
        val cardW = size.width * .3f
        drawGroupCard(Offset(size.width * .05f, size.height * .22f), cardW, "GROUP A", listOf(GoalioColors.TextPrimary, GoalioColors.TextSecondary, GoalioColors.TextTertiary))
        drawBracket(Offset(size.width * .68f, size.height * .22f), size.width * .27f, size.height * .52f)
        drawTrophy(Offset(size.width * .5f, size.height * .56f), size.minDimension * .19f, Gold.copy(alpha = .76f + glow * .2f))
    }
}

@Composable
private fun PlayIllustration(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "play")
    val tilt by transition.animateFloat(-5f, 5f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tilt")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = .04f), size.minDimension * .43f, center)
            drawPredictionGraph()
            drawLeaderboard(Offset(size.width * .65f, size.height * .18f), size.width * .28f, size.height * .48f)
        }
        Column(
            Modifier.fillMaxWidth(.48f).rotate(tilt).background(Card, RoundedCornerShape(18.dp)).border(1.dp, Gold.copy(alpha = .55f), RoundedCornerShape(18.dp)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("DAILY TRIVIA", color = Gold, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(9.dp))
            Text("Who scored the winning goal?", color = Color.White, fontSize = 14.sp, lineHeight = 18.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text("+ 250 XP", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.background(Gold, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp))
        }
        Text("⚄", fontSize = 34.sp, modifier = Modifier.align(Alignment.BottomStart).padding(start = 34.dp, bottom = 18.dp).rotate(-12f))
    }
}

private fun DrawScope.drawStadiumLights() {
    repeat(5) { i ->
        val x = size.width * (.12f + i * .19f)
        drawCircle(Color.White.copy(alpha = .35f), 3.5f, Offset(x, size.height * .03f))
        drawLine(Color.White.copy(alpha = .045f), Offset(x, size.height * .04f), Offset(size.width / 2, size.height * .8f), strokeWidth = 2f)
    }
}

private fun DrawScope.drawGlobe(c: Offset, radius: Float) {
    drawCircle(GoalioColors.Black700, radius, c)
    drawCircle(Gold.copy(alpha = .68f), radius, c, style = Stroke(3f))
    drawOval(
        Gold.copy(alpha = .4f),
        topLeft = Offset(c.x - radius * .48f, c.y - radius),
        size = Size(radius * .96f, radius * 2f),
        style = Stroke(2f)
    )
    drawLine(Gold.copy(alpha = .4f), Offset(c.x - radius, c.y), Offset(c.x + radius, c.y), 2f)
    drawOval(
        Gold.copy(alpha = .28f),
        topLeft = Offset(c.x - radius, c.y - radius * .45f),
        size = Size(radius * 2f, radius * .9f),
        style = Stroke(2f)
    )
}

private fun DrawScope.drawGroupCard(origin: Offset, width: Float, title: String, flags: List<Color>) {
    val height = size.height * .55f
    drawRoundRect(Card, origin, Size(width, height), androidx.compose.ui.geometry.CornerRadius(20f))
    drawRoundRect(GoalioColors.Black400, origin, Size(width, height), androidx.compose.ui.geometry.CornerRadius(20f), style = Stroke(2f))
    flags.forEachIndexed { index, color ->
        val y = origin.y + height * (.36f + index * .2f)
        drawCircle(color, 8f, Offset(origin.x + width * .2f, y))
        drawRoundRect(Color.White.copy(alpha = .22f), Offset(origin.x + width * .36f, y - 3f), Size(width * (.42f - index * .04f), 6f), androidx.compose.ui.geometry.CornerRadius(5f))
    }
    drawRoundRect(Gold.copy(alpha = .8f), Offset(origin.x + width * .13f, origin.y + height * .14f), Size(width * .56f, 5f), androidx.compose.ui.geometry.CornerRadius(5f))
}

private fun DrawScope.drawBracket(origin: Offset, width: Float, height: Float) {
    val line = Gold.copy(alpha = .62f)
    repeat(4) { i ->
        val y = origin.y + height * (.1f + i * .26f)
        drawRoundRect(Card, Offset(origin.x, y), Size(width * .48f, 18f), androidx.compose.ui.geometry.CornerRadius(8f))
        drawLine(line, Offset(origin.x + width * .48f, y + 9f), Offset(origin.x + width * .68f, y + 9f), 2f)
    }
    drawLine(line, Offset(origin.x + width * .68f, origin.y + height * .1f + 9f), Offset(origin.x + width * .68f, origin.y + height * .88f + 9f), 2f)
    drawRoundRect(Card, Offset(origin.x + width * .7f, origin.y + height * .47f), Size(width * .28f, 22f), androidx.compose.ui.geometry.CornerRadius(8f))
}

private fun DrawScope.drawTrophy(c: Offset, radius: Float, color: Color) {
    val cup = Path().apply {
        moveTo(c.x - radius * .55f, c.y - radius * .6f)
        lineTo(c.x + radius * .55f, c.y - radius * .6f)
        cubicTo(c.x + radius * .45f, c.y + radius * .1f, c.x + radius * .22f, c.y + radius * .2f, c.x, c.y + radius * .25f)
        cubicTo(c.x - radius * .22f, c.y + radius * .2f, c.x - radius * .45f, c.y + radius * .1f, c.x - radius * .55f, c.y - radius * .6f)
        close()
    }
    drawPath(cup, color)
    drawLine(color, Offset(c.x, c.y + radius * .2f), Offset(c.x, c.y + radius * .65f), radius * .16f, StrokeCap.Round)
    drawLine(color, Offset(c.x - radius * .42f, c.y + radius * .7f), Offset(c.x + radius * .42f, c.y + radius * .7f), radius * .18f, StrokeCap.Round)
    drawArc(
        color, 90f, 180f, false,
        topLeft = Offset(c.x - radius * .9f, c.y - radius * .5f),
        size = Size(radius * .78f, radius * .7f),
        style = Stroke(radius * .12f)
    )
    drawArc(
        color, -90f, 180f, false,
        topLeft = Offset(c.x + radius * .12f, c.y - radius * .5f),
        size = Size(radius * .78f, radius * .7f),
        style = Stroke(radius * .12f)
    )
}

private fun DrawScope.drawPredictionGraph() {
    val path = Path().apply {
        moveTo(size.width * .07f, size.height * .79f)
        cubicTo(size.width * .25f, size.height * .58f, size.width * .35f, size.height * .84f, size.width * .54f, size.height * .63f)
        cubicTo(size.width * .69f, size.height * .47f, size.width * .78f, size.height * .55f, size.width * .94f, size.height * .32f)
    }
    drawPath(path, Gold.copy(alpha = .48f), style = Stroke(4f, cap = StrokeCap.Round))
    listOf(.07f to .79f, .54f to .63f, .94f to .32f).forEach { (x, y) -> drawCircle(Gold, 7f, Offset(size.width * x, size.height * y)) }
}

private fun DrawScope.drawLeaderboard(origin: Offset, width: Float, height: Float) {
    drawRoundRect(Card.copy(alpha = .92f), origin, Size(width, height), androidx.compose.ui.geometry.CornerRadius(20f))
    repeat(3) { i ->
        val y = origin.y + height * (.27f + i * .24f)
        drawCircle(listOf(Gold, GoalioColors.TextSecondary, GoalioColors.TextTertiary)[i], 8f, Offset(origin.x + width * .2f, y))
        drawRoundRect(Color.White.copy(alpha = .2f), Offset(origin.x + width * .36f, y - 3f), Size(width * (.42f - i * .05f), 6f), androidx.compose.ui.geometry.CornerRadius(5f))
    }
}
