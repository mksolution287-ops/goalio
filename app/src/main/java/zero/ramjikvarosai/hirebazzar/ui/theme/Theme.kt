package zero.ramjikvarosai.hirebazzar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GoalioColorScheme = darkColorScheme(
    primary = GoalioColors.Primary,
    onPrimary = GoalioColors.Secondary,
    primaryContainer = GoalioColors.Neutral,
    onPrimaryContainer = GoalioColors.Secondary,
    secondary = GoalioColors.Secondary,
    onSecondary = GoalioColors.Primary,
    tertiary = GoalioColors.Tertiary,
    onTertiary = GoalioColors.Primary,
    tertiaryContainer = GoalioColors.Tertiary,
    onTertiaryContainer = GoalioColors.Primary,
    background = GoalioColors.Background,
    onBackground = GoalioColors.TextPrimary,
    surface = GoalioColors.Surface1,
    onSurface = GoalioColors.TextPrimary,
    surfaceVariant = GoalioColors.Surface2,
    onSurfaceVariant = GoalioColors.TextSecondary,
    outline = GoalioColors.Border,
    error = GoalioColors.Error,
    onError = GoalioColors.TextPrimary
)

@Composable
fun GoalioTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GoalioColorScheme,
        typography = Typography,
        content = content
    )
}
