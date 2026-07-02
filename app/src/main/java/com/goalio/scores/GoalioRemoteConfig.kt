package com.goalio.scores

import com.google.firebase.remoteconfig.FirebaseRemoteConfig

object GoalioRemoteConfig {
    private const val DEFAULT_PRIVACY_POLICY_URL = "https://goalio.app/privacy"
    private const val DEFAULT_ONESIGNAL_APP_ID = "4159dbb2-0f8d-4da3-945d-dc590d14f729"

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
}
