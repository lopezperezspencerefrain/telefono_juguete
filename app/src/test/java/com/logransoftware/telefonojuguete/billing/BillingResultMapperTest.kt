package com.logransoftware.telefonojuguete.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingResultMapperTest {

  @Test
  fun okResponseWithPurchasedStateGrantsEntitlement() {
    val outcome = classifyPurchaseUpdate(
      responseCode = BillingClient.BillingResponseCode.OK,
      purchaseStates = listOf(Purchase.PurchaseState.PURCHASED)
    )
    assertEquals(PurchaseOutcome.Granted, outcome)
  }

  @Test
  fun okResponseWithPendingStateReturnsPending() {
    val outcome = classifyPurchaseUpdate(
      responseCode = BillingClient.BillingResponseCode.OK,
      purchaseStates = listOf(Purchase.PurchaseState.PENDING)
    )
    assertEquals(PurchaseOutcome.Pending, outcome)
  }

  @Test
  fun userCanceledResponseReturnsCancelledRegardlessOfPurchases() {
    val outcome = classifyPurchaseUpdate(
      responseCode = BillingClient.BillingResponseCode.USER_CANCELED,
      purchaseStates = emptyList()
    )
    assertEquals(PurchaseOutcome.Cancelled, outcome)
  }

  @Test
  fun itemAlreadyOwnedResponseGrantsEntitlement() {
    val outcome = classifyPurchaseUpdate(
      responseCode = BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED,
      purchaseStates = emptyList()
    )
    assertEquals(PurchaseOutcome.Granted, outcome)
  }

  @Test
  fun okResponseWithNoPurchasesReturnsError() {
    val outcome = classifyPurchaseUpdate(
      responseCode = BillingClient.BillingResponseCode.OK,
      purchaseStates = emptyList()
    )
    assertTrue(outcome is PurchaseOutcome.Error)
  }

  @Test
  fun genericErrorResponseCodeReturnsError() {
    val outcome = classifyPurchaseUpdate(
      responseCode = BillingClient.BillingResponseCode.ERROR,
      purchaseStates = emptyList()
    )
    assertTrue(outcome is PurchaseOutcome.Error)
  }
}
