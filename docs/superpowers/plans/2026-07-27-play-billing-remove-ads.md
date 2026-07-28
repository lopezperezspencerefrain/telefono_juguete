# Play Billing "Remove Ads" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the fake `simulatePremiumPurchase()` flow with a real one-time Google Play Billing purchase (product ID `remove_ads`) that permanently disables the app's interstitial ad, with live Play Store pricing and auto-restore on reinstall.

**Architecture:** A new `BillingManager` class (package `com.example.telefonojuguete.billing`) wraps `BillingClient` from the Play Billing Library, owned by `MainActivity`. It talks to `MainActivity` through a `Listener` interface; `MainActivity` bridges that to the existing WebView via new `@JavascriptInterface` methods and `evaluateJavascript` callbacks, following the exact pattern already used for ads (`showGoogleVideoAd`) and kiosk mode (`startKioskMode`). A small pure function, `classifyPurchaseUpdate`, isolates the branch logic that decides success/pending/cancel/error so it can be unit tested without Android framework or a live Activity.

**Tech Stack:** Kotlin, Android WebView JS bridge, Google Play Billing Library 9.1.0 (`com.android.billingclient:billing-ktx`), JUnit 4 (already in the version catalog, not yet wired into the build).

## Global Constraints

- Play Billing Library version: `9.1.0` (`com.android.billingclient:billing-ktx:9.1.0`), current stable per Android Developers docs as of 2026-07.
- Product ID: `remove_ads`, one-time (`INAPP`) non-consumable — this exact string must match what's created in Play Console.
- No backend / server-side purchase verification (spec Non-goal — entitlement enforcement is client-side only, accepted risk for a $1 purchase).
- No third-party billing SDK (e.g. RevenueCat) — direct Billing Library integration only.
- Debug-only fake-purchase fallback must be gated by `BuildConfig.DEBUG` so it is compiled out of release builds entirely.
- Entitlement source of truth is native (`SharedPreferences`, set by `BillingManager`), pushed into the WebView's `isPremium` flag on every launch — not the other way around.
- Existing ad-gating logic (`app.js:823`, skips `showGoogleVideoAd()` when `isPremium` is true) is already correct and must not change.
- The dead template test file `app/src/test/java/com/example/telefonojuguete/ui/main/MainScreenViewModelTest.kt` currently makes `:app:testDebugUnitTest` fail to compile (it references `DataRepository`/`MainScreenViewModel`/`MainScreen`, none of which exist anywhere in `app/src/main`). It must be removed before any new unit test can run — this is a pre-existing blocker directly in the path of this feature's testing, not unrelated cleanup. The equivalent `app/src/androidTest/...MainScreenTest.kt` file is out of scope (doesn't block unit tests) and must be left untouched.

---

### Task 1: Clear the test-compilation blocker and wire up dependencies

**Files:**
- Delete: `app/src/test/java/com/example/telefonojuguete/ui/main/MainScreenViewModelTest.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: `libs.billing.ktx` and `libs.junit` available to `app/build.gradle.kts`; `testImplementation(libs.junit)` wired so `app/src/test/...` compiles and runs; `buildConfig = true` so later tasks can reference `BuildConfig.DEBUG`.

- [ ] **Step 1: Confirm the test source set is currently broken**

Run: `./gradlew :app:compileDebugUnitTestKotlin --console=plain`
Expected: FAILS with `Unresolved reference` errors pointing at `MainScreenViewModelTest.kt` (references to `DataRepository`, `MainScreenViewModel`, `MainScreenUiState`, none of which exist in `app/src/main`).

- [ ] **Step 2: Delete the dead template test file**

```bash
git rm app/src/test/java/com/example/telefonojuguete/ui/main/MainScreenViewModelTest.kt
```

- [ ] **Step 3: Add the Billing Library version and dependency to the version catalog**

In `gradle/libs.versions.toml`, in the `[versions]` block, add (near the other version entries):

```toml
billing = "9.1.0"
```

In the `[libraries]` block, add:

```toml
billing-ktx = { module = "com.android.billingclient:billing-ktx", version.ref = "billing" }
```

- [ ] **Step 4: Wire the dependency, JUnit, and BuildConfig into the app module**

In `app/build.gradle.kts`, change:

```kotlin
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }
```

to:

```kotlin
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = true
      shaders = false
    }
