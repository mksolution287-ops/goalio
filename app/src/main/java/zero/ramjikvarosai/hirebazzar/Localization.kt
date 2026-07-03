package zero.ramjikvarosai.hirebazzar

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

val LocalAppLanguage = staticCompositionLocalOf { "en-GB" }

private val staticStrings = mapOf(
    "DONE" to R.string.done, "LANGUAGE" to R.string.language,
    "Choose your language" to R.string.choose_your_language,
    "Scores, match updates and navigation will follow your preferred locale." to R.string.language_description,
    "DEVICE" to R.string.device, "System Default" to R.string.system_default,
    "Use your device language" to R.string.use_device_language,
    "ALL LANGUAGES" to R.string.all_languages, "Search languages" to R.string.search_languages,
    "Overview" to R.string.overview, "Timeline" to R.string.timeline, "Stats" to R.string.stats,
    "AI Insight" to R.string.ai_insight, "Lineups" to R.string.lineups, "Watch" to R.string.watch,
    "Referee" to R.string.referee, "Stadium" to R.string.stadium, "Not listed" to R.string.not_listed,
    "Key Moments" to R.string.key_moments, "Performance Matrix" to R.string.performance_matrix,
    "MATCH STATS" to R.string.match_stats, "Possession" to R.string.possession,
    "Goalio AI Summary" to R.string.goalio_ai_summary,
    "Auto-generated match context" to R.string.auto_generated_match_context,
    "Preferences" to R.string.preferences, "Language" to R.string.language,
    "Match Notifications" to R.string.match_notifications, "On" to R.string.on, "Off" to R.string.off,
    "Favorite Teams" to R.string.favorite_teams, "Favorite Players" to R.string.favorite_players,
    "Add teams" to R.string.add_teams, "Add players" to R.string.add_players, "App" to R.string.app,
    "Privacy Policy" to R.string.privacy_policy, "Open" to R.string.open, "Version" to R.string.version,
    "Back" to R.string.back, "SETTINGS" to R.string.settings,
    "Profile, alerts and app controls" to R.string.settings_subtitle, "EDIT" to R.string.edit,
    "Member since" to R.string.member_since, "recently" to R.string.recently,
    "Level" to R.string.level, "Mastery" to R.string.mastery, "Teams" to R.string.teams,
    "Players" to R.string.players, "Alerts" to R.string.alerts, "Sign Out" to R.string.sign_out,
    "Edit Profile" to R.string.edit_profile, "Save" to R.string.save, "Cancel" to R.string.cancel,
    "Search teams" to R.string.search_teams, "Search players" to R.string.search_players,
    "Match Details" to R.string.match_details, "Win Probability" to R.string.win_probability,
    "View Analysis" to R.string.view_analysis, "Group A" to R.string.group_a,
    "View All" to R.string.view_all, "Rank" to R.string.rank, "Team" to R.string.team,
    "Stage" to R.string.stage, "The Football Five" to R.string.football_five,
    "5 questions. 1 streak. Climb the table." to R.string.football_five_subtitle,
    "WORLD CUP" to R.string.world_cup, "World Cup" to R.string.world_cup,
    "Group" to R.string.group, "COLLAPSE" to R.string.collapse, "EXPAND" to R.string.expand,
    "TEAM" to R.string.team, "World Cup Library" to R.string.world_cup_library,
    "min read" to R.string.min_read, "Read on FIFA.com" to R.string.read_on_fifa,
    "Home" to R.string.home, "Matches" to R.string.matches, "Games" to R.string.games, "GAMES" to R.string.games,
    "Live Action" to R.string.live_action, "NEXT 7 DAYS" to R.string.next_seven_days,
    "UPCOMING TODAY" to R.string.upcoming_today, "Groups" to R.string.groups,
    "The Knockout Path" to R.string.knockout_path, "World Cup Matches" to R.string.world_cup_matches,
    "PLAY" to R.string.play,
    "Choose date" to R.string.choose_date, "MATCH CENTER" to R.string.match_center,
    "Tap to open full match details" to R.string.tap_full_match_details,
    "Player Lineups" to R.string.player_lineups,
    "Starting XI, formation and bench" to R.string.lineup_subtitle,
    "STARTING XI" to R.string.starting_xi, "BENCH" to R.string.bench,
    "No bench listed" to R.string.no_bench_listed, "UNAVAILABLE" to R.string.unavailable,
    "Watch & Highlights" to R.string.watch_highlights, "STREAM" to R.string.stream,
    "HIGHLIGHTS" to R.string.highlights,
    "THE FOOTBALL FIVE" to R.string.football_five,
    "Test your football knowledge" to R.string.test_football_knowledge,
    "5 questions  ·  15 seconds each\n+10 XP correct   ·   −5 XP wrong" to R.string.quiz_rules,
    "LOADING…" to R.string.loading, "PLAY NOW" to R.string.play_now,
    "LEADERBOARD" to R.string.leaderboard, "TOP 10" to R.string.top_ten,
    "No leaderboard data yet. Play to get ranked!" to R.string.no_leaderboard,
    "YOU" to R.string.you,
    "Done" to R.string.done, "Next" to R.string.next, "Skip" to R.string.skip,
    "Your football. Your way." to R.string.your_football_your_way,
    "Swipe to explore" to R.string.swipe_to_explore, "BUILT FOR MATCHDAY" to R.string.built_for_matchday,
    "FEATURE" to R.string.feature, "Never Miss a Kick" to R.string.never_miss_kick,
    "Experience the game as it happens with live scores, real-time stats, and instant match alerts." to R.string.never_miss_kick_subtitle,
    "LIVE MATCH CENTER" to R.string.live_match_center, "DEEP STATS" to R.string.deep_stats, "CUSTOM ALERTS" to R.string.custom_alerts,
    "Track Every Tournament" to R.string.track_tournaments,
    "Follow fixtures, standings, and match details from the World Cup and the biggest leagues." to R.string.track_tournaments_subtitle,
    "GROUP TABLES" to R.string.group_tables, "FIXTURES" to R.string.fixtures, "MATCH DETAILS" to R.string.match_details,
    "Build Your Dashboard" to R.string.build_dashboard,
    "Pin favorite teams and players so your home feed opens with the football you care about." to R.string.build_dashboard_subtitle,
    "FAVORITES" to R.string.favorites, "PLAYER HEROES" to R.string.player_heroes, "SMART FEED" to R.string.smart_feed,
    "BUILD YOUR PROFILE" to R.string.build_your_profile,
    "Choose your identity, teams and players. You can update these anytime." to R.string.profile_intro,
    "Your identity" to R.string.your_identity,
    "Create a new profile or recover one securely." to R.string.identity_subtitle,
    "FULL NAME" to R.string.full_name, "e.g., Alex Morgan" to R.string.full_name_hint,
    "PICK YOUR USERNAME" to R.string.pick_username, "e.g., GoalGetter99" to R.string.username_hint,
    "Favorite teams" to R.string.favorite_teams, "Build a feed around the clubs and nations you follow." to R.string.favorite_teams_subtitle,
    "Loading teams and players..." to R.string.loading_teams_players, "RETRY LOADING FAVORITES" to R.string.retry_favorites,
    "Search teams..." to R.string.search_teams, "Loading teams…" to R.string.loading_teams,
    "LOADING..." to R.string.loading, "LOAD 6 MORE TEAMS" to R.string.load_more_teams,
    "Favorite players" to R.string.favorite_players, "Pin the players you never want to miss." to R.string.favorite_players_subtitle,
    "Search players..." to R.string.search_players, "Loading players…" to R.string.loading_players,
    "LOAD 6 MORE PLAYERS" to R.string.load_more_players,
    "SIGNING IN..." to R.string.signing_in, "SAVING..." to R.string.saving,
    "CHECKING..." to R.string.checking, "SIGN IN" to R.string.sign_in, "CONTINUE" to R.string.continue_label,
    "Shots" to R.string.shots, "On Goal" to R.string.on_goal, "Corners" to R.string.corners,
    "Fouls" to R.string.fouls, "Yellow Cards" to R.string.yellow_cards, "Saves" to R.string.saves,
    "Passes" to R.string.passes, "Accurate Passes" to R.string.accurate_passes,
    "TOTAL SHOTS" to R.string.total_shots, "SHOTS ON TARGET" to R.string.shots_on_target,
    "P" to R.string.played_short, "W" to R.string.won_short, "D" to R.string.drawn_short,
    "L" to R.string.lost_short, "GD" to R.string.goal_difference_short, "PTS" to R.string.points_short,
    "Search" to R.string.search, "Settings" to R.string.settings,
    "NORTH AMERICA 2026" to R.string.north_america_2026,
    "Group A table will appear when FIFA publishes the current standings." to R.string.group_a_standings_pending,
    "HOME" to R.string.home_side, "AWAY" to R.string.away_side,
    "WIN PROBABILITY" to R.string.win_probability, "Formation" to R.string.formation,
    "selected" to R.string.selected, "Loading..." to R.string.loading,
    "LOADING..." to R.string.loading, "LOAD 6 MORE" to R.string.load_six_more,
    "Maximum 6 selections." to R.string.maximum_six_selections,
    "Add your name now. Pick a username later in Games." to R.string.identity_add_name_later,
    "+ PIN TO DASHBOARD" to R.string.pin_to_dashboard, "PINNED" to R.string.pinned,
    "All" to R.string.all_filter,
    "Enter first and last name; every name must have at least 2 letters." to R.string.full_name_validation
)

