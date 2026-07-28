package com.example.telefonojuguete.billing

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

  @Volatile private var isConnected = false
  private var cachedProductDetails: ProductDetails? = null
  private var cachedFormattedPrice: String? = null

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
        isConnected = billingResult.responseCode == BillingClient.BillingResponseCode.OK
        if (isConnected) {
          queryProductDetails()
          restorePurchases()
        }
      }

      override fun onBillingServiceDisconnected() {
        isConnected = false
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
      if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
        val details = result.productDetailsList.firstOrNull { it.productId == productId }
        val price = details?.oneTimePurchaseOfferDetails?.formattedPrice
        if (details != null && price != null) {
          cachedProductDetails = details
          cachedFormattedPrice = price
          listener.onPriceLoaded(price)
        }
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
    val details = cachedProductDetails
    if (!isConnected || details == null) {
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
    val purchaseStates = purchases?.map { it.purchaseState } ?: emptyList()
    when (val outcome = classifyPurchaseUpdate(billingResult.responseCode, purchaseStates)) {
      PurchaseOutcome.Granted -> {
        purchases?.forEach { acknowledgeIfNeeded(it) }
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
      // Play retries acknowledgment delivery on its own; no local retry needed.
      billingClient.acknowledgePurchase(ackParams) { }
    }
  }

  fun isEntitled(): Boolean = prefs.getBoolean(KEY_ENTITLED, false)

  private fun setEntitled(value: Boolean) {
    prefs.edit().putBoolean(KEY_ENTITLED, value).apply()
  }

  fun cachedPriceOrDefault(): String = cachedFormattedPrice ?: DEFAULT_PRICE

  fun isReady(): Boolean = isConnected && cachedProductDetails != null

  fun grantDebugEntitlement() {
    setEntitled(true)
  }

  companion object {
    private const val PREFS_NAME = "billing_prefs"
    private const val KEY_ENTITLED = "remove_ads_entitled"
    private const val DEFAULT_PRICE = "\$1.00 USD"
  }
}