```

In `app/build.gradle.kts`, change the `dependencies` block from:

```kotlin
dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material3)

  // WebKit
  implementation("androidx.webkit:webkit:1.11.0")

  // Google Mobile Ads (AdMob) SDK
  implementation("com.google.android.gms:play-services-ads:23.0.0")
}
```

to:

```kotlin
dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material3)

  // WebKit
  implementation("androidx.webkit:webkit:1.11.0")

  // Google Mobile Ads (AdMob) SDK
  implementation("com.google.android.gms:play-services-ads:23.0.0")

  // Google Play Billing (real "remove ads" purchase)
  implementation(libs.billing.ktx)

  // Unit testing
  testImplementation(libs.junit)
}
```

- [ ] **Step 5: Verify the test source set now compiles and runs (with zero tests)**

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, no compile errors (no test classes exist yet, so 0 tests run).

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "Remove dead template unit test and add Play Billing + JUnit dependencies"
```

---

### Task 2: TDD the pure purchase-outcome classifier

**Files:**
- Create: `app/src/main/java/com/example/telefonojuguete/billing/PurchaseOutcome.kt`
- Create: `app/src/main/java/com/example/telefonojuguete/billing/BillingResultMapper.kt`
- Test: `app/src/test/java/com/example/telefonojuguete/billing/BillingResultMapperTest.kt`

**Interfaces:**
- Consumes: `com.android.billingclient.api.BillingClient.BillingResponseCode` constants, `com.android.billingclient.api.Purchase.PurchaseState` constants (both from `libs.billing.ktx`, wired in Task 1).
- Produces: `sealed class PurchaseOutcome` with `Granted`, `Pending`, `Cancelled` (objects) and `Error(val message: String)` (data class); top-level `fun classifyPurchaseUpdate(responseCode: Int, purchaseStates: List<Int>): PurchaseOutcome`. Task 3's `BillingManager` calls this directly.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/com/example/telefonojuguete/billing/BillingResultMapperTest.kt`:

```kotlin
package com.example.telefonojuguete.billing

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
```

- [ ] **Step 2: Run the test to verify it fails to compile**

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: FAILS — `Unresolved reference: classifyPurchaseUpdate`, `Unresolved reference: PurchaseOutcome` (neither file exists yet).

- [ ] **Step 3: Create the `PurchaseOutcome` sealed class**

Create `app/src/main/java/com/example/telefonojuguete/billing/PurchaseOutcome.kt`:

```kotlin
package com.example.telefonojuguete.billing

sealed class PurchaseOutcome {
  data object Granted : PurchaseOutcome()
  data object Pending : PurchaseOutcome()
  data object Cancelled : PurchaseOutcome()
  data class Error(val message: String) : PurchaseOutcome()
}
```

- [ ] **Step 4: Implement the classifier**

Create `app/src/main/java/com/example/telefonojuguete/billing/BillingResultMapper.kt`:

```kotlin
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
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, 6 tests passed.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/telefonojuguete/billing/PurchaseOutcome.kt \
        app/src/main/java/com/example/telefonojuguete/billing/BillingResultMapper.kt \
        app/src/test/java/com/example/telefonojuguete/billing/BillingResultMapperTest.kt
git commit -m "Add pure purchase-outcome classifier for Play Billing updates"
```

---

### Task 3: Implement `BillingManager`

**Files:**
- Create: `app/src/main/java/com/example/telefonojuguete/billing/BillingManager.kt`

