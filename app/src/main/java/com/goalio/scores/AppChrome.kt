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
        Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp, modifier = Modifier.weight(1f))
        ChromeIcon(R.drawable.ic_search, "Search", onSearch)
        Spacer(Modifier.width(16.dp)); ChromeIcon(R.drawable.ic_bell, "Notifications", onNotifications)
        Spacer(Modifier.width(16.dp)); ChromeIcon(R.drawable.ic_settings, "Settings", onSettings)
    }
}

@Composable private fun ChromeIcon(icon: Int, description: String, onClick: () -> Unit) {
    Icon(painterResource(icon), description, tint = Color.White, modifier = Modifier.size(25.dp).clickable(onClick = onClick))
}

@Composable
fun GoalioBottomBar(modifier: Modifier = Modifier, selected: String, onHome: () -> Unit, onMatches: () -> Unit, onWorldCup: () -> Unit, onGames: () -> Unit) {
    Surface(color = Color(0xFF111111), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), border = BorderStroke(1.dp, GoalioColors.CardBorder), modifier = modifier.fillMaxWidth().navigationBarsPadding()) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceAround) {
            listOf("Home" to onHome, "Matches" to onMatches, "World Cup" to onWorldCup, "Games" to onGames).forEach { (label, action) ->
                Surface(color = if (selected == label) GoalioColors.Accent else Color.Transparent, shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f).clickable(onClick = action)) {
                    Text(label, color = if (selected == label) Color.Black else GoalioColors.TextSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(vertical = 15.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}
