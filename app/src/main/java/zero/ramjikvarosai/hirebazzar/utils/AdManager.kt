package zero.ramjikvarosai.hirebazzar.utils

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import zero.ramjikvarosai.hirebazzar.BuildConfig
import zero.ramjikvarosai.hirebazzar.R

object AdManager {
    private var initialized = false

    // ── Test Ad Unit IDs (replace with real ones for production) ─────────
    private const val TEST_BANNER_ID       = "ca-app-pub-3940256099942544/6300978111"
    private const val TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_NATIVE_ID       = "ca-app-pub-3940256099942544/2247696110"
    private const val TEST_APP_OPEN_ID     = "ca-app-pub-3940256099942544/9257395921"


    // ── Remote Config keys ────────────────────────────────────────────────
    private const val KEY_ADS_ENABLED          = "ads_enabled"
    private const val KEY_BANNER_ENABLED       = "banner_ad_enabled"
    private const val KEY_INTERSTITIAL_ENABLED = "interstitial_ad_enabled"
    private const val KEY_NATIVE_ENABLED       = "native_ad_enabled"
    private const val KEY_APP_OPEN_ENABLED     = "app_open_ad_enabled"
    private const val KEY_INTERSTITIAL_TRIGGER = "interstitial_trigger_count"
    private const val KEY_BANNER_AD_UNIT       = "banner_ad_unit_id"
    private const val KEY_INTERSTITIAL_AD_UNIT = "interstitial_ad_unit_id"
    private const val KEY_NATIVE_AD_UNIT       = "native_ad_unit_id"
    private const val KEY_APP_OPEN_AD_UNIT     = "app_open_ad_unit_id"


    // ── State ─────────────────────────────────────────────────────────────
    private var interstitialAd: InterstitialAd? = null
    private var actionCount = 0

    private val _adsEnabled = MutableStateFlow(false)
    val adsEnabled: StateFlow<Boolean> = _adsEnabled

    private val _bannerEnabled = MutableStateFlow(false)
    val bannerEnabled: StateFlow<Boolean> = _bannerEnabled

    private val _nativeEnabled = MutableStateFlow(false)
    val nativeEnabled: StateFlow<Boolean> = _nativeEnabled

    private val _appOpenEnabled = MutableStateFlow(false)
    val appOpenEnabled: StateFlow<Boolean> = _appOpenEnabled

    // ── Native Ad state ───────────────────────────────────────────────────
    private var nativeAd: NativeAd? = null
    private val _nativeAdReady = MutableStateFlow(false)
    val nativeAdReady: StateFlow<Boolean> = _nativeAdReady

    var isInterstitialShowing = false
        private set

    private val _isAdLoading = MutableStateFlow(false)
    val isAdLoading: StateFlow<Boolean> = _isAdLoading

    // ───────── FLAGS ─────────
    var isAppOpenAdBlocked = false
    private var isShowingAd = false

    // ───────── INTERSTITIAL ─────────
    private var lastAdTime = 0L

    // ── Remote Config defaults ────────────────────────────────────────────
    fun init(context: Context, onReady: () -> Unit = {}) {
        if (initialized) {
            onReady()
            return
        }
        initialized = true

        val appContext = context.applicationContext
        val remoteConfig = Firebase.remoteConfig
        remoteConfig.setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600
            }
        )
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults).addOnCompleteListener {
            applyRemoteConfig(appContext)
            onReady()

        // ── One-time fetch on start ───────────────────────────────────────
            remoteConfig.fetchAndActivate().addOnCompleteListener {
                applyRemoteConfig(appContext)
            }
        }

        // ── Real-time listener — fires whenever you change values in Firebase
        remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                remoteConfig.activate().addOnCompleteListener {
                    applyRemoteConfig(appContext)
                }
            }

            override fun onError(error: FirebaseRemoteConfigException) {
                Log.e("AdManager", "Real-time config error: ${error.message}")
            }
        })
    }

    // ── Extract repeated logic into one function ──────────────────────────────
    private fun applyRemoteConfig(context: Context) {
        val config = Firebase.remoteConfig

        _adsEnabled.value    = config.getBoolean(KEY_ADS_ENABLED)
        _bannerEnabled.value = config.getBoolean(KEY_ADS_ENABLED) &&
                config.getBoolean(KEY_BANNER_ENABLED)
        _nativeEnabled.value = config.getBoolean(KEY_ADS_ENABLED) &&
                config.getBoolean(KEY_NATIVE_ENABLED)
        _appOpenEnabled.value = config.getBoolean(KEY_ADS_ENABLED) &&
                config.getBoolean(KEY_APP_OPEN_ENABLED)

        Log.d("AdManager", "Config applied: ads=${_adsEnabled.value} " +
                "banner=${_bannerEnabled.value} native=${_nativeEnabled.value}")

        if (_adsEnabled.value) {
            preloadInterstitial(context)
            if (_nativeEnabled.value) preloadNativeAd(context)
        }
    }

    // Cooldown: blocks app open ad briefly after interstitial dismisses
    private var interstitialDismissedAt = 0L
    private const val INTERSTITIAL_COOLDOWN_MS = 2000L // 2 seconds

    fun isInterstitialRecentlyActive(): Boolean {
        return isInterstitialShowing ||
                (System.currentTimeMillis() - interstitialDismissedAt < INTERSTITIAL_COOLDOWN_MS)
    }

    // ── App Open Ad helpers (called by AppOpenAdManager) ──────────────────
    fun isAppOpenAdEnabled(): Boolean {
        val config = Firebase.remoteConfig
        return config.getBoolean(KEY_ADS_ENABLED) &&
                config.getBoolean(KEY_APP_OPEN_ENABLED)
    }

    fun getAppOpenAdUnitId(): String =
        Firebase.remoteConfig.getString(KEY_APP_OPEN_AD_UNIT).ifBlank { TEST_APP_OPEN_ID }


    // ── Banner ad unit ID from Remote Config ──────────────────────────────
    fun getBannerAdUnitId(): String {
        return Firebase.remoteConfig.getString(KEY_BANNER_AD_UNIT)
            .ifBlank { TEST_BANNER_ID }
    }

    fun getAppInstallNativeAdUnitId(): String =
        Firebase.remoteConfig.getString(KEY_NATIVE_AD_UNIT).ifBlank { TEST_NATIVE_ID }

    // ── Preload interstitial ──────────────────────────────────────────────
