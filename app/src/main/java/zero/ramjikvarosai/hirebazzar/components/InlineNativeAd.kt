package zero.ramjikvarosai.hirebazzar.components

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import java.util.concurrent.atomic.AtomicBoolean
import zero.ramjikvarosai.hirebazzar.R
import zero.ramjikvarosai.hirebazzar.utils.AdManager

@Composable
fun InlineNativeAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adsEnabled by AdManager.adsEnabled.collectAsState()
    val nativeEnabled by AdManager.nativeEnabled.collectAsState()
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    val disposed = remember { AtomicBoolean(false) }

    LaunchedEffect(adsEnabled, nativeEnabled) {
        if (!adsEnabled || !nativeEnabled || nativeAd != null) return@LaunchedEffect
        AdLoader.Builder(context, AdManager.getAppInstallNativeAdUnitId())
            .forNativeAd { ad ->
                if (disposed.get()) ad.destroy() else nativeAd = ad
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("InlineNativeAd", "Native ad failed: ${error.code} ${error.message}")
                }
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    AnimatedVisibility(visible = nativeAd != null, enter = fadeIn(), exit = fadeOut()) {
        AndroidView(
            modifier = modifier.fillMaxWidth(),
            factory = { viewContext ->
                (LayoutInflater.from(viewContext).inflate(R.layout.inline_native_ad, null) as NativeAdView).apply {
                    headlineView = findViewById(R.id.ad_headline)
                    bodyView = findViewById(R.id.ad_body)
                    iconView = findViewById(R.id.ad_app_icon)
                    callToActionView = findViewById(R.id.ad_call_to_action)
                }
            },
            update = { adView ->
                val ad = nativeAd ?: return@AndroidView
                (adView.headlineView as TextView).text = ad.headline

                (adView.bodyView as TextView).apply {
                    text = ad.body
                    visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
                }
                (adView.iconView as ImageView).apply {
                    setImageDrawable(ad.icon?.drawable)
                    visibility = if (ad.icon == null) View.GONE else View.VISIBLE
                }
                (adView.callToActionView as Button).apply {
                    text = ad.callToAction
                    visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
                }
                adView.setNativeAd(ad)
            },
            onRelease = { it.destroy() }
        )
    }
}
