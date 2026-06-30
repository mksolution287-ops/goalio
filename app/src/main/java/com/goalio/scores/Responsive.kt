package com.goalio.scores

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class GoalioMetrics(
    val horizontalPadding: Dp,
    val bottomBarPadding: Dp,
    val compact: Boolean,
    val scale: Float
) {
    fun dp(value: Int): Dp = (value * scale).dp
    fun sp(value: Int): TextUnit = (value * scale).sp
}

@Composable
fun rememberGoalioMetrics(): GoalioMetrics {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp
    val compact = width < 380 || height < 740
    val scale = when {
        width < 340 -> .84f
        width < 380 -> .90f
        width > 460 -> 1.08f
        else -> 1f
    }
    return GoalioMetrics(
        horizontalPadding = when {
            width < 340 -> 14.dp
            width < 380 -> 18.dp
            width > 460 -> 28.dp
            else -> 22.dp
        },
        bottomBarPadding = if (compact) 86.dp else 104.dp,
        compact = compact,
        scale = scale
    )
}
