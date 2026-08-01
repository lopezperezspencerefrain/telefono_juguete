# Keep WebKit and Javascript Interface methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep MainActivity
-keep class com.logransoftware.telefonojuguete.MainActivity { *; }

# Keep Google Mobile Ads SDK classes
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }

# WorkManager (pulled in transitively by Google's ad SDK) instantiates its Room database
# implementation by class name via reflection, which R8 can't see as a real usage; without
# this, its constructor gets stripped and the app crashes on startup.
-keep class androidx.work.** { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }

-dontusemixedcaseclassnames
-verbose
