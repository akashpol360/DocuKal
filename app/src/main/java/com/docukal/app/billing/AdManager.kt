package com.docukal.app.billing

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.LoadAdError

/**
 * Interstitial ads are shown only at the explicit save points requested by the
 * product requirement. Premium users never call this manager.
 *
 * This source package currently uses Google's official test interstitial unit.
 * Replace AD_UNIT_ID with your production AdMob interstitial unit before publishing.
 */
object AdManager {
    private const val AD_UNIT_ID = "ca-app-pub-1069994218806167/6563716915"

    @Volatile
    private var interstitial: InterstitialAd? = null

    fun initialize(context: Context) {
        MobileAds.initialize(context.applicationContext)
        load(context.applicationContext)
    }

    fun load(context: Context) {
        if (interstitial != null) return
        InterstitialAd.load(
            context,
            AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                }
            }
        )
    }

    fun show(activity: Activity, onFinished: () -> Unit = {}) {
        val ad = interstitial
        if (ad == null) {
            load(activity.applicationContext)
            onFinished()
            return
        }

        interstitial = null
        ad.fullScreenContentCallback = object :
            com.google.android.gms.ads.FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                load(activity.applicationContext)
                onFinished()
            }

            override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
                load(activity.applicationContext)
                onFinished()
            }
        }
        ad.show(activity)
    }
}
