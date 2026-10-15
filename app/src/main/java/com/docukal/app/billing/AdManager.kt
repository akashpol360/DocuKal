package com.docukal.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object AdManager {

    private const val TAG = "DocuKalAds"

    // Production AdMob interstitial ad unit ID
    private const val AD_UNIT_ID =
        "ca-app-pub-1069994218806167/6563716915"

    @Volatile
    private var interstitial: InterstitialAd? = null

    fun initialize(context: Context) {
        Log.d(TAG, "Initializing Google Mobile Ads SDK")

        MobileAds.initialize(context.applicationContext) {
            Log.d(TAG, "Google Mobile Ads SDK initialization completed")
        }

        load(context.applicationContext)
    }

    @Synchronized
    fun load(context: Context) {
        if (interstitial != null) {
            Log.d(TAG, "An interstitial ad is already loaded")
            return
        }

        Log.d(TAG, "Requesting an interstitial ad")

        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            context.applicationContext,
            AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {

                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad

                    Log.d(
                        TAG,
                        "Ad loaded successfully. Response info: ${ad.responseInfo}"
                    )
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null

                    Log.e(
                        TAG,
                        "Ad failed to load. Code=${error.code}, " +
                                "Message=${error.message}, " +
                                "Domain=${error.domain}, " +
                                "ResponseInfo=${error.responseInfo}"
                    )
                }
            }
        )
    }

    fun show(
        activity: Activity,
        onFinished: () -> Unit = {}
    ) {
        val ad = interstitial

        if (ad == null) {
            Log.d(TAG, "No cached interstitial ad available")

            load(activity.applicationContext)
            onFinished()
            return
        }

        interstitial = null

        ad.fullScreenContentCallback =
            object : FullScreenContentCallback() {

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad displayed")
                }

                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad dismissed")

                    load(activity.applicationContext)
                    onFinished()
                }

                override fun onAdFailedToShowFullScreenContent(
                    adError: AdError
                ) {
                    Log.e(
                        TAG,
                        "Failed to display interstitial ad: " +
                                "${adError.code} - ${adError.message}"
                    )

                    load(activity.applicationContext)
                    onFinished()
                }
            }

        ad.show(activity)
    }
}