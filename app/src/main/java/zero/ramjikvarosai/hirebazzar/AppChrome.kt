package zero.ramjikvarosai.hirebazzar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zero.ramjikvarosai.hirebazzar.ui.theme.GoalioColors
import zero.ramjikvarosai.hirebazzar.components.BannerAd

@Composable
fun GoalioTopBar(
    title: String = APP_DISPLAY_NAME,
    onBack: (() -> Unit)? = null,
    onSettings: () -> Unit
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp, max = 58.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            ChromeIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(12.dp))
        }
        val isBrandTitle = title == APP_DISPLAY_NAME
        Text(
            trans(title),
            color = GoalioColors.Secondary,
            fontSize = if (isBrandTitle) 15.sp else 26.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = if (isBrandTitle) 0.sp else 4.sp,
            lineHeight = if (isBrandTitle) 17.sp else 30.sp,
            maxLines = if (isBrandTitle) 2 else 1,
            modifier = Modifier.weight(1f)
        )
        ChromeIcon(Icons.Default.Settings, "Settings", onSettings)
    }
}

@Composable
private fun ChromeIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    Surface(color = Color.Transparent, shape = CircleShape, modifier = Modifier.size(30.dp).clickable(onClick = onClick)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = GoalioColors.Secondary, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun GoalioBottomBar(modifier: Modifier = Modifier, selected: String, onHome: () -> Unit, onMatches: () -> Unit, onWorldCup: () -> Unit, onGames: () -> Unit) {
    val competitionLabel = GoalioRemoteConfig.competitionHubMode().label
    val items = buildList {
        add("Home" to onHome)
        add("Matches" to onMatches)
        competitionLabel?.let { add(it to onWorldCup) }
        add("Games" to onGames)
    }
    Surface(
        color = GoalioColors.Neutral,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        border = BorderStroke(1.dp, GoalioColors.CardBorder),
        modifier = modifier.fillMaxWidth().navigationBarsPadding()
    ) {
        Column {
            BannerAd()
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
                            Icon(bottomBarIcon(label), contentDescription = label, tint = fg, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(trans(label), color = fg, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun bottomBarIcon(label: String): ImageVector = when (label) {
    "Home" -> Icons.Default.Home
    "Matches" -> Icons.Default.SportsSoccer
    "World Cup" -> Icons.Default.EmojiEvents
    "League" -> Icons.Default.SportsSoccer
    else -> Icons.Default.SportsEsports
}

