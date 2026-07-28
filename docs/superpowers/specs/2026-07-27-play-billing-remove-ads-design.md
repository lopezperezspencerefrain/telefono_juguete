# Real "Remove Ads" purchase via Google Play Billing

Date: 2026-07-27
Status: Approved for planning

## Problem

The app has a "Apoyar con $1 USD y Quitar Anuncios" button (`app/src/main/assets/www/index.html:249`) wired to `simulatePremiumPurchase()` (`app/src/main/assets/www/app.js:854`), which just sets `isPremium = true` in `localStorage` with no real payment. We need a real one-time purchase, processed through Google Play, that permanently removes the app's only ad (the interstitial shown in `MainActivity.kt`).

## Confirmed existing behavior (no change needed)

The `isPremium` flag already fully gates the app's only ad: `app.js:823` skips `showGoogleVideoAd()` and closes the app directly when `isPremium` is true. Once a real purchase sets this flag, ads are removed completely and permanently. This spec is only about replacing the fake purchase with a real one and making the flag trustworthy across reinstalls.

## Goals

- Real $1 non-consumable purchase via Google Play Billing Library, product ID `remove_ads`.
- Purchase auto-restores on app launch (reinstall / new device, same Google account) before any ad could show.
- Button shows the live Play Store price instead of a hardcoded "$1 USD" string.
- Debug builds keep a fallback fake-purchase path (compiled out of release) so UI work doesn't require full Play Console setup.
- Graceful handling of cancel / pending / error / already-owned / billing-unavailable — no crashes.

## Non-goals

- No backend / server-side purchase verification (Approach C, rejected as disproportionate for a $1 purchase in this app).
- No third-party billing SDK (RevenueCat etc. — rejected as overkill for one SKU).
- No changes to ad display logic itself (already correct).

## Architecture

**New: `BillingManager` (Kotlin class, owned by `MainActivity`)**
Wraps a `BillingClient` (`com.android.billingclient:billing-ktx`). Responsibilities:
- Connect to Play Billing on app start.
- Query `ProductDetails` for `remove_ads` (for live price).
- Launch the purchase flow on request.
- Handle `PurchasesUpdatedListener` callbacks: acknowledge successful purchases (required by Play within 3 days or the purchase auto-refunds), persist entitlement natively (SharedPreferences), and report outcome back to `MainActivity`.
- On every app start, call `queryPurchasesAsync` for `INAPP` products and restore entitlement if an acknowledged `remove_ads` purchase exists.

**New JS↔Kotlin bridge methods** (same `@JavascriptInterface` pattern as `showGoogleVideoAd`, `startKioskMode`):
- `AndroidApp.getRemoveAdsPrice()` → returns the live formatted price string from `ProductDetails` (e.g. `"$1.00"`), or a safe fallback string if not yet loaded.
- `AndroidApp.purchaseRemoveAds()` → triggers `BillingManager.launchPurchase()`.
- Kotlin → JS callbacks via `evaluateJavascript(...)`:
  - `onRemoveAdsPurchased()` — success or already-owned.
  - `onRemoveAdsPurchasePending()` — payment method needs time to clear (e.g. some carrier billing).
  - `onRemoveAdsPurchaseFailed(reason)` — genuine error; user-cancel is NOT reported as an error (silently returns to idle button state).
  - `onRemoveAdsEntitlementRestored()` — called on app start if restore finds an existing purchase, before the user could see an ad.

**Entitlement source of truth:** Kotlin-side (SharedPreferences, set by `BillingManager` from real Play purchase state) is authoritative. On every launch, Kotlin pushes the correct value into JS via the restore callback, so `localStorage`'s `isPremium` self-heals on reinstall rather than being the only source of truth.

## Data flow

**Purchase:**
1. Tap → `AndroidApp.purchaseRemoveAds()` → `BillingManager.launchPurchase()` → Play's native payment sheet (Play handles all payment details; app never sees card/payment info).
2. `PurchasesUpdatedListener` result:
   - **Success / already owned:** acknowledge if needed → persist entitlement → `onRemoveAdsPurchased()` → JS sets `isPremium = true`, shows existing `premium-success-screen`.
   - **User cancelled:** no error surfaced; button returns to idle, no dialog.
   - **Pending** (e.g. slow payment methods in some regions): `onRemoveAdsPurchasePending()` → JS shows a lightweight "Payment processing…" state; entitlement granted later when the pending purchase resolves (via listener or next-launch restore check).
   - **Error** (network/billing failure): `onRemoveAdsPurchaseFailed(reason)` → JS shows "Couldn't complete purchase, try again," no crash.

**Restore (every app start):** `queryPurchasesAsync(INAPP)` → if an acknowledged `remove_ads` purchase exists, persist entitlement and call `onRemoveAdsEntitlementRestored()` before any ad-eligible flow can run.

**Billing unavailable** (e.g. no Play Store services on device): button stays visible; tapping shows "Purchases unavailable on this device" via the existing failure callback path, no crash.

## Debug fallback

`BuildConfig.DEBUG` gates a fallback: in debug builds only, if Billing setup isn't available/configured, `purchaseRemoveAds()` falls back to today's instant-simulate behavior. Compiled out of release builds entirely — release builds only ever use the real Billing path.

## Manual setup required (outside this repo, user-owned)

Code changes alone cannot make real purchases work. Before the real path can be tested end-to-end:
1. Google Play Developer account (one-time $25 fee, if not already set up).
2. Create the app listing in Play Console; upload a signed build to at least the **Internal testing** track.
3. Play Console → **Monetize → Products → In-app products** → create product ID **`remove_ads`**, one-time (non-consumable), price ~$1 (or per-market pricing Google computes).
4. Play Console → Setup → **License testing** → add own Google account as a license tester, to make real test purchases without being charged.
5. Install via the internal testing opt-in link (not a sideloaded debug APK) to exercise the live purchase flow.

## Testing plan

- Unit/manual: debug fallback path exercised via existing manual flow (no Play Console needed).
- Once steps 1-5 above are done: real sandbox purchase as a license tester, verify success/cancel/already-owned paths, verify restore-on-reinstall by uninstalling and reinstalling the internal-testing build.
- Verify ad truly never shows post-purchase (existing `isPremium` gate at `app.js:823`, already correct).

## Open risk (accepted)

Entitlement enforcement is fully client-side (no backend verification). A rooted/tampered device could locally fake the entitlement flag. Accepted as disproportionate to defend against for a $1 purchase in a kids' toy app (see Approach C rejection above).
