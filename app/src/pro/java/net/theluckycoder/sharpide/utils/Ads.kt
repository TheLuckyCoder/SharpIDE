package net.theluckycoder.sharpide.utils

import net.theluckycoder.sharpide.utils.interfaces.AdsInterface

class Ads(@Suppress("UNUSED_PARAMETER") activity: Any) : AdsInterface {

    override fun loadBanner() = Unit

    override fun loadInterstitial() = Unit

    override fun showInterstitial() = Unit
}