**Interfaces:**
- Consumes: `classifyPurchaseUpdate`, `PurchaseOutcome` (Task 2); `com.android.billingclient.api.*` (Task 1's dependency).
- Produces (used by Task 4):
  - `class BillingManager(context: Context, productId: String, listener: Listener)`
  - `interface BillingManager.Listener { fun onPriceLoaded(formattedPrice: String); fun onEntitlementGranted(isRestore: Boolean); fun onPurchasePending(); fun onPurchaseCancelled(); fun onPurchaseError(message: String) }`
  - `fun connect()`
  - `fun launchPurchase(activity: Activity)`
  - `fun isEntitled(): Boolean`
  - `fun isReady(): Boolean`
  - `fun cachedPriceOrDefault(): String`
  - `fun grantDebugEntitlement()`

This class is integration glue over `BillingClient` and an `Activity`/`SharedPreferences`, so it isn't unit tested here (would require Robolectric or an instrumented test, disproportionate for this feature per the spec). It's verified by compilation now and by manual runs once wired into `MainActivity` in Task 4.

- [ ] **Step 1: Write `BillingManager.kt`**

Create `app/src/main/java/com/example/telefonojuguete/billing/BillingManager.kt`:

```kotlin
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
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/telefonojuguete/billing/BillingManager.kt
git commit -m "Add BillingManager wrapping Play Billing Library for remove_ads purchase"
```

---

### Task 4: Wire `BillingManager` into `MainActivity` and the JS bridge

**Files:**
- Modify: `app/src/main/java/com/example/telefonojuguete/MainActivity.kt`

**Interfaces:**
- Consumes: `com.example.telefonojuguete.billing.BillingManager` and `BillingManager.Listener` (Task 3).
- Produces (used by Task 5's JS):
  - `@JavascriptInterface fun getRemoveAdsPrice(): String` — synchronous, returns cached or default price string.
  - `@JavascriptInterface fun purchaseRemoveAds()` — triggers the real (or, debug-only, simulated) purchase flow.
  - JS-side callbacks invoked via `evaluateJavascript`: `onRemoveAdsPriceLoaded(priceText)`, `onRemoveAdsPurchased()`, `onRemoveAdsEntitlementRestored()`, `onRemoveAdsPurchasePending()`, `onRemoveAdsPurchaseFailed(reason)` — Task 5 must define all five as global JS functions.

- [ ] **Step 1: Add the import**

In `app/src/main/java/com/example/telefonojuguete/MainActivity.kt`, change:

```kotlin
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
```

to:

```kotlin
import com.example.telefonojuguete.billing.BillingManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
```

- [ ] **Step 2: Add the `billingManager` field and `callJs` helper**

In `app/src/main/java/com/example/telefonojuguete/MainActivity.kt`, change:

```kotlin
  private val testDeviceIds = listOf(
    AdRequest.DEVICE_ID_EMULATOR
    // "PASTE_YOUR_DEVICE_HASHED_ID_HERE",
  )

  private fun loadGoogleAd() {
```

to:

```kotlin
  private val testDeviceIds = listOf(
    AdRequest.DEVICE_ID_EMULATOR
    // "PASTE_YOUR_DEVICE_HASHED_ID_HERE",
  )

  private val billingManager: BillingManager by lazy {
    BillingManager(
      context = this,
      productId = "remove_ads",
      listener = object : BillingManager.Listener {
        override fun onPriceLoaded(formattedPrice: String) {
          callJs("onRemoveAdsPriceLoaded", formattedPrice)
        }

        override fun onEntitlementGranted(isRestore: Boolean) {
          if (isRestore) callJs("onRemoveAdsEntitlementRestored") else callJs("onRemoveAdsPurchased")
        }

        override fun onPurchasePending() {
          callJs("onRemoveAdsPurchasePending")
        }

        override fun onPurchaseCancelled() {
          // User closed the Play payment sheet; nothing to report.
        }

        override fun onPurchaseError(message: String) {
          callJs("onRemoveAdsPurchaseFailed", message)
        }
      }
    )
  }

  // Play Billing callbacks are guaranteed to run on the main thread, so calling
  // evaluateJavascript directly from them (via this helper) is safe.
  private fun callJs(functionName: String, vararg args: String) {
    val encodedArgs = args.joinToString(",") { org.json.JSONObject.quote(it) }
    webView?.evaluateJavascript("$functionName($encodedArgs);", null)
  }

  private fun loadGoogleAd() {
```

- [ ] **Step 3: Add the two JS bridge methods**

In `app/src/main/java/com/example/telefonojuguete/MainActivity.kt`, change:

```kotlin
  @android.webkit.JavascriptInterface
  fun startKioskMode() {
```

to:

```kotlin
  @android.webkit.JavascriptInterface
  fun getRemoveAdsPrice(): String = billingManager.cachedPriceOrDefault()

  @android.webkit.JavascriptInterface
  fun purchaseRemoveAds() {
    runOnUiThread {
      if (billingManager.isEntitled()) {
        callJs("onRemoveAdsPurchased")
        return@runOnUiThread
      }
      if (BuildConfig.DEBUG && !billingManager.isReady()) {
        billingManager.grantDebugEntitlement()
        callJs("onRemoveAdsPurchased")
      } else {
        billingManager.launchPurchase(this@MainActivity)
      }
    }
  }

  @android.webkit.JavascriptInterface
  fun startKioskMode() {
```

- [ ] **Step 4: Connect to Play Billing on app start**

In `app/src/main/java/com/example/telefonojuguete/MainActivity.kt`, change:

```kotlin
    MobileAds.initialize(this) {}
    loadGoogleAd()

    // Hide UI elements safely
    hideSystemUI()
```

to:

```kotlin
    MobileAds.initialize(this) {}
    loadGoogleAd()

    // Connect to Play Billing & restore any existing "remove ads" purchase
    billingManager.connect()

    // Hide UI elements safely
    hideSystemUI()
```

- [ ] **Step 5: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/telefonojuguete/MainActivity.kt
git commit -m "Wire BillingManager into MainActivity with getRemoveAdsPrice/purchaseRemoveAds bridge"
```

---

### Task 5: Replace the fake purchase in the WebView UI

**Files:**
- Modify: `app/src/main/assets/www/app.js`
- Modify: `app/src/main/assets/www/index.html`

**Interfaces:**
- Consumes: `AndroidApp.getRemoveAdsPrice()`, `AndroidApp.purchaseRemoveAds()` (Task 4); calls the five global JS callback functions defined here, which Task 4's Kotlin code invokes by name via `evaluateJavascript`.
- Produces: nothing consumed by later tasks (final UI-facing task).

- [ ] **Step 1: Replace the purchase button's click handler**

In `app/src/main/assets/www/app.js`, change:

```js
  // PREMIUM BUY
  const buyPremiumBtn = document.getElementById('buy-premium-btn');
  buyPremiumBtn.addEventListener('click', () => {
    simulatePremiumPurchase();
  });
```

to:

```js
  // PREMIUM BUY
  const buyPremiumBtn = document.getElementById('buy-premium-btn');
  buyPremiumBtn.addEventListener('click', () => {
    requestRemoveAdsPurchase();
  });
```

- [ ] **Step 2: Fetch the initial price on bootstrap**

In `app/src/main/assets/www/app.js`, change:

```js
  setupEventListeners();
});
```

to:

```js
  setupEventListeners();
  initRemoveAdsPrice();
});
```

- [ ] **Step 3: Replace the simulation function with the real bridge + callbacks**

In `app/src/main/assets/www/app.js`, change:

```js
// PREMIUM PURCHASE SIMULATION
function simulatePremiumPurchase() {
  isPremium = true;
  localStorage.setItem('is_premium', 'true');
  playCallConnectedSound();
  showScreen('premium-success-screen');
}
```

to:

```js
// REMOVE ADS PURCHASE (Google Play Billing)
function requestRemoveAdsPurchase() {
  if (window.AndroidApp && typeof window.AndroidApp.purchaseRemoveAds === 'function') {
    window.AndroidApp.purchaseRemoveAds();
  } else {
    alert('Las compras solo están disponibles en la aplicación instalada. 📱');
  }
}