@StringRes
fun staticStringResource(text: String): Int? = staticStrings[text.trim()]

@Composable
fun trans(text: String): String {
    val resource = staticStringResource(text) ?: return text
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val localizedContext = remember(context, language) {
        val locale = Locale.forLanguageTag(language)
        context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(locale) }
        )
    }
    return localizedContext.resources.getString(resource)
}

@Composable
fun localizedStringResource(@StringRes resource: Int, vararg formatArgs: Any): String {
    val context = LocalContext.current
    val language = LocalAppLanguage.current
    val localizedContext = remember(context, language) {
        context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        )
    }
    return localizedContext.resources.getString(resource, *formatArgs)
}

@Composable
fun dynamicTrans(text: String): String {
    val language = LocalAppLanguage.current
    if (TranslationManager.isEnglish(language)) return text
    val context = LocalContext.current
    val manager = remember(context) { TranslationManager.get(context) }
    var translated by remember(text, language) { mutableStateOf(manager.peek(text, language) ?: text) }
    LaunchedEffect(text, language) { translated = manager.translateText(text, language) }
    return translated
}

@Composable
fun TText(text: String, targetLanguage: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember(context) { TranslationManager.get(context) }
    var translated by remember(text, targetLanguage) { mutableStateOf(manager.peek(text, targetLanguage) ?: text) }
    LaunchedEffect(text, targetLanguage) { translated = manager.translateText(text, targetLanguage) }
    Text(text = translated, modifier = modifier)
}

object AppLanguageState {
    @Volatile var current: String = "en-GB"
}
