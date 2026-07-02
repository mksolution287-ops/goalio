package com.goalio.scores

import android.app.Application
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.onesignal.OneSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GoalioApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        applicationScope.launch {
            runCatching { TranslationManager.get(this@GoalioApplication).warmCache() }
                .onFailure(FirebaseCrashlytics.getInstance()::recordException)
        }

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
            setDefaultsAsync(R.xml.remote_config_defaults).addOnCompleteListener {
                OneSignal.initWithContext(this@GoalioApplication, GoalioRemoteConfig.oneSignalAppId())
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
        }
    }

    companion object {
        lateinit var instance: GoalioApplication
            private set
    }
}