function initRemoveAdsPrice() {
  if (window.AndroidApp && typeof window.AndroidApp.getRemoveAdsPrice === 'function') {
    onRemoveAdsPriceLoaded(window.AndroidApp.getRemoveAdsPrice());
  }
}

function onRemoveAdsPriceLoaded(priceText) {
  document.querySelectorAll('.remove-ads-price').forEach((el) => {
    el.textContent = priceText;
  });
}

function onRemoveAdsPurchased() {
  isPremium = true;
  localStorage.setItem('is_premium', 'true');
  playCallConnectedSound();
  showScreen('premium-success-screen');
}

function onRemoveAdsEntitlementRestored() {
  isPremium = true;
  localStorage.setItem('is_premium', 'true');
}

function onRemoveAdsPurchasePending() {
  alert('Tu pago se está procesando. Los anuncios se quitarán automáticamente cuando se confirme. ⏳');
}

function onRemoveAdsPurchaseFailed(reason) {
  console.error('Remove ads purchase failed:', reason);
  alert('No se pudo completar la compra. Intenta de nuevo. 😕');
}
```

- [ ] **Step 4: Make the three price displays updatable**

In `app/src/main/assets/www/index.html`, change:

```html
          <button id="show-premium-panel-btn" class="btn btn-premium animate-pulse" style="margin-top: 10px;">⭐ Eliminar Anuncios ($1.00 USD)</button>
