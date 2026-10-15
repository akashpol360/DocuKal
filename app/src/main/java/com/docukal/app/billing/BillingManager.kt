package com.docukal.app.billing

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import android.util.Log
import java.lang.ref.WeakReference

/**
 * Handles the Google Play one-time Premium upgrade (INAPP product "docukal_premium").
 *
 * Fixes vs. the released version:
 *  1. First tap no longer silently does nothing: if the connection / product details
 *     are not ready yet, the tap is remembered and the purchase sheet opens as soon
 *     as they are ready (plus a message is shown).
 *  2. ITEM_ALREADY_OWNED (response code 7) is handled: we re-query Play and unlock
 *     Premium instead of leaving the user stuck on the "Upgrade" button.
 *  3. Premium is unlocked as soon as Play reports PURCHASED. Acknowledge is done
 *     afterwards, so a failed acknowledge can no longer leave a paid user on the free plan.
 *  4. refreshPurchases() lets the app re-sync on every resume (e.g. after the user
 *     switched Google account in the Play Store).
 */
class BillingManager(context: Context) {
    companion object {
        const val PREMIUM_PRODUCT_ID = "docukal_premium"
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("docukal_premium", Context.MODE_PRIVATE)
    private val premiumMutable = mutableStateOf(prefs.getBoolean("premium_unlocked", false))
    val premium: State<Boolean> = premiumMutable

    private var productDetails: ProductDetails? = null

    // Set when the user tapped Upgrade before billing was ready.
    private var pendingActivity: WeakReference<Activity>? = null
    private var pendingMessage: ((String) -> Unit)? = null

    private fun setPremium(value: Boolean) {
        premiumMutable.value = value
        prefs.edit().putBoolean("premium_unlocked", value).apply()
    }

    private val stateListener = object : BillingClientStateListener {
        override fun onBillingSetupFinished(result: BillingResult) {
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                queryExistingPurchase()
                queryPremiumProduct()
            } else {
                failPending("Google Play Billing is unavailable right now. Please try again.")
            }
        }

        override fun onBillingServiceDisconnected() {
            // enableAutoServiceReconnection() reconnects automatically.
        }
    }

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                purchases?.forEach { handlePurchase(it) }

            // User already owns it on this Play account -> just re-sync and unlock.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                queryExistingPurchase()

            BillingClient.BillingResponseCode.USER_CANCELED -> Unit

            else -> pendingMessage?.invoke("Purchase failed (code ${result.responseCode}). Please try again.")
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun startConnection() {
        if (!billingClient.isReady) billingClient.startConnection(stateListener)
    }

    fun endConnection() {
        billingClient.endConnection()
    }

    /** Call from ON_RESUME so ownership is re-checked (account switch, refund, restore). */
    fun refreshPurchases() {
        if (billingClient.isReady) queryExistingPurchase() else startConnection()
    }

    fun launchPremiumPurchase(activity: Activity, onMessage: (String) -> Unit = {}) {
        pendingMessage = onMessage

        if (premiumMutable.value) {
            onMessage("Premium is already active.")
            return
        }

        if (!billingClient.isReady) {
            pendingActivity = WeakReference(activity)
            onMessage("Connecting to Google Play… please wait a moment.")
            startConnection()
            return
        }

        val details = productDetails
        if (details == null) {
            pendingActivity = WeakReference(activity)
            onMessage("Loading Premium details… please wait a moment.")
            queryPremiumProduct()
            return
        }

        pendingActivity = null
        launchFlow(activity, details)
    }

    private fun launchFlow(activity: Activity, details: ProductDetails) {
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        details.oneTimePurchaseOfferDetailsList
            ?.firstOrNull()
            ?.offerToken
            ?.takeIf { it.isNotBlank() }
            ?.let { productParams.setOfferToken(it) }

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams.build()))
            .build()

        val result = billingClient.launchBillingFlow(activity, params)
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryExistingPurchase()
            else -> pendingMessage?.invoke("Could not open Google Play (code ${result.responseCode}).")
        }
    }

    private fun failPending(message: String) {
        if (pendingActivity != null) {
            pendingActivity = null
            pendingMessage?.invoke(message)
        }
    }

    private fun queryPremiumProduct() {
        if (!billingClient.isReady) return
        val products = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PREMIUM_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        billingClient.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder().setProductList(products).build()
        ) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = detailsResult.productDetailsList.firstOrNull()
            }
            val reason = "code=${result.responseCode} (${result.debugMessage}), " +
                "found=${detailsResult.productDetailsList.size}, " +
                "unfetched=${detailsResult.unfetchedProductList.size}"
            Log.d("BillingManager", "queryProductDetails: $reason")

            // If the user already tapped Upgrade, open the purchase sheet now.
            val activity = pendingActivity?.get()
            val details = productDetails
            if (activity != null && details != null && !premiumMutable.value) {
                pendingActivity = null
                launchFlow(activity, details)
            } else if (pendingActivity != null && details == null) {
                failPending("Premium product not available from Google Play. $reason")
            }
        }
    }

    private fun queryExistingPurchase() {
        if (!billingClient.isReady) return
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val owned = purchases.firstOrNull {
                    it.products.contains(PREMIUM_PRODUCT_ID) &&
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (owned != null) handlePurchase(owned) else setPremium(false)
            }
            // On error: keep the current state; don't revoke Premium because of a network blip.
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(PREMIUM_PRODUCT_ID)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        // Unlock first, acknowledge second.
        setPremium(true)

        if (!purchase.isAcknowledged) {
            billingClient.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            ) { /* If this fails, it is retried on the next refreshPurchases(). */ }
        }
    }
}
