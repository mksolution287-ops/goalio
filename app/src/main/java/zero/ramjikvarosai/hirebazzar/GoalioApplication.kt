package zero.ramjikvarosai.hirebazzar

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.onesignal.OneSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import zero.ramjikvarosai.hirebazzar.utils.AdManager
import zero.ramjikvarosai.hirebazzar.utils.AppOpenAdManager

class GoalioApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // ── App Open Ad Manager ───────────────────────────────────────────────
    lateinit var appOpenAdManager: AppOpenAdManager
        private set

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
        // Initialize AdMob
        MobileAds.initialize(this) {
            AdManager.init(this)
            appOpenAdManager = AppOpenAdManager(this)
            appOpenAdManager.loadAd(this)
        }
        //initializing metaads
        MobileAds.initialize(this)
    }

    companion object {
        lateinit var instance: GoalioApplication
            private set
    }
}