```

to:

```html
          <button id="show-premium-panel-btn" class="btn btn-premium animate-pulse" style="margin-top: 10px;">⭐ Eliminar Anuncios (<span class="remove-ads-price">$1.00 USD</span>)</button>
```

In `app/src/main/assets/www/index.html`, change:

```html
          <p class="price">Solo <strong>$1.00 USD</strong></p>
```

to:

```html
          <p class="price">Solo <strong class="remove-ads-price">$1.00 USD</strong></p>
```

In `app/src/main/assets/www/index.html`, change:

```html
        <button id="buy-premium-btn" class="btn btn-premium animate-pulse">Apoyar con $1 USD y Quitar Anuncios ✨</button>
```

to:

```html
        <button id="buy-premium-btn" class="btn btn-premium animate-pulse">Apoyar con <span class="remove-ads-price">$1 USD</span> y Quitar Anuncios ✨</button>
```

- [ ] **Step 5: Build the debug APK**

Run: `./gradlew :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/assets/www/app.js app/src/main/assets/www/index.html
git commit -m "Replace simulated purchase with real Play Billing bridge calls in the WebView UI"
```

---

### Task 6: Manual end-to-end verification

**Files:** none (verification only).

**Interfaces:**
- Consumes: everything from Tasks 1-5.
- Produces: nothing (final task).

- [ ] **Step 1: Full automated check**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL`, all unit tests pass (6 from Task 2).

- [ ] **Step 2: Manual debug-fallback purchase flow (no Play Console needed yet)**

Install the debug build on the emulator, open the app, navigate to the parents panel, tap "⭐ Eliminar Anuncios," then tap "Apoyar con $1 USD y Quitar Anuncios ✨."
Expected: since no `remove_ads` product exists in Play Console yet, `billingManager.isReady()` is false, so the debug fallback grants entitlement instantly and shows the "¡Muchas Gracias!" success screen — matching today's behavior, but now going through the real bridge method instead of the old simulate function.

- [ ] **Step 3: Confirm the ad is gone for the rest of the session**

From the success screen, return to the dial screen and trigger the app-close flow (whatever normally shows the interstitial).
Expected: no ad appears; the app closes directly (existing `app.js:823` gating, unchanged).

- [ ] **Step 4: Confirm persistence across relaunch**

Force-stop the app and relaunch it.
Expected: no ad appears on close this session either — `BillingManager.isEntitled()` reads `true` from `SharedPreferences` set in Step 2, independent of the WebView's `localStorage`.

- [ ] **Step 5: Note remaining manual work outside this repo**

Once ready for real purchases: complete the 5 manual Play Console steps from the spec (`docs/superpowers/specs/2026-07-27-play-billing-remove-ads-design.md`) — Play Developer account, app listing + internal testing upload, `remove_ads` in-app product, license tester, install via the internal testing link — then repeat Steps 2-4 above through an actual (test) purchase instead of the debug fallback.
