package org.fossify.filemanager.helpers

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import org.fossify.filemanager.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

object AdManager {
    private const val TAG = "LuminaAdManager"
    private const val INTERSTITIAL_COOLDOWN_MS = 90_000L
    private const val INITIAL_GRACE_PERIOD_MS = 25_000L

    private val isMobileAdsInitialized = AtomicBoolean(false)
    private var appStartElapsedMs: Long = SystemClock.elapsedRealtime()
    private var lastInterstitialShownMs: Long = 0L
    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false

    @Volatile
    var isPrivacyOptionsRequired: Boolean = false
        private set

    fun initializeWithConsent(activity: Activity, onReady: (() -> Unit)? = null) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form warning: ${formError.errorCode} ${formError.message}")
                    }
                    isPrivacyOptionsRequired =
                        consentInformation.privacyOptionsRequirementStatus ==
                            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

                    if (consentInformation.canRequestAds()) {
                        initializeMobileAdsSdk(activity.applicationContext, onReady)
                    } else {
                        onReady?.invoke()
                    }
                }
            },
            { requestError ->
                Log.w(TAG, "Consent info update error: ${requestError.errorCode} ${requestError.message}")
                isPrivacyOptionsRequired =
                    consentInformation.privacyOptionsRequirementStatus ==
                        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                if (consentInformation.canRequestAds()) {
                    initializeMobileAdsSdk(activity.applicationContext, onReady)
                } else {
                    // Fallback initialization when offline or consent endpoint unavailable
                    initializeMobileAdsSdk(activity.applicationContext, onReady)
                }
            }
        )

        if (consentInformation.canRequestAds()) {
            initializeMobileAdsSdk(activity.applicationContext, onReady)
        }
    }

    private fun initializeMobileAdsSdk(appContext: Context, onReady: (() -> Unit)? = null) {
        if (isMobileAdsInitialized.getAndSet(true)) {
            onReady?.invoke()
            return
        }
        Thread {
            try {
                MobileAds.initialize(appContext) {
                    Log.d(TAG, "Google Mobile Ads SDK initialized.")
                    preloadInterstitial(appContext)
                    onReady?.invoke()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize MobileAds", e)
                isMobileAdsInitialized.set(false)
                onReady?.invoke()
            }
        }.start()
    }

    fun showPrivacyOptionsForm(activity: Activity, onDismissed: () -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Privacy options form error: ${formError.message}")
            }
            onDismissed()
        }
    }

    fun loadAdaptiveBanner(activity: Activity, container: FrameLayout): AdView? {
        return try {
            val adSize = getAdaptiveBannerAdSize(activity, container)
            val adView = AdView(activity).apply {
                adUnitId = BuildConfig.ADMOB_BANNER_ID
                setAdSize(adSize)
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        container.visibility = View.VISIBLE
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.w(TAG, "Banner failed to load: ${loadAdError.message}")
                        container.visibility = View.GONE
                    }
                }
            }
            container.removeAllViews()
            container.addView(adView)
            adView.loadAd(AdRequest.Builder().build())
            adView
        } catch (e: Exception) {
            Log.e(TAG, "Error loading adaptive banner", e)
            container.visibility = View.GONE
            null
        }
    }

    private fun getAdaptiveBannerAdSize(activity: Activity, container: FrameLayout): AdSize {
        val displayMetrics: DisplayMetrics = activity.resources.displayMetrics
        var adWidthPixels = container.width.toFloat()
        if (adWidthPixels <= 0f) {
            adWidthPixels = displayMetrics.widthPixels.toFloat()
        }
        val density = displayMetrics.density
        val adWidth = (adWidthPixels / density).toInt().coerceAtLeast(320)
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }

    fun preloadInterstitial(context: Context) {
        if (interstitialAd != null || isLoadingInterstitial) return
        isLoadingInterstitial = true
        val appContext = context.applicationContext
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            appContext,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoadingInterstitial = false
                    Log.d(TAG, "Interstitial ad loaded.")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isLoadingInterstitial = false
                    Log.w(TAG, "Interstitial failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    fun maybeShowInterstitial(activity: Activity, onComplete: () -> Unit) {
        val now = SystemClock.elapsedRealtime()
        val pastGrace = (now - appStartElapsedMs) >= INITIAL_GRACE_PERIOD_MS
        val pastCooldown = (now - lastInterstitialShownMs) >= INTERSTITIAL_COOLDOWN_MS
        val ad = interstitialAd

        if (ad != null && pastGrace && pastCooldown && !activity.isFinishing && !activity.isDestroyed) {
            interstitialAd = null
            lastInterstitialShownMs = now
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    preloadInterstitial(activity.applicationContext)
                    onComplete()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.w(TAG, "Interstitial failed to show: ${adError.message}")
                    preloadInterstitial(activity.applicationContext)
                    onComplete()
                }
            }
            ad.show(activity)
        } else {
            if (interstitialAd == null) {
                preloadInterstitial(activity.applicationContext)
            }
            onComplete()
        }
    }
}
