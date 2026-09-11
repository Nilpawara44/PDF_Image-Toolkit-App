package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

const val ADMOB_INTERSTITIAL_ID = "ca-app-pub-2394452298794016/6747996851"
// Kept for debug / test builds
const val ADMOB_TEST_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"

/**
 * Manages pre-loading and displaying AdMob Interstitial Ads.
 * Handles lifecycle callbacks and seamless user dismissal routing.
 */
class InterstitialAdManager(
    private val adUnitId: String = ADMOB_INTERSTITIAL_ID
) {
    private val tag = "InterstitialAdManager"

    private var interstitialAd: InterstitialAd? = null
    private var isLoading: Boolean = false
    private var pendingShowCallback: ((InterstitialAd?) -> Unit)? = null

    /**
     * Pre-loads the Interstitial Ad when the conversion process begins.
     */
    fun preloadAd(context: Context) {
        if (interstitialAd != null) {
            Log.d(tag, "Interstitial Ad is already loaded and ready.")
            return
        }
        if (isLoading) {
            Log.d(tag, "Interstitial Ad is already in the process of loading.")
            return
        }

        isLoading = true
        Log.d(tag, "Starting pre-load of Interstitial Ad (ID: $adUnitId)...")
        val adRequest = AdRequest.Builder().build()

        try {
            InterstitialAd.load(
                context,
                adUnitId,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        isLoading = false
                        interstitialAd = ad
                        Log.d(tag, "Interstitial Ad pre-loaded successfully.")
                        val callback = pendingShowCallback
                        pendingShowCallback = null
                        callback?.invoke(ad)
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        isLoading = false
                        interstitialAd = null
                        Log.w(tag, "Interstitial Ad failed to pre-load: ${loadAdError.message}")
                        val callback = pendingShowCallback
                        pendingShowCallback = null
                        callback?.invoke(null)
                    }
                }
            )
        } catch (e: Exception) {
            isLoading = false
            interstitialAd = null
            Log.w(tag, "Warning during InterstitialAd.load", e)
            val callback = pendingShowCallback
            pendingShowCallback = null
            callback?.invoke(null)
        }
    }

    /**
     * Shows the Interstitial Ad if available or waits briefly if still loading.
     * When the ad is dismissed by the user or fails to display, [onDismissed] is called
     * so the user is seamlessly routed to the final success destination.
     */
    fun showAd(activity: Activity?, onDismissed: () -> Unit) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            Log.w(tag, "Activity is null, finishing, or destroyed. Cannot show interstitial ad. Routing directly to success.")
            onDismissed()
            return
        }

        val ad = interstitialAd
        if (ad != null) {
            displayLoadedAd(ad, activity, onDismissed)
            return
        }

        if (isLoading) {
            Log.d(tag, "Ad is still loading. Waiting briefly for ad to finish loading before showing...")
            val handler = Handler(Looper.getMainLooper())
            var hasTriggered = false

            val timeoutRunnable = Runnable {
                if (!hasTriggered) {
                    hasTriggered = true
                    pendingShowCallback = null
                    Log.d(tag, "Ad loading timeout reached. Proceeding to destination.")
                    onDismissed()
                }
            }

            pendingShowCallback = { loadedAd ->
                handler.removeCallbacks(timeoutRunnable)
                if (!hasTriggered) {
                    hasTriggered = true
                    if (loadedAd != null) {
                        displayLoadedAd(loadedAd, activity, onDismissed)
                    } else {
                        onDismissed()
                    }
                }
            }

            // Allow up to 1.5 seconds if conversion completed faster than ad fetch
            handler.postDelayed(timeoutRunnable, 1500L)
        } else {
            Log.d(tag, "No interstitial ad available. Proceeding directly to success screen.")
            onDismissed()
        }
    }

    private fun displayLoadedAd(ad: InterstitialAd, activity: Activity, onDismissed: () -> Unit) {
        interstitialAd = null
        if (activity.isFinishing || activity.isDestroyed) {
            Log.w(tag, "Activity finished before ad display. Cancelling popup and triggering completion.")
            onDismissed()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(tag, "Interstitial ad dismissed by user. Navigating to success screen.")
                onDismissed()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(tag, "Interstitial ad failed to show: ${adError.message}. Navigating to success screen.")
                onDismissed()
            }

            override fun onAdShowedFullScreenContent() {
                Log.d(tag, "Interstitial ad is now displaying full screen.")
            }
        }
        try {
            ad.show(activity)
        } catch (e: Exception) {
            Log.w(tag, "Warning displaying interstitial ad in emulator", e)
            onDismissed()
        }
    }
}

/**
 * Helper to safely extract Activity from a given Context.
 */
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
