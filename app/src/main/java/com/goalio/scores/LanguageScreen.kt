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
    LanguageOption("ar-SA", "العربية", "Arabic", "🇸🇦"),
    LanguageOption("en-GB", "English", "English", "🇬🇧"),
    LanguageOption("fr-FR", "Français", "French", "🇫🇷"),
    LanguageOption("de-DE", "Deutsch", "German", "🇩🇪"),
    LanguageOption("hi-IN", "हिन्दी", "Hindi", "🇮🇳"),
    LanguageOption("it-IT", "Italiano", "Italian", "🇮🇹"),
    LanguageOption("ja-JP", "日本語", "Japanese", "🇯🇵"),
    LanguageOption("ko-KR", "한국어", "Korean", "🇰🇷"),
    LanguageOption("pt-BR", "Português", "Portuguese", "🇧🇷"),
    LanguageOption("ru-RU", "Русский", "Russian", "🇷🇺"),
    LanguageOption("zh-CN", "简体中文", "Simplified Chinese", "🇨🇳"),
    LanguageOption("es-419", "Español", "Spanish", "🇪🇸"),
    LanguageOption("zh-TW", "繁體中文", "Traditional Chinese", "🇹🇼")
)

@Composable
fun LanguageScreen(
    onBack: () -> Unit,
    onDone: (String) -> Unit,
    initialLanguage: String = "en-GB"
) {
    val metrics = rememberGoalioMetrics()
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable(initialLanguage) {
        mutableStateOf(initialLanguage.takeIf { it == "system" || languages.any { language -> language.tag == it } } ?: "en-GB")
    }
    val filtered = remember(query) {
        languages.filter { it.name.contains(query, true) || it.subtitle.contains(query, true) || it.tag.contains(query, true) }
    }

    BackHandler(onBack = onBack)
    GoalioBackground {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(metrics.dp(72))
                    .padding(horizontal = metrics.horizontalPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBack,
                    color = GoalioColors.Neutral,
                    contentColor = Color.White,
                    border = BorderStroke(1.dp, GoalioColors.Border),
                    shape = CircleShape,
                    modifier = Modifier.size(metrics.dp(44))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        BackIcon(Modifier.size(metrics.dp(21)), GoalioColors.Secondary)
                    }
                }
                Text(
                    APP_DISPLAY_NAME,
                    color = Color.White,
                    fontSize = metrics.sp(if (metrics.compact) 12 else 14),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.sp,
                    lineHeight = metrics.sp(if (metrics.compact) 14 else 16),
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    onClick = { onDone(selected) },
                    color = Color(0xFF241000),
                    contentColor = GoalioColors.Secondary,
                    border = BorderStroke(1.5.dp, GoalioColors.Tertiary),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(trans("DONE"), fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, fontSize = metrics.sp(12), modifier = Modifier.padding(horizontal = metrics.dp(17), vertical = metrics.dp(11)))
                }
            }
            HorizontalDivider(color = GoalioColors.Divider)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(metrics.horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(metrics.dp(10))
            ) {
                item {
                    Spacer(Modifier.height(metrics.dp(24)))
                    Text(trans("Select Language"), color = Color.White, fontSize = metrics.sp(29), fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(metrics.dp(18)))
                    SearchBox(query) { query = it }
                    Spacer(Modifier.height(metrics.dp(16)))
                    LanguageCard(
                        LanguageOption("system", trans("System Default"), "", "⚙"),
                        selected == "system"
                    ) { selected = "system" }
                    Spacer(Modifier.height(metrics.dp(10)))
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
        color = GoalioColors.Neutral,
        shape = RoundedCornerShape(metrics.dp(16)),
        border = BorderStroke(1.dp, GoalioColors.Border),
        modifier = Modifier.fillMaxWidth().height(metrics.dp(56))
    ) {
        Row(Modifier.padding(horizontal = metrics.dp(18)), verticalAlignment = Alignment.CenterVertically) {
            SearchIcon(Modifier.size(metrics.dp(23)), GoalioColors.Tertiary)
            Spacer(Modifier.width(metrics.dp(14)))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(trans("Search languages"), color = GoalioColors.Placeholder, fontSize = metrics.sp(16))
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = metrics.sp(16)),
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
        color = if (selected) Color(0xFF241000) else GoalioColors.Neutral,
        shape = RoundedCornerShape(metrics.dp(16)),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) GoalioColors.Accent else GoalioColors.CardBorder
        ),
        modifier = Modifier.fillMaxWidth().height(metrics.dp(78))
    ) {
        Row(Modifier.padding(horizontal = metrics.dp(16)), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(metrics.dp(13)),
                color = if (selected) GoalioColors.Tertiary.copy(alpha = .12f) else GoalioColors.Primary,
                border = BorderStroke(1.dp, if (selected) GoalioColors.Tertiary.copy(alpha = .45f) else GoalioColors.Border),
                modifier = Modifier.size(metrics.dp(44))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(language.badge, color = Color.White, fontSize = metrics.sp(22), fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.width(metrics.dp(14)))
            Column(Modifier.weight(1f)) {
                Text(
                    language.name,
                    color = GoalioColors.TextPrimary,
                    fontSize = metrics.sp(if (language.name.length > 18) 17 else 19),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (language.subtitle.isNotBlank()) {
                    Text(
                        language.subtitle,
                        color = GoalioColors.TextTertiary,
                        fontSize = metrics.sp(12),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) {
                Box(
                    Modifier
                        .size(metrics.dp(25))
                        .border(2.dp, GoalioColors.Tertiary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(metrics.dp(13)).clip(CircleShape).background(GoalioColors.Tertiary))
                }
            } else {
                Box(Modifier.size(metrics.dp(25)).border(1.5.dp, GoalioColors.Gray300, CircleShape))
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
