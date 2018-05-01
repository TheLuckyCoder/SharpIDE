package net.theluckycoder.sharpide.utils

import android.app.Activity
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.InterstitialAd
import com.google.android.gms.ads.MobileAds
import net.theluckycoder.sharpide.BuildConfig
import net.theluckycoder.sharpide.R
import net.theluckycoder.sharpide.utils.extensions.lazyFast

class Ads(private val activity: Activity) {

    private val interstitialAd by lazyFast { InterstitialAd(activity) }

    fun initAds(): Ads {
        MobileAds.initialize(activity, "ca-app-pub-1279472163660969~2916940339")
        return this
    }

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
        val testDevice = if (BuildConfig.DEBUG) "21996E7D39CB4A757CBEF3F4EAB93D6B" else ""
        return AdRequest.Builder()
                .addTestDevice(testDevice)
                .build()
    }
}
