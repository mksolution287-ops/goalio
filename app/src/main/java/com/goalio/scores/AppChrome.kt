package com.goalio.scores

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalio.scores.ui.theme.GoalioColors

@Composable
fun GoalioTopBar(title: String = "GOALIO", onBack: (() -> Unit)? = null, onSearch: () -> Unit = {}, onNotifications: () -> Unit = {}, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 58.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) Text("‹", color = Color.White, fontSize = 38.sp, modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp))
        Text(title, color = GoalioColors.Secondary, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp, modifier = Modifier.weight(1f))
        ChromeIcon(R.drawable.ic_search, "Search", onSearch)
        Spacer(Modifier.width(16.dp)); ChromeIcon(R.drawable.ic_bell, "Notifications", onNotifications)
        Spacer(Modifier.width(16.dp)); ChromeIcon(R.drawable.ic_settings, "Settings", onSettings)
    }
}

@Composable private fun ChromeIcon(icon: Int, description: String, onClick: () -> Unit) {
    Icon(painterResource(icon), description, tint = GoalioColors.Secondary, modifier = Modifier.size(25.dp).clickable(onClick = onClick))
}

@Composable
private fun BottomBarIcon(label: String, tint: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        when (label) {
            "Home" -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.5f, h * 0.2f)
                    lineTo(w * 0.2f, h * 0.5f)
                    lineTo(w * 0.2f, h * 0.85f)
                    lineTo(w * 0.8f, h * 0.85f)
                    lineTo(w * 0.8f, h * 0.5f)
                    close()
                }
                drawPath(path, tint, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
            "Matches" -> {
                drawCircle(tint, w * 0.36f, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                drawArc(tint, 45f, 90f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                drawArc(tint, 225f, 90f, false, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
            "World Cup" -> {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.32f, h * 0.2f)
                    lineTo(w * 0.68f, h * 0.2f)
                    lineTo(w * 0.64f, h * 0.5f)
                    lineTo(w * 0.54f, h * 0.5f)
                    lineTo(w * 0.54f, h * 0.72f)
                    lineTo(w * 0.64f, h * 0.72f)
                    lineTo(w * 0.64f, h * 0.8f)
                    lineTo(w * 0.36f, h * 0.8f)
                    lineTo(w * 0.36f, h * 0.72f)
                    lineTo(w * 0.46f, h * 0.72f)
                    lineTo(w * 0.46f, h * 0.5f)
                    lineTo(w * 0.36f, h * 0.5f)
                    close()
                }
                drawPath(path, tint, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
            else -> {
                drawRoundRect(tint, androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.32f), androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.36f), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                drawCircle(tint, 2.dp.toPx(), androidx.compose.ui.geometry.Offset(w * 0.68f, h * 0.5f))
                drawCircle(tint, 2.dp.toPx(), androidx.compose.ui.geometry.Offset(w * 0.6f, h * 0.5f))
            }
        }
    }
}

@Composable
fun GoalioBottomBar(modifier: Modifier = Modifier, selected: String, onHome: () -> Unit, onMatches: () -> Unit, onWorldCup: () -> Unit, onGames: () -> Unit) {
    val items = listOf("Home" to onHome, "Matches" to onMatches, "World Cup" to onWorldCup, "Games" to onGames)
    Surface(
        color = GoalioColors.Neutral,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        border = BorderStroke(1.dp, GoalioColors.CardBorder),
        modifier = modifier.fillMaxWidth().navigationBarsPadding()
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
            items.forEach { (label, action) ->
                val active = selected == label
                val fg = if (active) GoalioColors.Primary else GoalioColors.TextSecondary
                Surface(
                    color = if (active) GoalioColors.Accent else Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.weight(1f).height(62.dp).clickable(onClick = action)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                        BottomBarIcon(label, fg, Modifier.size(24.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(label, color = fg, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

