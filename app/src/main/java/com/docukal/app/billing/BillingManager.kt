package com.docukal.app.billing

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Handles the Google Play one-time Premium upgrade.
 *
 * Configure the product id "docukal_premium" as an INAPP product in Play
 * Console before publishing. Premium is verified from the Play purchase list
 * each time the billing connection is established, so a stale local flag is
 * not enough to keep ads disabled.
 */
class BillingManager(context: Context) {
    companion object {
        const val PREMIUM_PRODUCT_ID = "docukal_premium"
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("docukal_premium", Context.MODE_PRIVATE)
    private val premiumMutable = mutableStateOf(
        prefs.getBoolean("premium_unlocked", false)
    )
    val premium: State<Boolean> = premiumMutable

    private fun setPremium(value: Boolean) {
        premiumMutable.value = value
        prefs.edit().putBoolean("premium_unlocked", value).apply()
    }

    private var productDetails: ProductDetails? = null

    private val listener = object : BillingClientStateListener {
        override fun onBillingSetupFinished(result: BillingResult) {
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                queryPremiumProduct()
                queryExistingPurchase()
            }
        }

        override fun onBillingServiceDisconnected() {
            // BillingClient will reconnect automatically when supported.
        }
    }

    private val purchasesListener = object : PurchasesUpdatedListener {
        override fun onPurchasesUpdated(
            result: BillingResult,
            purchases: MutableList<Purchase>?
        ) {
            if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                purchases.forEach { handlePurchase(it) }
            }
        }
    }

    private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun startConnection() {
        if (!billingClient.isReady) billingClient.startConnection(listener)
    }

    fun endConnection() {
        billingClient.endConnection()
    }

    fun launchPremiumPurchase(activity: Activity, onUnavailable: () -> Unit = {}) {
        if (!billingClient.isReady) {
            startConnection()
            onUnavailable()
            return
        }

        val details = productDetails ?: run {
            queryPremiumProduct()
            onUnavailable()
            return
        }

        val offerBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        details.oneTimePurchaseOfferDetailsList
            ?.firstOrNull()
            ?.offerToken
            ?.takeIf { it.isNotBlank() }
            ?.let { offerBuilder.setOfferToken(it) }

        val offer = offerBuilder.build()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(offer))
            .build()

        billingClient.launchBillingFlow(activity, params)
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
            QueryProductDetailsParams.newBuilder()
                .setProductList(products)
                .build()
        ) { result, detailsResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = detailsResult.productDetailsList.firstOrNull()
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
                val premiumPurchase = purchases.firstOrNull { purchase ->
                    purchase.products.contains(PREMIUM_PRODUCT_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (premiumPurchase != null) {
                    handlePurchase(premiumPurchase)
                } else {
                    setPremium(false)
                }
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(PREMIUM_PRODUCT_ID)) return
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        if (!purchase.isAcknowledged) {
            billingClient.acknowledgePurchase(
                com.android.billingclient.api.AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
            ) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    setPremium(true)
                }
            }
        } else {
            setPremium(true)
        }
    }
}
