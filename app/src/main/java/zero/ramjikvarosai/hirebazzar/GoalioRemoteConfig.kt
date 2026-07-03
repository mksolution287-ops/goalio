package zero.ramjikvarosai.hirebazzar

import com.google.firebase.remoteconfig.FirebaseRemoteConfig

object GoalioRemoteConfig {
    private const val DEFAULT_PRIVACY_POLICY_URL = "https://goalio.app/privacy"
    private const val DEFAULT_ONESIGNAL_APP_ID = "014b1cc7-22ca-494b-a8f0-b130b68cf140"

    fun privacyPolicyUrl(): String = FirebaseRemoteConfig.getInstance()
        .getString("privacy_policy_url")
        .trim()
        .takeIf { it.startsWith("https://") }
        ?: DEFAULT_PRIVACY_POLICY_URL

    fun oneSignalAppId(): String = FirebaseRemoteConfig.getInstance()
        .getString("onesignal_app_id")
        .trim()
        .takeIf { it.matches(Regex("[0-9a-fA-F-]{36}")) }
        ?: DEFAULT_ONESIGNAL_APP_ID

    fun worldCupEnabled(): Boolean {
        val value = FirebaseRemoteConfig.getInstance().getValue("world_cup_enabled")
        return if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) true else value.asBoolean()
    }

    fun leagueScreenEnabled(): Boolean = FirebaseRemoteConfig.getInstance().getBoolean("league_screen_enabled")

    fun competitionHubMode(): CompetitionHubMode = when {
        worldCupEnabled() -> CompetitionHubMode(label = "World Cup", screen = "worldcup")
        leagueScreenEnabled() -> CompetitionHubMode(label = "League", screen = "league")
        else -> CompetitionHubMode(label = null, screen = null)
    }
}

data class CompetitionHubMode(val label: String?, val screen: String?)
