package com.logransoftware.telefonojuguete

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.delay
import com.logransoftware.telefonojuguete.billing.BillingManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class MainActivity : ComponentActivity() {
  private var webView: WebView? = null
  private var pendingPermissionRequest: PermissionRequest? = null
  private var mInterstitialAd: InterstitialAd? = null

  private val interstitialAdUnitId = "ca-app-pub-8887568673600103/5527936069"

  private var isAdLoading = false
  private var shouldCloseAppOnResume = false

  // Real ad unit ID above, but this device list keeps THIS device receiving clearly-labeled
  // test ads instead of live ones. After running on a physical device, check Logcat (tag "Ad")
  // for a line like: Use RequestConfiguration.Builder().setTestDeviceIds(Arrays.asList("XXXXX"))
  // and add that hashed ID below. Never tap real ads on your own device — it risks an AdMob ban.
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

  // billingManager.connect() runs before the WebView exists and its callbacks are async, so the
  // listener-driven pushes above can fire while webView is still null or the page hasn't defined
  // its JS callbacks yet — and the restore push only fires the first time entitlement is seen.
  // This re-pushes the current state once the page is actually able to receive it. Safe to call
  // before billing has resolved: isEntitled() reads the persisted flag and cachedPriceOrDefault()
  // falls back to the default string. onRemoveAdsEntitlementRestored() is idempotent in app.js.
  private fun syncBillingStateToWebView() {
    if (billingManager.isEntitled()) {
      callJs("onRemoveAdsEntitlementRestored")
    }
    callJs("onRemoveAdsPriceLoaded", billingManager.cachedPriceOrDefault())
  }

  private fun loadGoogleAd() {
    if (mInterstitialAd != null || isAdLoading) return
    isAdLoading = true
    val adRequest = AdRequest.Builder().build()
    InterstitialAd.load(this, interstitialAdUnitId, adRequest, object : InterstitialAdLoadCallback() {
      override fun onAdFailedToLoad(adError: LoadAdError) {
        android.util.Log.e("AdMob", "Error al cargar anuncio de Google: ${adError.message} (Código ${adError.code})")
        mInterstitialAd = null
        isAdLoading = false
      }

      override fun onAdLoaded(interstitialAd: InterstitialAd) {
        android.util.Log.d("AdMob", "Anuncio de Google cargado exitosamente.")
        mInterstitialAd = interstitialAd
        isAdLoading = false
      }
    })
  }

  @android.webkit.JavascriptInterface
  fun showGoogleVideoAd() {
    runOnUiThread {
      shouldCloseAppOnResume = true
      if (mInterstitialAd != null) {
        displayAdNow()
      } else {
        @Suppress("DEPRECATION")
        val progressDialog = android.app.ProgressDialog(this@MainActivity).apply {
          setMessage("Cargando anuncio de Google... ⏳")
          setCancelable(false)
          show()
        }

        loadGoogleAd()

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var checkCount = 0
        val runnable = object : Runnable {
          override fun run() {
            checkCount++
            if (mInterstitialAd != null) {
              try { progressDialog.dismiss() } catch (e: Exception) {}
              displayAdNow()
            } else if (checkCount < 25) { // Esperar hasta 5 segundos
              handler.postDelayed(this, 200)
            } else {
              try { progressDialog.dismiss() } catch (e: Exception) {}
              closeApp()
            }
          }
        }
        handler.post(runnable)
      }
    }
  }

  private fun displayAdNow() {
    shouldCloseAppOnResume = true
    mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
      override fun onAdDismissedFullScreenContent() {
        mInterstitialAd = null
        loadGoogleAd()
        closeApp()
      }

      override fun onAdFailedToShowFullScreenContent(adError: com.google.android.gms.ads.AdError) {
        mInterstitialAd = null
        loadGoogleAd()
        closeApp()
      }
    }
    mInterstitialAd?.show(this@MainActivity)
  }

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
    runOnUiThread {
      try {
        startLockTask()
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  @android.webkit.JavascriptInterface
  fun stopKioskMode() {
    runOnUiThread {
      try {
        stopLockTask()
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  @android.webkit.JavascriptInterface
  fun closeApp() {
    runOnUiThread {
      try {
        stopLockTask()
      } catch (e: Exception) {
        e.printStackTrace()
      }
      try {
        finishAndRemoveTask()
      } catch (e: Exception) {
        e.printStackTrace()
      }
      try {
        finishAffinity()
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  private val requestPermissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { isGranted: Boolean ->
    try {
      if (isGranted) {
        pendingPermissionRequest?.let {
          it.grant(it.resources)
        }
      } else {
        pendingPermissionRequest?.deny()
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    pendingPermissionRequest = null
  }

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Enable Chrome remote debugging for WebViews
    WebView.setWebContentsDebuggingEnabled(true)

    // Initialize Google Mobile Ads SDK & Preload Ad
    MobileAds.setRequestConfiguration(
      RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build()
    )
    MobileAds.initialize(this) {}
    loadGoogleAd()

    // Connect to Play Billing & restore any existing "remove ads" purchase
    billingManager.connect()

    // Hide UI elements safely
    hideSystemUI()

    // Initialize the WebView Asset Loader to serve local assets via secure https:// domain
    val assetLoader = WebViewAssetLoader.Builder()
      .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
      .build()

    setContent {
      // Compose states to handle minimum display time and smooth transition
      var isPageLoaded by remember { mutableStateOf(false) }
      var showSplash by remember { mutableStateOf(true) }

      LaunchedEffect(isPageLoaded) {
        if (isPageLoaded) {
          // Keep splash visible for 1.8 seconds to allow the user to read the logo comfortably
          delay(1800)
          showSplash = false
        }
      }

      BackHandler(enabled = true) {
        try {
          webView?.evaluateJavascript("handleBackGesture();", null)
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }

      Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
          modifier = Modifier.fillMaxSize(),
          factory = { context ->
            WebView(context).apply {
              webView = this
              
              // Set explicit layout params to match parent size
              layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
              )

              // Set background color to black to avoid pink flashes during WebView initialization
              setBackgroundColor(android.graphics.Color.BLACK)

              // Add Javascript interface to allow web code to close the app natively
              addJavascriptInterface(this@MainActivity, "AndroidApp")

              // Configure WebView settings for maximum execution speed & GPU acceleration
              settings.javaScriptEnabled = true
              settings.domStorageEnabled = true
              settings.databaseEnabled = true
              settings.allowFileAccess = true
              settings.allowContentAccess = true
              @Suppress("DEPRECATION")
              settings.setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)
              settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
              settings.offscreenPreRaster = true
              
              webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                  view: WebView,
                  request: WebResourceRequest
                ): WebResourceResponse? {
                  android.util.Log.d("WebViewAssetLoader", "Interceptando URL: ${request.url}")
                  // Intercept the request and route to the local asset handler
                  return assetLoader.shouldInterceptRequest(request.url)
                }

                override fun onPageFinished(view: WebView, url: String) {
                  super.onPageFinished(view, url)
                  android.util.Log.d("WebViewClient", "Página cargada con éxito: $url")
                  
                  // Signal that the page has finished loading
                  isPageLoaded = true

                  // The page can now receive JS calls; push current billing state to it.
                  syncBillingStateToWebView()
                }

                override fun onReceivedError(
                  view: WebView,
                  request: WebResourceRequest,
                  error: android.webkit.WebResourceError
                ) {
                  super.onReceivedError(view, request, error)
                  val msg = "WebView Error: ${error.description} en ${request.url}"
                  android.util.Log.e("WebViewError", msg)
                }
              }

              webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                  val msg = "JS [${consoleMessage.messageLevel()}]: ${consoleMessage.message()} (L${consoleMessage.lineNumber()})"
                  android.util.Log.d("WebViewConsole", msg)
                  return true
                }

                override fun onPermissionRequest(request: PermissionRequest) {
                  try {
                    val resources = request.resources
                    for (resource in resources) {
                      if (resource == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                        if (ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.RECORD_AUDIO
                          ) == PackageManager.PERMISSION_GRANTED
                        ) {
                          request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                        } else {
                          pendingPermissionRequest = request
                          requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                        return
                      }
                    }
                  } catch (e: Exception) {
                    e.printStackTrace()
                  }
                  super.onPermissionRequest(request)
                }
              }
              
              // Load application via secure virtual host domain (avoids LocalStorage/CORS file:// restrictions)
              loadUrl("https://appassets.androidplatform.net/assets/www/index.html")
            }
          }
        )

        // Native Black Splash Screen with smooth fade out animation
        AnimatedVisibility(
          visible = showSplash,
          exit = fadeOut(animationSpec = tween(durationMillis = 800))
        ) {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(Color.Black),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = "LÓGRAN SOFTWARE",
              color = Color.White,
              fontSize = 24.sp,
              fontWeight = FontWeight.Bold,
              fontFamily = FontFamily.SansSerif
            )
          }
        }
      }
    }
  }

  private fun hideSystemUI() {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.let { controller ->
          controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
          controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
      } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
          View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
          or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
          or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
          or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
          or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
          or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    // The Activity owns billingManager, so its BillingClient (and the listeners holding a
    // reference back to this Activity) must be unbound on every teardown, including the
    // recreation caused by a configuration change.
    billingManager.release()
  }

  override fun onResume() {
    super.onResume()
    hideSystemUI()
    if (shouldCloseAppOnResume) {
      closeApp()
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      hideSystemUI()
    }
  }
}