//    fun preloadInterstitial(context: Context) {
//        val config = Firebase.remoteConfig
//        if (!config.getBoolean(KEY_ADS_ENABLED) ||
//            !config.getBoolean(KEY_INTERSTITIAL_ENABLED)) return
//
//        val adUnitId = config.getString(KEY_INTERSTITIAL_AD_UNIT).ifBlank { TEST_INTERSTITIAL_ID }
//
//        InterstitialAd.load(
//            context,
//            adUnitId,
//            AdRequest.Builder().build(),
//            object : InterstitialAdLoadCallback() {
//                override fun onAdLoaded(ad: InterstitialAd) {
//                    interstitialAd = ad
//                    Log.d("AdManager", "Interstitial loaded ✅")
//                }
//                override fun onAdFailedToLoad(error: LoadAdError) {
//                    interstitialAd = null
//                    Log.e("AdManager", "Interstitial failed ❌ ${error.message}")
//                }
//            }
//        )
//    }

    fun suppressNextAppOpenAd(context: Context) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("skip_app_open_ad", true)
            .apply()
    }

    fun preloadInterstitial(context: Context) {
        val config = Firebase.remoteConfig

        if (!config.getBoolean(KEY_ADS_ENABLED) ||
            !config.getBoolean(KEY_INTERSTITIAL_ENABLED)) {
            Log.d("AdManager", "Interstitial disabled by config")
            return
        }

        val adUnitId = config
            .getString(KEY_INTERSTITIAL_AD_UNIT)
            .ifBlank { TEST_INTERSTITIAL_ID }

        val appContext = context.applicationContext  // ✅ IMPORTANT

        Log.d("AdManager", "Loading interstitial...")

        InterstitialAd.load(
            appContext,   // ✅ always use application context
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d("AdManager", "✅ Interstitial LOADED")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Log.e("AdManager", "❌ Interstitial FAILED: ${error.code} ${error.message}")
                }
            }
        )
    }

    // ── Call this on user actions (calls made, contacts opened, etc.) ─────
    fun trackAction(context: Context, activity: Activity?) {
        if (!_adsEnabled.value) return
        val config = Firebase.remoteConfig
        if (!config.getBoolean(KEY_INTERSTITIAL_ENABLED)) return

        actionCount++
        val triggerCount = config.getLong(KEY_INTERSTITIAL_TRIGGER).toInt().coerceAtLeast(1)

//        if (actionCount >= triggerCount) {
//            actionCount = 0
//            showInterstitial(activity) {
//                preloadInterstitial(context)
//            }
//        }
        if (actionCount >= triggerCount) {
            actionCount = 0

            // 🚫 Block interstitial if app just resumed (App Open should take priority)
            if (isInterstitialRecentlyActive()) {
                Log.d("AdManager", "Skipping interstitial due to recent app open")
                return
            }

            showInterstitial(activity) {
                preloadInterstitial(context)
            }
        }
    }

    // ── Show interstitial if ready ────────────────────────────────────────
    private fun showInterstitial(activity: Activity?, onDismiss: () -> Unit) {
        if (activity == null || interstitialAd == null) {
            onDismiss()
            return
        }

        isInterstitialShowing = true  // ← ADD THIS

        interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                isInterstitialShowing = false
                interstitialDismissedAt = System.currentTimeMillis()  // ← ADD THIS too
                onDismiss()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                isInterstitialShowing = false
                onDismiss()
            }
        }
        interstitialAd?.show(activity)
    }


    // ── Preload native ad ─────────────────────────────────────────────────
    fun preloadNativeAd(context: Context) {
        if (!_adsEnabled.value || !_nativeEnabled.value) return

        val adUnitId = try {
            Firebase.remoteConfig.getString(KEY_NATIVE_AD_UNIT).ifBlank { TEST_NATIVE_ID }
        } catch (e: Exception) {
            TEST_NATIVE_ID
        }

        Log.d("AdManager", "preloadNativeAd: loading adUnitId=$adUnitId")

        val adLoader = AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                nativeAd?.destroy()
                nativeAd = ad
                _nativeAdReady.value = true
                Log.d("AdManager", "Native ad loaded ✅")
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    nativeAd = null
                    _nativeAdReady.value = false
                    Log.e("AdManager", "Native ad failed ❌ code=${error.code} msg=${error.message}")
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .build()

        adLoader.loadAd(AdRequest.Builder().build())
    }

    // ── Get the loaded native ad (call after nativeAdReady = true) ───────
    fun getNativeAd(): NativeAd? = nativeAd

    // ── Must call when the screen/composable is destroyed ────────────────
    fun destroyNativeAd() {
        nativeAd?.destroy()
        nativeAd = null
        _nativeAdReady.value = false
    }

    fun immediateInterstitialAd(
        activity: Activity?,
        onDismiss: () -> Unit
    ) {
        Log.d("AdManager", "👉 immediateInterstitialAd called")

        if (!_adsEnabled.value) {
            Log.d("AdManager", "❌ Ads disabled")
            onDismiss()
            return
        }

        if (!Firebase.remoteConfig.getBoolean(KEY_INTERSTITIAL_ENABLED)) {
            Log.d("AdManager", "Interstitial disabled by config")
            onDismiss()
            return
        }

        if (activity == null) {
            Log.e("AdManager", "❌ Activity is NULL")
            onDismiss()
            return
        }

        Log.d("AdManager", "📊 Interstitial state: ${interstitialAd != null}")

        // Show loader only if ad not ready
        if (interstitialAd == null) {
            Log.d("AdManager", "⏳ Ad not ready → showing loader")
            _isAdLoading.value = true
        }

        if (interstitialAd != null) {
            Log.d("AdManager", "✅ Ad is READY → showing now")

            isInterstitialShowing = true

            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {

                override fun onAdShowedFullScreenContent() {
                    Log.d("AdManager", "🎬 Ad SHOWED")
                    _isAdLoading.value = false
                }

                override fun onAdDismissedFullScreenContent() {
                    Log.d("AdManager", "❎ Ad DISMISSED")

                    interstitialAd = null
                    isInterstitialShowing = false
                    interstitialDismissedAt = System.currentTimeMillis()

                    _isAdLoading.value = false
                    onDismiss()

                    Log.d("AdManager", "🔄 Reloading next interstitial")
                    preloadInterstitial(activity.applicationContext)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.e("AdManager", "❌ Failed to SHOW ad: ${error.message}")

                    interstitialAd = null
                    isInterstitialShowing = false

                    _isAdLoading.value = false
                    onDismiss()

                    preloadInterstitial(activity.applicationContext)
                }
            }

            Log.d("AdManager", "🚀 Calling show()")
            interstitialAd?.show(activity)

        } else {
            Log.d("AdManager", "⚠️ Ad NOT ready → start loading")

            preloadInterstitial(activity.applicationContext)

            val startTime = System.currentTimeMillis()
            val handler = android.os.Handler(activity.mainLooper)

            val checkRunnable = object : Runnable {
                override fun run() {
                    val elapsed = System.currentTimeMillis() - startTime

                    if (interstitialAd != null) {
                        Log.d("AdManager", "✅ Ad loaded during wait → showing now")

                        _isAdLoading.value = false
                        immediateInterstitialAd(activity, onDismiss)
                        return
                    }

                    if (elapsed > 2500) {
                        Log.d("AdManager", "⌛ Timeout reached → skip ad")

                        _isAdLoading.value = false
                        onDismiss()
                        return
                    }

                    handler.postDelayed(this, 200)
                }
            }

            handler.post(checkRunnable)
        }
    }


    fun showInterstitialOnAppResume(activity: Activity?) {
        if (!_adsEnabled.value) return
        if (activity == null) return

        // Block if already showing something
        if (isInterstitialShowing) return

        if (interstitialAd != null) {
            isInterstitialShowing = true

            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    isInterstitialShowing = false
                    interstitialDismissedAt = System.currentTimeMillis()
                    preloadInterstitial(activity)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    interstitialAd = null
                    isInterstitialShowing = false
                    preloadInterstitial(activity)
                }
            }

            interstitialAd?.show(activity)
        } else {
            preloadInterstitial(activity)
        }
    }
}

@Composable
fun AdLoadingOverlay(isVisible: Boolean) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = "Loading Ad...",
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}



