package com.logransoftware.telefonojuguete.billing

import android.app.Activity
import android.content.Context
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

class BillingManager(
  context: Context,
  private val productId: String,
  private val listener: Listener
) {
  interface Listener {
    fun onPriceLoaded(formattedPrice: String)
    fun onEntitlementGranted(isRestore: Boolean)
    fun onPurchasePending()
    fun onPurchaseCancelled()
    fun onPurchaseError(message: String)
  }

  private val appContext = context.applicationContext
  private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  // Written from Billing Library callbacks (main thread) and read from MainActivity's
  // @JavascriptInterface methods, which run on the WebView's JS thread.
  @Volatile private var cachedProductDetails: ProductDetails? = null
  @Volatile private var cachedFormattedPrice: String? = null

  private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
    handlePurchasesUpdated(billingResult, purchases)
  }

  private val billingClient: BillingClient = BillingClient.newBuilder(appContext)
    .setListener(purchasesUpdatedListener)
    .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
    .enableAutoServiceReconnection()
    .build()

  fun connect() {
    billingClient.startConnection(object : BillingClientStateListener {
      override fun onBillingSetupFinished(billingResult: BillingResult) {
        val connected = billingResult.responseCode == BillingClient.BillingResponseCode.OK
        if (connected) {
          queryProductDetails()
          restorePurchases()
        }
      }

      override fun onBillingServiceDisconnected() {
        // enableAutoServiceReconnection() handles re-establishing the connection; readiness is
        // read live from billingClient.isReady rather than tracked here.
      }
    })
  }

  private fun queryProductDetails() {
    val params = QueryProductDetailsParams.newBuilder()
      .setProductList(
        listOf(
          QueryProductDetailsParams.Product.newBuilder()
            .setProductId(productId)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        )
      )
      .build()

    billingClient.queryProductDetailsAsync(params) { billingResult, result ->
      val details = if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
        result.productDetailsList.firstOrNull { it.productId == productId }
      } else {
        null
      }
      val price = details?.oneTimePurchaseOfferDetails?.formattedPrice
      if (details != null && price != null) {
        cachedProductDetails = details
        cachedFormattedPrice = price
        listener.onPriceLoaded(price)
      } else {
        android.util.Log.e(
          TAG,
          "Failed to load product details for $productId: responseCode=${billingResult.responseCode}"
        )
      }
    }
  }

  private fun restorePurchases() {
    val params = QueryPurchasesParams.newBuilder()
      .setProductType(BillingClient.ProductType.INAPP)
      .build()

    billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
      if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
        val owned = purchases.firstOrNull {
          it.products.contains(productId) && it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        if (owned != null) {
          acknowledgeIfNeeded(owned)
          if (!isEntitled()) {
            setEntitled(true)
            listener.onEntitlementGranted(isRestore = true)
          }
        }
      }
    }
  }

  fun launchPurchase(activity: Activity) {
    // isReady() is the single readiness check; the local read of cachedProductDetails only
    // exists so the compiler can smart-cast it to non-null below.
    val details = cachedProductDetails
    if (!isReady() || details == null) {
      listener.onPurchaseError("Billing not ready")
      return
    }
    val flowParams = BillingFlowParams.newBuilder()
      .setProductDetailsParamsList(
        listOf(
          BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        )
      )
      .build()
    billingClient.launchBillingFlow(activity, flowParams)
  }

  private fun handlePurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
    // Only this manager's product is our concern; any other SKU in the update belongs to
    // whoever owns it and must not be acknowledged or turned into a "remove ads" entitlement.
    val ourPurchases = purchases?.filter { it.products.contains(productId) } ?: emptyList()
    // An OK update that carried purchases, none of them ours, is not this manager's business
    // at all — ignore it rather than reporting it as a missing-purchase-data error.
    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK &&
      !purchases.isNullOrEmpty() && ourPurchases.isEmpty()
    ) {
      return
    }
    val purchaseStates = ourPurchases.map { it.purchaseState }
    when (val outcome = classifyPurchaseUpdate(billingResult.responseCode, purchaseStates)) {
      PurchaseOutcome.Granted -> {
        ourPurchases.forEach { acknowledgeIfNeeded(it) }
        setEntitled(true)
        listener.onEntitlementGranted(isRestore = false)
      }
      PurchaseOutcome.Pending -> listener.onPurchasePending()
      PurchaseOutcome.Cancelled -> listener.onPurchaseCancelled()
      is PurchaseOutcome.Error -> listener.onPurchaseError(outcome.message)
    }
  }

  private fun acknowledgeIfNeeded(purchase: Purchase) {
    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
      val ackParams = AcknowledgePurchaseParams.newBuilder()
        .setPurchaseToken(purchase.purchaseToken)
        .build()
      // Play auto-refunds a purchase that stays unacknowledged for 3 days. There is no retry
      // from Play here: our safety net is restorePurchases(), which re-attempts acknowledgment
      // on the next connect() / app launch if this call fails.
      billingClient.acknowledgePurchase(ackParams) { billingResult ->
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
          android.util.Log.e(TAG, "acknowledgePurchase failed: ${billingResult.responseCode}")
        }
      }
    }
  }

  fun isEntitled(): Boolean = prefs.getBoolean(KEY_ENTITLED, false)

  private fun setEntitled(value: Boolean) {
    prefs.edit().putBoolean(KEY_ENTITLED, value).apply()
  }

  fun cachedPriceOrDefault(): String = cachedFormattedPrice ?: DEFAULT_PRICE

  // billingClient.isReady reflects the library's live connection state, including after
  // enableAutoServiceReconnection() has silently re-established it without a listener callback.
  fun isReady(): Boolean = billingClient.isReady && cachedProductDetails != null

  fun grantDebugEntitlement() {
    setEntitled(true)
  }

  /** Unbinds from the Play Store service. Call from the owning Activity's onDestroy(). */
  fun release() {
    billingClient.endConnection()
  }

  companion object {
    private const val TAG = "BillingManager"
    private const val PREFS_NAME = "billing_prefs"
    private const val KEY_ENTITLED = "remove_ads_entitled"
    private const val DEFAULT_PRICE = "\$1.00 USD"
  }
}
