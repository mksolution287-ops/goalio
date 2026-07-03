package zero.ramjikvarosai.hirebazzar.components

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import zero.ramjikvarosai.hirebazzar.utils.AdManager
import zero.ramjikvarosai.hirebazzar.R

@Composable
fun AppInstallNativeAdCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val adsEnabled by AdManager.adsEnabled.collectAsState()
    val nativeEnabled by AdManager.nativeEnabled.collectAsState()

    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var adReady by remember { mutableStateOf(false) }

    if (!adsEnabled || !nativeEnabled) return

    LaunchedEffect(Unit) {

        val adUnitId = AdManager.getAppInstallNativeAdUnitId()

        AdLoader.Builder(context, adUnitId)
            .forNativeAd { ad ->
                nativeAd?.destroy()
                nativeAd = ad
                adReady = true
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    adReady = false
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
                    .build()
            )
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    DisposableEffect(Unit) {
        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    AnimatedVisibility(
        visible = adReady,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        AndroidView(
            modifier = modifier.fillMaxWidth(),
            factory = { ctx ->
                val adView = LayoutInflater.from(ctx)
                    .inflate(R.layout.native_app_install_ad_view, null) as NativeAdView

                adView.mediaView        = adView.findViewById(R.id.ad_media)
                adView.headlineView     = adView.findViewById(R.id.ad_headline)
                adView.bodyView         = adView.findViewById(R.id.ad_body)
                adView.iconView         = adView.findViewById(R.id.ad_app_icon)
                adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
                adView.storeView        = adView.findViewById(R.id.ad_store)
                adView.priceView        = adView.findViewById(R.id.ad_price)
                adView.starRatingView   = adView.findViewById(R.id.ad_stars)

                adView
            },
            update = { adView ->
                val ad = nativeAd ?: return@AndroidView

                // Media
                adView.mediaView?.mediaContent = ad.mediaContent

                // Text fields
                (adView.headlineView as? TextView)?.text = ad.headline
                (adView.bodyView as? TextView)?.text = ad.body
                (adView.callToActionView as? Button)?.text = ad.callToAction

                // Icon
                ad.icon?.drawable?.let {
                    (adView.iconView as? ImageView)?.setImageDrawable(it)
                }

                // Store
                val storeView = adView.storeView as? TextView
                if (ad.store != null) {
                    storeView?.text = ad.store
                    storeView?.visibility = View.VISIBLE
                } else {
                    storeView?.visibility = View.GONE
                }

                // Price
                val priceView = adView.priceView as? TextView
                if (ad.price != null) {
                    priceView?.text = ad.price
                    priceView?.visibility = View.VISIBLE
                } else {
                    priceView?.visibility = View.GONE
                }

                // Stars + meta row visibility
                val metaRow = adView.findViewById<LinearLayout>(R.id.ad_meta_row)
                val starsView = adView.starRatingView as? RatingBar
                if (ad.starRating != null) {
                    starsView?.rating = ad.starRating!!.toFloat()
                    metaRow?.visibility = View.VISIBLE
                } else if (ad.store != null || ad.price != null) {
                    metaRow?.visibility = View.VISIBLE
                } else {
                    metaRow?.visibility = View.GONE
                }

                // Required — registers all views for click tracking
                adView.setNativeAd(ad)
            }
        )
    }
}