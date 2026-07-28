package com.example.telefonojuguete.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase

/**
 * Pure mapping from a Play Billing update to what the UI should do.
 * Kept free of BillingClient/Activity dependencies so it's unit-testable.
 */
fun classifyPurchaseUpdate(responseCode: Int, purchaseStates: List<Int>): PurchaseOutcome {
  return when (responseCode) {
    BillingClient.BillingResponseCode.OK -> when {
      purchaseStates.isEmpty() ->
        PurchaseOutcome.Error("Purchase succeeded but no purchase data was returned")
      purchaseStates.all { it == Purchase.PurchaseState.PURCHASED } -> PurchaseOutcome.Granted
      purchaseStates.any { it == Purchase.PurchaseState.PENDING } -> PurchaseOutcome.Pending
      else -> PurchaseOutcome.Error("Unexpected purchase state")
    }
    BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseOutcome.Cancelled
    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseOutcome.Granted
    else -> PurchaseOutcome.Error("Billing error (code $responseCode)")
  }
}
