package com.goalio.scores

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalio.scores.ui.theme.GoalioColors

private data class LanguageOption(
    val tag: String, val name: String, val subtitle: String, val badge: String
)

private val languages = listOf(
    LanguageOption("de", "Deutsch", "German", "DEU"),
    LanguageOption("en-US", "English (US)", "Default Content Language", "🇺🇸"),
    LanguageOption("es", "Español", "Spanish", "🇪🇸"),
    LanguageOption("fr", "Français", "French", "FRA"),
    LanguageOption("it", "Italiano", "Italian", "ITA"),
    LanguageOption("pt", "Português", "Portuguese", "🇵🇹")
)

@Composable
fun LanguageScreen(onBack: () -> Unit, onDone: (String) -> Unit) {
    val metrics = rememberGoalioMetrics()
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf("en-US") }
    val filtered = remember(query) {
        languages.filter { it.name.contains(query, true) || it.subtitle.contains(query, true) }
    }
    BackHandler(onBack = onBack)
    GoalioBackground(.25f) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(metrics.dp(68)).padding(horizontal = metrics.horizontalPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("‹", color = Color.White, fontSize = metrics.sp(46),
                    modifier = Modifier.clickable(onClick = onBack).padding(end = metrics.dp(18)))
                Text("GOALIO", color = Color.White, fontSize = metrics.sp(24), fontWeight = FontWeight.Bold,
                    letterSpacing = if (metrics.compact) 3.sp else 5.sp, modifier = Modifier.weight(1f))
                Button(
                    onClick = { onDone(selected) },
                    colors = ButtonDefaults.buttonColors(containerColor = GoalioColors.Accent, contentColor = GoalioColors.TextPrimary),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = metrics.dp(20), vertical = metrics.dp(9))
                ) { Text("DONE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp, fontSize = metrics.sp(13)) }
            }
            HorizontalDivider(color = GoalioColors.Divider)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(metrics.horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(metrics.dp(10))
            ) {
                item {
                    Spacer(Modifier.height(20.dp))
                    Text("Select Language", color = Color.White, fontSize = metrics.sp(26), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text("Personalize your experience with your preferred language.",
                        color = GoalioColors.Body, fontSize = metrics.sp(16), lineHeight = metrics.sp(22))
                    Spacer(Modifier.height(28.dp))
                    SearchBox(query) { query = it }
                    Spacer(Modifier.height(17.dp))
                    Text("DEVICE SETTING", color = GoalioColors.Caption, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(start = 4.dp))
                    Spacer(Modifier.height(9.dp))
                    LanguageCard(
                        LanguageOption("system", "System Default", "English (UK)", "⚙"),
                        selected == "system"
                    ) { selected = "system" }
                    Spacer(Modifier.height(19.dp))
                }
                items(filtered, key = { it.tag }) { language ->
                    LanguageCard(language, selected == language.tag) { selected = language.tag }
                }
                item { Spacer(Modifier.navigationBarsPadding().height(12.dp)) }
            }
        }
    }
}

@Composable
private fun SearchBox(value: String, onValueChange: (String) -> Unit) {
    val metrics = rememberGoalioMetrics()
    Row(
        Modifier.fillMaxWidth().height(metrics.dp(56)).clip(RoundedCornerShape(metrics.dp(14)))
            .then(Modifier).padding(horizontal = metrics.dp(0)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = GoalioColors.Search, shape = RoundedCornerShape(metrics.dp(14)),
            border = BorderStroke(1.dp, GoalioColors.Border),
            modifier = Modifier.fillMaxSize()
        ) {
            Row(Modifier.padding(horizontal = metrics.dp(16)), verticalAlignment = Alignment.CenterVertically) {
                Text("⌕", color = Color.White, fontSize = metrics.sp(30))
                Spacer(Modifier.width(metrics.dp(12)))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text("Search language...", color = GoalioColors.Placeholder, fontSize = metrics.sp(17))
                    BasicTextField(value, onValueChange, singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = metrics.sp(17)), modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun LanguageCard(language: LanguageOption, selected: Boolean, onClick: () -> Unit) {
    val metrics = rememberGoalioMetrics()
    Surface(
        onClick = onClick,
        color = GoalioColors.Surface1,
        shape = RoundedCornerShape(metrics.dp(22)),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp,
            if (selected) GoalioColors.Accent else GoalioColors.CardBorder),
        modifier = Modifier.fillMaxWidth().height(metrics.dp(82))
    ) {
        Row(Modifier.padding(horizontal = metrics.dp(16)), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape, color = GoalioColors.Background,
                border = BorderStroke(1.dp, GoalioColors.Border), modifier = Modifier.size(metrics.dp(46))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(language.badge, color = Color.White, fontSize = if (language.badge.length > 3) metrics.sp(19) else metrics.sp(13),
                        fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(metrics.dp(16)))
            Column(Modifier.weight(1f)) {
                Text(language.name, color = GoalioColors.TextPrimary, fontSize = metrics.sp(21),
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(language.subtitle, color = GoalioColors.TextSecondary, fontSize = metrics.sp(15),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selected) {
                Box(Modifier.size(metrics.dp(24)).clip(CircleShape), contentAlignment = Alignment.Center) {
                    Text("●", color = GoalioColors.Accent, fontSize = metrics.sp(22))
                }
            }
        }
    }
}
