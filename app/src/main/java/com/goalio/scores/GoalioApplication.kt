package com.goalio.scores

import android.app.Application
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.onesignal.OneSignal

class GoalioApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        FirebaseCrashlytics.getInstance().apply {
            setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
            setCustomKey("backend_base_url", BuildConfig.BACKEND_BASE_URL)
        }

        FirebaseRemoteConfig.getInstance().apply {
            setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 0 else 3600)
                    .build()
            )
            setDefaultsAsync(R.xml.remote_config_defaults)
            fetchAndActivate()
                .addOnSuccessListener {
                    FirebaseCrashlytics.getInstance().setCustomKey(
                        "backend_base_url",
                        getString("backend_base_url").ifBlank { BuildConfig.BACKEND_BASE_URL }
                    )
                }
                .addOnFailureListener { error ->
                    FirebaseCrashlytics.getInstance().recordException(error)
                }
        }

        val oneSignalAppId = getString(R.string.onesignal_app_id)
        if (oneSignalAppId.matches(Regex("[0-9a-fA-F-]{36}"))) {
            OneSignal.initWithContext(this, oneSignalAppId)
        }
    }
}
