package zero.ramjikvarosai.hirebazzar.components

import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.NativeAdView
import zero.ramjikvarosai.hirebazzar.utils.AdManager
import zero.ramjikvarosai.hirebazzar.R

@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val nativeAdReady by AdManager.nativeAdReady.collectAsState()

    // Preload when composable first enters composition
    LaunchedEffect(Unit) {
        if (!nativeAdReady) {
            AdManager.preloadNativeAd(context)
        }
    }

    // Destroy ad when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            AdManager.destroyNativeAd()
        }
    }

    AnimatedVisibility(
        visible = nativeAdReady,
        enter = fadeIn(),
        exit  = fadeOut()
    ) {
        AndroidView(
            modifier = modifier
                .fillMaxWidth(),
            factory = { ctx ->
                val adView = LayoutInflater.from(ctx)
                    .inflate(R.layout.native_ad_card, null) as NativeAdView

                // Wire up all the views to the NativeAdView
                adView.headlineView    = adView.findViewById(R.id.ad_headline)
                adView.bodyView        = adView.findViewById(R.id.ad_body)
                adView.iconView        = adView.findViewById(R.id.ad_app_icon)
                adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)

                adView
            },
            update = { adView ->
                val ad = AdManager.getNativeAd() ?: return@AndroidView

                // Populate each view with ad content
                (adView.headlineView as? TextView)?.text = ad.headline
                (adView.bodyView    as? TextView)?.text = ad.body
                (adView.callToActionView as? Button)?.text = ad.callToAction

                ad.icon?.drawable?.let { icon ->
                    (adView.iconView as? ImageView)?.setImageDrawable(icon)
                }

                // CRITICAL: must call this to register all views for click tracking
                adView.setNativeAd(ad)
            }
        )
    }
}