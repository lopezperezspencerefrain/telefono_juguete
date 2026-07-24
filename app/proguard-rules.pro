# Keep WebKit and Javascript Interface methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep MainActivity
-keep class com.example.telefonojuguete.MainActivity { *; }

# Keep Google Mobile Ads SDK classes
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }

# Optimize aggressively
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
