package com.logransoftware.telefonojuguete.billing

sealed class PurchaseOutcome {
  data object Granted : PurchaseOutcome()
  data object Pending : PurchaseOutcome()
  data object Cancelled : PurchaseOutcome()
  data class Error(val message: String) : PurchaseOutcome()
}
