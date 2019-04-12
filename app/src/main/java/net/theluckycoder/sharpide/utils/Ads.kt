package net.theluckycoder.sharpide.utils

import android.app.Activity
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.InterstitialAd
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.extensions.lazyFast
import net.theluckycoder.sharpide.utils.interfaces.AdsInterface

class Ads(private val activity: Activity) : AdsInterface {

    private val interstitialAd by lazyFast { InterstitialAd(activity) }

    override fun loadBanner() {
        activity.findViewById<AdView>(R.id.adView).loadAd(newAdRequest())
    }

    override fun loadInterstitial() {
        interstitialAd.adUnitId = "ca-app-pub-1279472163660969/4828330332"
        interstitialAd.adListener = object : AdListener() {
            override fun onAdClosed() {
                interstitialAd.loadAd(newAdRequest())
            }
        }
        interstitialAd.loadAd(newAdRequest())
    }

    override fun showInterstitial() {
        try {
            if (interstitialAd.isLoaded) interstitialAd.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun newAdRequest(): AdRequest = AdRequest.Builder().build()
}
