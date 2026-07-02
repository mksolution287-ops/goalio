package com.goalio.scores

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalio.scores.ui.theme.GoalioColors

private data class LanguageOption(
    val tag: String,
    val name: String,
    val subtitle: String,
    val badge: String
)

private val languages = listOf(
    LanguageOption("en-GB", "English", "English (UK) · Global", "🇬🇧"),
    LanguageOption("es-419", "Español", "Spanish · Latinoamérica", "🇪🇸"),
    LanguageOption("fr-FR", "Français", "French · France", "🇫🇷"),
    LanguageOption("de-DE", "Deutsch", "German · Deutschland", "🇩🇪"),
    LanguageOption("pt-BR", "Português (Brasil)", "Portuguese · Brazil", "🇧🇷"),
    LanguageOption("ja-JP", "日本語", "Japanese · Japan", "🇯🇵"),
    LanguageOption("zh-CN", "简体中文", "Simplified Chinese · China", "🇨🇳"),
    LanguageOption("zh-TW", "繁體中文", "Traditional Chinese · Taiwan", "🇹🇼"),
    LanguageOption("ko-KR", "한국어", "Korean · South Korea", "🇰🇷"),
    LanguageOption("ar-SA", "العربية", "Arabic · الشرق الأوسط", "🇸🇦"),
    LanguageOption("it-IT", "Italiano", "Italian · Italia", "🇮🇹"),
    LanguageOption("ru-RU", "Русский", "Russian · Россия", "🇷🇺"),
    LanguageOption("hi-IN", "हिन्दी", "Hindi · भारत", "🇮🇳")
)

@Composable
fun LanguageScreen(onBack: () -> Unit, onDone: (String) -> Unit) {
    val metrics = rememberGoalioMetrics()
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf("en-GB") }
    val filtered = remember(query) {
        languages.filter { it.name.contains(query, true) || it.subtitle.contains(query, true) || it.tag.contains(query, true) }
    }

    BackHandler(onBack = onBack)
    GoalioBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(metrics.dp(82))
                    .padding(horizontal = metrics.horizontalPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBack,
                    color = GoalioColors.Accent,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(metrics.dp(52))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        BackIcon(Modifier.size(metrics.dp(25)), Color.White)
                    }
                }
                Text(
                    "GOALIO",
                    color = Color.White,
                    fontSize = metrics.sp(25),
                    fontWeight = FontWeight.Black,
                    letterSpacing = if (metrics.compact) 5.sp else 7.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { onDone(selected) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoalioColors.Accent,
                        contentColor = GoalioColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = metrics.dp(24), vertical = metrics.dp(11))
                ) {
                    Text("DONE", fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, fontSize = metrics.sp(13))
                }
            }
            HorizontalDivider(color = GoalioColors.Accent.copy(alpha = .16f))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(metrics.horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(metrics.dp(10))
            ) {
                item {
                    Spacer(Modifier.height(metrics.dp(22)))
                    Text("Select Language", color = Color.White, fontSize = metrics.sp(27), fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(metrics.dp(7)))
                    Text(
                        "Personalize your experience with your preferred language.",
                        color = GoalioColors.Body,
                        fontSize = metrics.sp(16),
                        lineHeight = metrics.sp(23)
                    )
                    Spacer(Modifier.height(metrics.dp(28)))
                    SearchBox(query) { query = it }
                    Spacer(Modifier.height(metrics.dp(17)))
                    Text(
                        "DEVICE SETTING",
                        color = GoalioColors.TextSecondary,
                        fontSize = metrics.sp(13),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Spacer(Modifier.height(metrics.dp(9)))
                    LanguageCard(
                        LanguageOption("system", "System Default", "Use your device language", "⚙"),
                        selected == "system"
                    ) { selected = "system" }
                    Spacer(Modifier.height(metrics.dp(19)))
                }
                items(filtered, key = { it.tag }) { language ->
                    LanguageCard(language, selected == language.tag) { selected = language.tag }
                }
                item { Spacer(Modifier.navigationBarsPadding().height(metrics.dp(12))) }
            }
        }
    }
}

@Composable
private fun SearchBox(value: String, onValueChange: (String) -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(metrics.dp(17)),
        border = BorderStroke(1.2.dp, GoalioColors.TextTertiary),
        modifier = Modifier.fillMaxWidth().height(metrics.dp(58))
    ) {
        Row(Modifier.padding(horizontal = metrics.dp(18)), verticalAlignment = Alignment.CenterVertically) {
            SearchIcon(Modifier.size(metrics.dp(27)), Color.White)
            Spacer(Modifier.width(metrics.dp(14)))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text("Search language...", color = GoalioColors.Placeholder, fontSize = metrics.sp(17))
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = metrics.sp(17)),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun LanguageCard(language: LanguageOption, selected: Boolean, onClick: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(
        onClick = onClick,
        color = if (selected) Color(0xFF140A02) else GoalioColors.Surface1,
        shape = RoundedCornerShape(metrics.dp(20)),
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) GoalioColors.Accent else GoalioColors.CardBorder
        ),
        modifier = Modifier.fillMaxWidth().height(metrics.dp(88))
    ) {
        Row(Modifier.padding(horizontal = metrics.dp(16)), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = Color.Black,
                border = BorderStroke(1.dp, GoalioColors.Border),
                modifier = Modifier.size(metrics.dp(46))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(language.badge, color = Color.White, fontSize = metrics.sp(22), fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.width(metrics.dp(16)))
            Column(Modifier.weight(1f)) {
                Text(
                    language.name,
                    color = GoalioColors.TextPrimary,
                    fontSize = metrics.sp(if (language.name.length > 18) 18 else 21),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    language.subtitle,
                    color = GoalioColors.TextSecondary,
                    fontSize = metrics.sp(15),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Box(
                    Modifier
                        .size(metrics.dp(27))
                        .border(3.dp, GoalioColors.Accent.copy(alpha = .35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(metrics.dp(16)).clip(CircleShape).background(GoalioColors.Accent))
                }
            }
        }
    }
}

@Composable
private fun SearchIcon(modifier: Modifier, color: Color) = Canvas(modifier) {
    drawCircle(
        color,
        radius = size.minDimension * .32f,
        center = Offset(size.width * .42f, size.height * .42f),
        style = Stroke(size.minDimension * .12f)
    )
    drawLine(
        color,
        Offset(size.width * .65f, size.height * .65f),
        Offset(size.width * .9f, size.height * .9f),
        size.minDimension * .12f,
        StrokeCap.Round
    )
}

@Composable
private fun BackIcon(modifier: Modifier, color: Color) = Canvas(modifier) {
    drawLine(color, Offset(size.width * .85f, size.height * .5f), Offset(size.width * .16f, size.height * .5f), size.minDimension * .11f, StrokeCap.Round)
    drawLine(color, Offset(size.width * .16f, size.height * .5f), Offset(size.width * .43f, size.height * .22f), size.minDimension * .11f, StrokeCap.Round)
    drawLine(color, Offset(size.width * .16f, size.height * .5f), Offset(size.width * .43f, size.height * .78f), size.minDimension * .11f, StrokeCap.Round)
}
