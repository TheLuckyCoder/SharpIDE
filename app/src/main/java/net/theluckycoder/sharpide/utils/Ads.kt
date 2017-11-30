package net.theluckycoder.sharpide.utils

import android.app.Activity
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.InterstitialAd
import net.theluckycoder.sharpide.BuildConfig
import net.theluckycoder.sharpide.R


class Ads(private val activity: Activity) {

    private val interstitialAd by lazyFast { InterstitialAd(activity) }

    fun loadBanner() {
        activity.findViewById<AdView>(R.id.adView).loadAd(newAdRequest())
    }

    fun loadInterstitial() {
        interstitialAd.adUnitId = "ca-app-pub-1279472163660969/4828330332"
        interstitialAd.adListener = object : AdListener() {
            override fun onAdClosed() {
                interstitialAd.loadAd(newAdRequest())
            }
        }
        interstitialAd.loadAd(newAdRequest())
    }

    fun showInterstitial() {
        try {
            if (interstitialAd.isLoaded) interstitialAd.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun newAdRequest(): AdRequest {
        val testDevice = if (BuildConfig.DEBUG) "221E5C37FA6D629E99A639B912C683D3" else ""
        return AdRequest.Builder()
                .addTestDevice(testDevice)
                .build()
    }
}
